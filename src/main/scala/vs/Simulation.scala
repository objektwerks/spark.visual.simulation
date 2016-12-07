package vs

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.datastax.driver.core.Cluster
import com.datastax.spark.connector.SomeColumns
import kafka.admin.AdminUtils
import kafka.utils.ZkUtils
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.{KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Milliseconds, StreamingContext}

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

case class Result(ratings: Seq[(String, String, String, String)], // Source
                  programToEpisodesRatings: Map[String, Seq[(Int, Int)]], // Flow
                  programRatings: Seq[(String, Long)]) // Sink

class Simulation {
  val cluster = Cluster.builder.addContactPoint("127.0.0.1").build()
  val session = cluster.connect()
  session.execute("DROP KEYSPACE IF EXISTS simulation;")
  session.execute("CREATE KEYSPACE simulation WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };")
  session.execute("CREATE TABLE simulation.ratings(program text, season int, episode int, rating int, PRIMARY KEY (program, season, episode));")

  val sparkSession = SparkSession.builder
    .master("local[2]")
    .appName("visual.spark")
    .config("spark.cassandra.connection.host", "127.0.0.1")
    .config("spark.cassandra.auth.username", "cassandra")
    .config("spark.cassandra.auth.password", "cassandra")
    .getOrCreate()
  val ratingsExternalTable = sparkSession.catalog.createExternalTable("ratings", "org.apache.spark.sql.cassandra", Map("keyspace" -> "simulation", "table" -> "ratings"))

  val kafkaProducerProperties = loadProperties("/kafka.producer.properties")
  val kafkaConsumerProperties = toMap(loadProperties("/kafka.consumer.properties"))
  val kafkaTopic = "ratings"

  def play(): Result = {
    createKafkaTopic()
    val ratings = produceAndSendKafkaTopicMessages()
    consumeKafkaTopicMessagesAsDirectStream()
    val programToEpisodesRatings = selectProgramToEpisodesRatingsFromCassandra()
    val programRatings = selectProgramRatingsFromCassandra()
    sparkSession.stop()
    Result(ratings, programToEpisodesRatings, programRatings)
  }

  def createKafkaTopic(): Unit = {
    val zkClient = ZkUtils.createZkClient("localhost:2181", 10000, 10000)
    val zkUtils = ZkUtils(zkClient, isZkSecurityEnabled = false)
    val topicMetadata = AdminUtils.fetchTopicMetadataFromZk(kafkaTopic, zkUtils)
    println(s"Kafka topic: ${topicMetadata.topic}")
    if (topicMetadata.topic != kafkaTopic) {
      AdminUtils.createTopic(zkUtils, kafkaTopic, 1, 1, kafkaProducerProperties)
      println(s"Kafka Topic ( $kafkaTopic ) created.")
    }
  }

  // Source
  def produceAndSendKafkaTopicMessages(): Seq[(String, String, String, String)] = {
    val producer = new KafkaProducer[String, String](kafkaProducerProperties)
    val source = Source.fromInputStream(getClass.getResourceAsStream("/ratings")).getLines.toSeq
    val ratings = ArrayBuffer[(String, String, String, String)]()
    source foreach { line =>
      val fields = line.split(",")
      val rating = (fields(0), fields(1), fields(2), fields(3))
      ratings += rating
      val record = new ProducerRecord[String, String](kafkaTopic, 0, line, line)
      producer.send(record)
    }
    producer.close(3000L, TimeUnit.MILLISECONDS)
    ratings
  }

  // Flow
  def consumeKafkaTopicMessagesAsDirectStream(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(sparkSession.sparkContext, Milliseconds(10000))
    val kafkaParams = kafkaConsumerProperties
    val kafkaTopics = Set(kafkaTopic)
    val is = KafkaUtils.createDirectStream[String, String](
      streamingContext,
      LocationStrategies.PreferConsistent,
      Subscribe[String, String](kafkaTopics, kafkaParams)
    )
    val ds = is map { record =>
      val fields = record.value.split(",")
      val tuple = (fields(0), fields(1).toInt, fields(2).toInt, fields(3).toInt)
      tuple
    }
    ds.saveToCassandra("simulation", "ratings", SomeColumns("program", "season", "episode", "rating"))
    streamingContext.start()
    streamingContext.awaitTerminationOrTimeout(3000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }

  // Flow
  def selectProgramToEpisodesRatingsFromCassandra(): Map[String, Seq[(Int, Int)]] = {
    val dataframe = ratingsExternalTable.select("program", "episode", "rating")
    val rows = dataframe.orderBy("program", "episode", "rating").collect()
    val data = new ArrayBuffer[(String, Int, Int)](rows.length)
    rows foreach { row =>
      val tuple = (row.getAs[String](0), row.getAs[Int](1), row.getAs[Int](2))
      data += tuple
    }
    data groupBy { program => program._1 } mapValues { _.map { episodeAndRating => (episodeAndRating._2, episodeAndRating._3 ) } }
  }

  // Sink
  def selectProgramRatingsFromCassandra(): Seq[(String, Long)] = {
    val dataframe = ratingsExternalTable.select("program", "rating")
    val rows = dataframe.groupBy("program").agg("rating" -> "sum").orderBy("program").collect()
    val data = new ArrayBuffer[(String, Long)](rows.length)
    rows foreach { row =>
      val tuple = (row.getAs[String](0), row.getAs[Long](1))
      data += tuple
    }
    data
  }

  private def loadProperties(file: String): Properties = {
    val properties = new Properties()
    properties.load(Source.fromInputStream(getClass.getResourceAsStream(file)).bufferedReader())
    properties
  }

  private def toMap(properties: Properties): Map[String, String] = {
    import scala.collection.JavaConverters._
    properties.asScala.toMap
  }
}