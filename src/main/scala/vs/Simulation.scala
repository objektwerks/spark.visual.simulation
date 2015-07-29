package vs

import java.io.{ByteArrayInputStream, ObjectInputStream}
import java.util.{Properties, UUID}

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
import kafka.admin.AdminUtils
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.{Decoder, StringDecoder}
import kafka.utils.ZKStringSerializer
import org.I0Itec.zkclient.ZkClient
import org.apache.spark.sql.cassandra.CassandraSQLContext
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

case class Rating(uuid: String = UUID.randomUUID().toString, program: String, episode: Int, rating: Int)

class RatingDecoder extends Decoder[Rating] {
  override def fromBytes(bytes: Array[Byte]): Rating = {
    val is = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val o = is.readObject()
    is.close()
    o.asInstanceOf[Rating]
  }
}

case class Result(producedKafkaTopicMessages: ArrayBuffer[Rating],
                  selectedCassandraRatings: ArrayBuffer[(String, Int)]) {
  override def toString: String = {
    s"""produced kafka topic messages: $producedKafkaTopicMessages"
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
      session.execute("CREATE TABLE simulation.ratings(uuid text PRIMARY KEY, program text, episode int, rating int);")
    }
  }

  def produceKafkaTopicMessages(): ArrayBuffer[Rating] = {
    val props = new Properties
    props.load(Source.fromInputStream(getClass.getResourceAsStream("/kafka.properties")).bufferedReader())
    val config = new ProducerConfig(props)
    val producer = new Producer[String, Rating](config)
    val messages = ArrayBuffer[Rating]()
    ratings foreach { l =>
      val fields = l.split(",").map(_.trim)
      val rating = Rating(program = fields(0), episode = fields(1).toInt, rating = fields(2).toInt)
      producer.send(KeyedMessage[String, Rating](topic = topic, key = rating.program, partKey = rating.program, message = rating))
      messages += rating
    }
    messages
  }

  def consumeKafkaTopicMessages(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    streamingContext.checkpoint("./target/output/test/checkpoint")
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val ds: InputDStream[(String, Rating)] = KafkaUtils.createDirectStream[String, Rating, StringDecoder, RatingDecoder](streamingContext, kafkaParams, topics)
    ds.saveAsTextFiles("./target/output/test/ds")
    ds.saveToCassandra("simulation", "ratings", SomeColumns("uuid", "program", "episode", "rating"))
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