package vs

import java.util.Properties

import kafka.admin.AdminUtils
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.utils.ZKStringSerializer
import org.I0Itec.zkclient.ZkClient

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

class KafkaProducer {
  private val props = new Properties
  props.load(Source.fromInputStream(getClass.getResourceAsStream("/kafka.producer.properties")).bufferedReader())
  private val config = new ProducerConfig(props)
  private val topic = AppInstance.topic
  private val context = AppInstance.context

  def send(): Unit = {
    createTopic
    val producer = new Producer[String, String](config)
    val rdd = context.makeRDD(AppInstance.license)
    val wordCounts = countWords(rdd).collect()
    val messages = ArrayBuffer[KeyedMessage[String, String]]()
    wordCounts.foreach { wc =>
      messages += KeyedMessage[String, String](topic = topic, key = wc._1, partKey = wc._1, message = wc._2.toString)
    }
    producer.send(messages:_*)
    println(s"Sent ${messages.size} messages to Kafka topic: $topic.")
  }

  private def createTopic(): Unit = {
    val zkClient = new ZkClient("localhost:2181", 3000, 3000, ZKStringSerializer)
    val metadata = AdminUtils.fetchTopicMetadataFromZk(topic, zkClient)
    metadata.partitionsMetadata.foreach(println)
    if (metadata.topic != topic) {
      AdminUtils.createTopic(zkClient, topic, 1, 1)
      println(s"Created topic: $topic")
    }
  }
}