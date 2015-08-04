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
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

case class Rating(program: String, season: Int, episode: Int, rating: Int)

case class Result(kafkaMessages: Seq[Rating], lineChartData: Map[String, Seq[(Int, Int)]], pieChartData: Seq[(String, Long)])

class Simulation {
  val conf = new SparkConf().setMaster("local[4]").setAppName("sparky")
    .set("spark.cassandra.connection.host", "127.0.0.1")
    .set("spark.executor.memory", "1g")
  val context = new SparkContext(conf)
  val connector = CassandraConnector(conf)
  val topic = "ratings"

  def play(): Result = {
    createKafkaTopic()
    createCassandraStore()
    val messages = produceKafkaTopicMessages()
    consumeKafkaTopicMessages()
    val lineChartData = selectLineChartDataFromCassandra()
    val pieChartData = selectPieChartDataFromCassandra()
    context.stop()
    Result(messages, lineChartData, pieChartData)
  }

  def createKafkaTopic(): Unit = {
    val zkClient = new ZkClient("localhost:2181", 3000, 3000, ZKStringSerializer)
    val metadata = AdminUtils.fetchTopicMetadataFromZk(topic, zkClient)
    metadata.partitionsMetadata.foreach(println)
    if (metadata.topic != topic) {
      AdminUtils.createTopic(zkClient, topic, 1, 1)
    }
  }

  def createCassandraStore(): Unit = {
    connector.withSessionDo { session =>
      session.execute("DROP KEYSPACE IF EXISTS simulation;")
      session.execute("CREATE KEYSPACE simulation WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };")
      session.execute("CREATE TABLE simulation.ratings(program text, season int, episode int, rating int, PRIMARY KEY (program, season, episode));")
    }
  }

  def produceKafkaTopicMessages(): Seq[Rating] = {
    val props = new Properties
    props.load(Source.fromInputStream(getClass.getResourceAsStream("/kafka.properties")).bufferedReader())
    val config = new ProducerConfig(props)
    val producer = new Producer[String, String](config)
    val _messages = ArrayBuffer[Rating]()
    val messages = ArrayBuffer[KeyedMessage[String, String]]()
    val ratings = Source.fromInputStream(getClass.getResourceAsStream("/ratings")).getLines.toSeq
    ratings foreach { l =>
      val fields = l.split(",")
      _messages += Rating(fields(0), fields(1).toInt, fields(2).toInt, fields(3).toInt)
      messages += KeyedMessage[String, String](topic = topic, key = l, partKey = 0, message = l)
    }
    producer.send(messages: _*)
    _messages
  }

  def consumeKafkaTopicMessages(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(context, Milliseconds(3000))
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val is: InputDStream[(String, String)] = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](streamingContext, kafkaParams, topics)
    val ds: DStream[(String, Int, Int, Int)] = is map { rdd =>
      val fields = rdd._2.split(",")
      val tuple = (fields(0), fields(1).toInt, fields(2).toInt, fields(3).toInt)
      tuple
    }
    ds.repartitionByCassandraReplica(keyspaceName = "simulation", tableName = "ratings", partitionsPerHost = 2)
    ds.saveToCassandra("simulation", "ratings", SomeColumns("program", "season", "episode", "rating"))
    streamingContext.start()
    streamingContext.awaitTerminationOrTimeout(3000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }

  def selectLineChartDataFromCassandra(): Map[String, Seq[(Int, Int)]] = {
    val sqlContext = new CassandraSQLContext(context)
    val df = sqlContext.sql("select program, episode, rating from simulation.ratings")
    val rows = df.orderBy("program", "episode", "rating").collect()
    var data = new ArrayBuffer[(String, Int, Int)](rows.length)
    rows foreach { r =>
      val tuple = (r.getAs[String](0), r.getAs[Int](1), r.getAs[Int](2))
      data += tuple
    }
    data groupBy { t => t._1 } mapValues { _.map { t => (t._2, t._3 ) } }
  }

  def selectPieChartDataFromCassandra(): Seq[(String, Long)] = {
    val sqlContext = new CassandraSQLContext(context)
    val df = sqlContext.sql("select program, rating from simulation.ratings")
    val rows = df.groupBy("program").agg("rating" -> "sum").orderBy("program").collect()
    val data = new ArrayBuffer[(String, Long)](rows.length)
    rows foreach { r =>
      val tuple = (r.getAs[String](0), r.getAs[Long](1))
      data += tuple
    }
    data
  }
}