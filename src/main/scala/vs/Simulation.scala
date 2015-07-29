package vs

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.{Properties, UUID}

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

case class Result(producedKafkaTopicMessages: ArrayBuffer[(String, String, String, String)],
                  consumedTransformedKafkaTopicMessages: ArrayBuffer[(String, String, Int, Int)],
                  selectedCassandraRatings: ArrayBuffer[(String, Int)]) {
  override def toString: String = {
    s"""produced kafka topic messages: $producedKafkaTopicMessages"
        \nconsumed transformed kafka topic messages: $consumedTransformedKafkaTopicMessages"
        \nselected cassandra ratings: $selectedCassandraRatings"""
  }
}

class Simulation {
  val conf = new SparkConf().setMaster("local[2]").setAppName("sparky").set("spark.cassandra.connection.host", "127.0.0.1")
  val context = new SparkContext(conf)
  val connector = CassandraConnector(conf)
  val ratings = Source.fromInputStream(getClass.getResourceAsStream("/ratings")).getLines.toSeq
  val topic = "ratings"

  def play(): Result = {
    try {
      createKafkaTopic()
      createCassandraStore()
      val producedKafkaTopicMessages = produceKafkaTopicMessages()
      val consumedTransformedKafkaTopicMessages = consumeKafkaTopicMessages()
      saveToCassandra(consumedTransformedKafkaTopicMessages)
      val selectedCassandraRatings = selectFromCassandra()
      Result(producedKafkaTopicMessages, consumedTransformedKafkaTopicMessages, selectedCassandraRatings)
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
    }
  }

  def createCassandraStore(): Unit = {
    connector.withSessionDo { session =>
      session.execute("DROP KEYSPACE IF EXISTS simulation;")
      session.execute("CREATE KEYSPACE simulation WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };")
      session.execute("CREATE TABLE simulation.ratings(uuid text PRIMARY KEY, program text, episode int, rating int);")
    }
  }

  def produceKafkaTopicMessages(): ArrayBuffer[(String, String, String, String)] = {
    val props = new Properties
    props.load(Source.fromInputStream(getClass.getResourceAsStream("/kafka.properties")).bufferedReader())
    val config = new ProducerConfig(props)
    val producer = new Producer[String, String](config)
    val messages = ArrayBuffer[(String, String, String, String)]()
    ratings foreach { l =>
      val fields: Array[String] = l.split(",").map(_.trim)
      producer.send(KeyedMessage[String, String](topic = topic, key = fields(0), partKey = fields(0), message = s"$fields(1),$fields(2)"))
      val tuple = (LocalTime.now().format(DateTimeFormatter.ofPattern("mm:ss")), fields(0), fields(1), fields(2))
      messages += tuple
    }
    messages
  }

  def consumeKafkaTopicMessages(): ArrayBuffer[(String, String, Int, Int)] = {
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    streamingContext.checkpoint("./target/output/test/checkpoint")
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val ds = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](streamingContext, kafkaParams, topics)
    ds.saveAsTextFiles("./target/output/test/ds")
    val messages = ArrayBuffer[(String, String, Int, Int)]()
    ds foreachRDD { rdd =>
      rdd foreach { t =>
        val uuid = UUID.randomUUID.toString
        val program = t._1
        val episodeRating = t._2.split(",").map(_.toInt)
        val (episode, rating) = (episodeRating(0), episodeRating(1))
        val tuple = (uuid, program, episode, rating)
        messages += tuple
      }
    }
    streamingContext.start()
    streamingContext.awaitTerminationOrTimeout(3000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
    messages
  }

  def saveToCassandra(messages: ArrayBuffer[(String, String, Int, Int)]): Unit = {
    messages foreach { m =>
      val (uuid, program, episode, rating) = m
      connector.withSessionDo { session =>
        session.execute(s"INSERT INTO simulation.ratings(uuid, program, episode, rating) VALUES ($uuid, $program, $episode, $rating);")
      }
    }
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