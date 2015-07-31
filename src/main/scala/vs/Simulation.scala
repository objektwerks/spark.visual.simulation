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

case class Result(producedKafkaTopicMessageCount: Int, selectedCassandraRatings: ArrayBuffer[(String, Int)]) {
  override def toString: String = {
    s"""Produced kafka topic message count: $producedKafkaTopicMessageCount \nselected cassandra ratings: ${selectedCassandraRatings.foreach(println)}"""
  }
}

class Simulation {
  val conf = new SparkConf().setMaster("local[2]").setAppName("sparky")
    .set("spark.cassandra.connection.host", "127.0.0.1")
    .set("spark.executor.memory", "1024m")
  val context = new SparkContext(conf)
  val connector = CassandraConnector(conf)
  val ratings = Source.fromInputStream(getClass.getResourceAsStream("/ratings")).getLines.toSeq
  val topic = "ratings"

  def play(): Result = {
    try {
      createKafkaTopic()
      createCassandraStore()
      val producedKafkaTopicMessages = produceKafkaTopicMessages()
      consumeKafkaTopicMessages()
      val selectedCassandraRatings = selectFromCassandra()
      Result(producedKafkaTopicMessages, selectedCassandraRatings)
    } finally {
      context.stop
    }
  }

  def createKafkaTopic(): Unit = {
    val zkClient = new ZkClient("localhost:2181", 3000, 3000, ZKStringSerializer)
    val metadata = AdminUtils.fetchTopicMetadataFromZk(topic, zkClient)
    metadata.partitionsMetadata.foreach(println)
    if (metadata.topic != topic) {
      AdminUtils.createTopic(zkClient, topic, 1, 1)
      println(s"$topic created!")
    }
  }

  def createCassandraStore(): Unit = {
    connector.withSessionDo { session =>
      session.execute("DROP KEYSPACE IF EXISTS simulation;")
      session.execute("CREATE KEYSPACE simulation WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };")
      session.execute("CREATE TABLE simulation.ratings(program text, season int, episode int, rating int PRIMARY KEY (program, season, episode);")
    }
  }

  def produceKafkaTopicMessages(): Int = {
    val props = new Properties
    props.load(Source.fromInputStream(getClass.getResourceAsStream("/kafka.properties")).bufferedReader())
    val config = new ProducerConfig(props)
    val producer = new Producer[String, String](config)
    var count = 1
    ratings foreach { l =>
      producer.send(KeyedMessage[String, String](topic = topic, key = l, partKey = l, message = l))
      count += 1
    }
    println(s"$count published to kafka topic: $topic")
    count
  }

  def consumeKafkaTopicMessages(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val is: InputDStream[(String, String)] = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](streamingContext, kafkaParams, topics)
    val ds: DStream[(String, Int, Int, Int)] = is map { rdd =>
      val fields = rdd._2.split(",").map(_.trim)
      val tuple = (fields(0), fields(1).toInt, fields(2).toInt, fields(3).toInt)
      tuple
    }
    ds.repartitionByCassandraReplica(keyspaceName = "simulation", tableName = "ratings", partitionsPerHost = 2)
    ds.saveToCassandra("simulation", "ratings", SomeColumns("program", "season", "episode", "rating"))
    streamingContext.start()
    streamingContext.awaitTerminationOrTimeout(3000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }

  def selectFromCassandra(): ArrayBuffer[(String, Int)] = {
    val sqlContext = new CassandraSQLContext(context)
    val df = sqlContext.sql("select program, rating from simulation.ratings")
    val rows = df.groupBy("program").agg("rating" -> "sum").orderBy("program").collect()
    val data = ArrayBuffer[(String, Int)]()
    rows foreach { r =>
      val tuple = (r.getString(0), r.getInt(1))
      data += tuple
    }
    data
  }
}