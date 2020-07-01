package simulation

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.spark.connector.SomeColumns

import java.util.Properties
import java.net.InetSocketAddress

import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig, NewTopic}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.{KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Milliseconds, StreamingContext}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

case class Result(ratings: Seq[(String, String, String, String)], // Source
                  programToEpisodesRatings: Map[String, Seq[(Int, Int)]], // Flow
                  programRatings: Seq[(String, Long)]) // Sink

class Simulation {
  // Cassandra
  val address = new InetSocketAddress("127.0.0.1", 9042)
  val datacenter = "datacenter1"
  val session = CqlSession
    .builder()
    .addContactPoint(address)
    .withLocalDatacenter(datacenter)
    .build()
  session.execute("DROP KEYSPACE IF EXISTS simulation;")
  session.execute("CREATE KEYSPACE simulation WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };")
  session.execute("CREATE TABLE simulation.ratings(program text, season int, episode int, rating int, PRIMARY KEY (program, season, episode));")

  // Spark
  val sparkSession = SparkSession.builder
    .master("local[2]")
    .appName("visual.spark")
    .config("spark.cassandra.connection.host", "127.0.0.1")
    .config("spark.cassandra.auth.username", "cassandra")
    .config("spark.cassandra.auth.password", "cassandra")
    .getOrCreate()
  val ratingsTable = sparkSession.catalog.createTable("ratings", "org.apache.spark.sql.cassandra", Map("keyspace" -> "simulation", "table" -> "ratings"))

  // Kafka
  val kafkaProducerProperties = loadProperties("/kafka.producer.properties")
  val kafkaConsumerProperties = toMap(loadProperties("/kafka.consumer.properties"))
  val kafkaTopic = "ratings"

  // Run
  def play(): Result = {
    assert( createKafkaTopic(kafkaTopic) )
    val ratings = produceAndSendKafkaTopicMessages()
    consumeKafkaTopicMessagesAsDirectStream()
    val programToEpisodesRatings = selectProgramToEpisodesRatingsFromCassandra()
    val programRatings = selectProgramRatingsFromCassandra()
    sparkSession.stop()
    Result(ratings, programToEpisodesRatings, programRatings)
  }

  // Topic
  def createKafkaTopic(topic: String): Boolean = {
    val adminClientProperties = new Properties()
    adminClientProperties.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    val adminClient = AdminClient.create(adminClientProperties)
    val newTopic = new NewTopic(topic, 1, 1.toShort)
    val createTopicResult = adminClient.createTopics(List(newTopic).asJavaCollection)
    createTopicResult.values().containsKey(topic)
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
    producer.close()
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
    val dataframe = ratingsTable.select("program", "episode", "rating")
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
    val dataframe = ratingsTable.select("program", "rating")
    val rows = dataframe.groupBy("program").agg("rating" -> "sum").orderBy("program").collect()
    val data = new ArrayBuffer[(String, Long)](rows.length)
    rows foreach { row =>
      val tuple = (row.getAs[String](0), row.getAs[Long](1))
      data += tuple
    }
    data
  }

  def loadProperties(file: String): Properties = {
    val properties = new Properties()
    properties.load(Source.fromInputStream(getClass.getResourceAsStream(file)).bufferedReader())
    properties
  }

  def toMap(properties: Properties): Map[String, String] = {
    import scala.collection.JavaConverters._
    properties.asScala.toMap
  }
}