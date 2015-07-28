package vs

import java.util.Properties

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
import kafka.admin.AdminUtils
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.StringDecoder
import kafka.utils.ZKStringSerializer
import org.I0Itec.zkclient.ZkClient
import org.apache.spark.sql.Row
import org.apache.spark.sql.cassandra.CassandraSQLContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.{SparkContext, SparkConf}

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

class Simulation {
  val conf = new SparkConf().setMaster("local[2]").setAppName("sparky").set("spark.cassandra.connection.host", "127.0.0.1")
  val context = new SparkContext(conf)
  val sqlContext = new CassandraSQLContext(context)
  val license = Source.fromInputStream(getClass.getResourceAsStream("/license.mit")).getLines.toSeq
  val topic = "license"

  def play(): Unit = {
    // Todo
    context.stop()
  }

  def createCassandraStore(): Unit = {
    val connector = CassandraConnector(conf)
    connector.withSessionDo { session =>
      session.execute("DROP KEYSPACE IF EXISTS test;")
      session.execute("CREATE KEYSPACE test WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };")
      session.execute("CREATE TABLE test.words(word text PRIMARY KEY, count int);")
    }
  }

  def createKafkaTopic(): Unit = {
    val zkClient = new ZkClient("localhost:2181", 3000, 3000, ZKStringSerializer)
    val metadata = AdminUtils.fetchTopicMetadataFromZk(topic, zkClient)
    metadata.partitionsMetadata.foreach(println)
    if (metadata.topic != topic) {
      AdminUtils.createTopic(zkClient, topic, 1, 1)
      println(s"Created topic: $topic")
    }
  }

  def produceKafkaTopicMessages(): Int = {
    val props = new Properties
    props.load(Source.fromInputStream(getClass.getResourceAsStream("/kafka.properties")).bufferedReader())
    val config = new ProducerConfig(props)
    val producer = new Producer[String, String](config)
    val rdd = context.makeRDD(license)
    val words = rdd.flatMap(l => l.split("\\P{L}+")).filter(_.nonEmpty).map(_.toLowerCase).collect()
    val messages = ArrayBuffer[KeyedMessage[String, String]]()
    words foreach { w =>
      messages += KeyedMessage[String, String](topic = topic, key = w, partKey = w, message = 1.toString)
    }
    producer.send(messages:_*)
    messages.size
  }

  def consumeKafkaTopicMessages(): Unit = {
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    streamingContext.checkpoint("./target/output/test/checkpoint/kss")
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val ds = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](streamingContext, kafkaParams, topics).cache()
    ds.checkpoint(Milliseconds(1000))
    ds.saveAsTextFiles("./target/output/test/ds")
    val wordCountDs = ds.map(kv => (kv._1, kv._2.toInt)).reduceByKey(_ + _)
    saveToCassandra(wordCountDs)
    streamingContext.start()
    streamingContext.awaitTerminationOrTimeout(3000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }

  def saveToCassandra(ds: DStream[(String, Int)]): Unit = {
    import com.datastax.spark.connector.streaming._
    ds.repartitionByCassandraReplica(keyspaceName = "test", tableName = "words", partitionsPerHost = 2)
    ds.saveToCassandra("test", "words", SomeColumns("word", "count"))
  }

  def selectFromCassandra(streamingContext: StreamingContext): Array[Row] = {
    val df = sqlContext.sql("select * from test.words")
    df.collect()
    // Todo
  }
}