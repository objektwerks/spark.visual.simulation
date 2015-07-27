package vs

import java.util.Properties

import kafka.producer.{KeyedMessage, Producer, ProducerConfig}

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

class KafkaProducer {
  private val props = new Properties
  props.load(Source.fromInputStream(getClass.getResourceAsStream("/kafka.producer.properties")).bufferedReader())
  private val config = new ProducerConfig(props)
  private val topic = AppInstance.topic
  private val context = AppInstance.context

  def send(): Int = {
    val producer = new Producer[String, String](config)
    val rdd = context.makeRDD(AppInstance.license)
    val words = toWords(rdd).collect()
    val messages = ArrayBuffer[KeyedMessage[String, String]]()
    words foreach { w =>
      messages += KeyedMessage[String, String](topic = topic, key = w, partKey = w, message = 1.toString)
    }
    producer.send(messages:_*)
    messages.size
  }
}