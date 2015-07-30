package vs

import java.io._
import java.util.{Properties, UUID}

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
import com.datastax.spark.connector.mapper.DefaultColumnMapper
import kafka.admin.AdminUtils
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.{Decoder, Encoder, StringDecoder}
import kafka.utils.{VerifiableProperties, ZKStringSerializer}
import org.I0Itec.zkclient.ZkClient
import org.apache.spark.sql.cassandra.CassandraSQLContext
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

case class Rating(uuid: String, program: String, episode: Int, rating: Int)

object Rating {
  implicit object Mapper extends DefaultColumnMapper[Rating](
    Map("uuid" -> "uuid", "program" -> "program", "episode" -> "episode", "rating" -> "rating"))
}

class RatingEncoder extends Encoder[Rating] with Serializable {
  def this(props: VerifiableProperties) {
    this()
  }

  override def toBytes(rating: Rating): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    try {
      oos.writeObject(rating)
      baos.toByteArray
    } finally {
      baos.close()
      oos.close()
    }
  }
}

class RatingDecoder extends Decoder[Rating] with Serializable {
  def this(props: VerifiableProperties) {
    this()
  }

  override def fromBytes(bytes: Array[Byte]): Rating = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      val rating = ois.readObject()
      rating.asInstanceOf[Rating]
    } finally {
      ois.close()
    }
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
  val conf = new SparkConf().setMaster("local[2]").setAppName("sparky")
    .set("spark.cassandra.connection.host", "127.0.0.1")
    .set("spark.executor.memory", "2048m")
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
    val producer = new Producer[String, Array[Byte]](config)
    val messages = ArrayBuffer[Rating]()
    val encoder = new RatingEncoder()
    ratings foreach { l =>
      val fields = l.split(",").map(_.trim)
      val rating = Rating(uuid = UUID.randomUUID().toString, program = fields(0), episode = fields(1).toInt, rating = fields(2).toInt)
      val bytes = encoder.toBytes(rating)
      producer.send(KeyedMessage[String, Array[Byte]](topic = topic, key = rating.uuid, partKey = rating.program, message = bytes))
      messages += rating
    }
    messages
  }

  def consumeKafkaTopicMessages(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val is: InputDStream[(String, Rating)] = KafkaUtils.createDirectStream[String, Rating, StringDecoder, RatingDecoder](streamingContext, kafkaParams, topics)
    val ds: DStream[Rating] = is map(_._2)
    ds.repartitionByCassandraReplica(keyspaceName = "simulation", tableName = "ratings", partitionsPerHost = 2)
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