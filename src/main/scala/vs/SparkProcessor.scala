package vs

import kafka.serializer.StringDecoder
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{StreamingContext, Milliseconds}

class SparkProcessor {
  val context = AppInstance.context
  val topic = AppInstance.topic

  def process(): Unit = {
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    streamingContext.checkpoint("./target/output/test/checkpoint/kss")
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val is: InputDStream[(String, String)] = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](streamingContext, kafkaParams, topics)
    is.checkpoint(Milliseconds(1000))
    is.saveAsTextFiles("./target/output/test/is")
    streamingContext.start
    streamingContext.awaitTerminationOrTimeout(1000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }
}