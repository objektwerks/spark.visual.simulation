package vs

import java.util.Properties

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
import kafka.admin.AdminUtils
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.StringDecoder
import kafka.utils.ZKStringSerializer
import org.I0Itec.zkclient.ZkClient
import org.apache.spark.sql.cassandra.CassandraSQLContext
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

case class Result(ratings: Seq[(String, String, String, String)],
                  programToEpisodesRatings: Map[String, Seq[(Int, Int)]],
                  programRatings: Seq[(String, Long)])

class Simulation {
  val conf = new SparkConf().setMaster("local[4]").setAppName("sparky").set("spark.cassandra.connection.host", "127.0.0.1")
  val context = new SparkContext(conf)
  val topic = "ratings"

  def play(): Result = {
    createKafkaTopic()
    createCassandraKeyspaceAndTable()
    val ratings = produceAndSendKafkaTopicMessages()
    consumeKafkaTopicMessagesAsDirectStream()
    val episodeRatings = selectProgramToEpisodesRatingsFromCassandra()
    val programRatings = selectProgramRatingsFromCassandra()
    context.stop()
    Result(ratings, episodeRatings, programRatings)
  }

  def createKafkaTopic(): Unit = {
    val zkClient = new ZkClient("localhost:2181", 3000, 3000, ZKStringSerializer)
    val metadata = AdminUtils.fetchTopicMetadataFromZk(topic, zkClient)
    metadata.partitionsMetadata.foreach(println)
    if (metadata.topic != topic) {
      AdminUtils.createTopic(zkClient, topic, 1, 1)
    }
  }

  def createCassandraKeyspaceAndTable(): Unit = {
    val connector = CassandraConnector(conf)
    connector.withSessionDo { session =>
      session.execute("DROP KEYSPACE IF EXISTS simulation;")
      session.execute("CREATE KEYSPACE simulation WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };")
      session.execute("CREATE TABLE simulation.ratings(program text, season int, episode int, rating int, PRIMARY KEY (program, season, episode));")
    }
  }

  def produceAndSendKafkaTopicMessages(): Seq[(String, String, String, String)] = {
    val props = new Properties
    props.load(Source.fromInputStream(getClass.getResourceAsStream("/kafka.properties")).bufferedReader())
    val config = new ProducerConfig(props)
    val producer = new Producer[String, String](config)
    val source = Source.fromInputStream(getClass.getResourceAsStream("/ratings")).getLines.toSeq
    val ratings = ArrayBuffer[(String, String, String, String)]()
    val keyedMessages = ArrayBuffer[KeyedMessage[String, String]]()
    source foreach { line =>
      val fields = line.split(",")
      val rating = (fields(0), fields(1), fields(2), fields(3))
      ratings += rating
      keyedMessages += KeyedMessage[String, String](topic = topic, key = line, partKey = 0, message = line)
    }
    producer.send(keyedMessages: _*)
    ratings
  }

  def consumeKafkaTopicMessagesAsDirectStream(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(context, Milliseconds(3000))
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val is = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](streamingContext, kafkaParams, topics)
    val ds = is map { rdd =>
      val fields = rdd._2.split(",")
      val tuple = (fields(0), fields(1).toInt, fields(2).toInt, fields(3).toInt)
      tuple
    }
    ds.saveToCassandra("simulation", "ratings", SomeColumns("program", "season", "episode", "rating"))
    streamingContext.start()
    streamingContext.awaitTerminationOrTimeout(3000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }

  def selectProgramToEpisodesRatingsFromCassandra(): Map[String, Seq[(Int, Int)]] = {
    val sqlContext = new CassandraSQLContext(context)
    val df = sqlContext.sql("select program, episode, rating from simulation.ratings")
    val rows = df.orderBy("program", "episode", "rating").collect()
    var data = new ArrayBuffer[(String, Int, Int)](rows.length)
    rows foreach { row =>
      val tuple = (row.getAs[String](0), row.getAs[Int](1), row.getAs[Int](2))
      data += tuple
    }
    data groupBy { program => program._1 } mapValues { _.map { episodeAndRating => (episodeAndRating._2, episodeAndRating._3 ) } }
  }

  def selectProgramRatingsFromCassandra(): Seq[(String, Long)] = {
    val sqlContext = new CassandraSQLContext(context)
    val df = sqlContext.sql("select program, rating from simulation.ratings")
    val rows = df.groupBy("program").agg("rating" -> "sum").orderBy("program").collect()
    val data = new ArrayBuffer[(String, Long)](rows.length)
    rows foreach { row =>
      val tuple = (row.getAs[String](0), row.getAs[Long](1))
      data += tuple
    }
    data
  }
}