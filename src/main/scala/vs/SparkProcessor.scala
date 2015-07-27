package vs

import com.datastax.spark.connector.SomeColumns
import kafka.serializer.StringDecoder
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Milliseconds, StreamingContext}

class SparkProcessor {
  val context = AppInstance.context
  val topic = AppInstance.topic

  def process(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    streamingContext.checkpoint("./target/output/test/checkpoint/kss")
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val is: InputDStream[(String, String)] = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](streamingContext, kafkaParams, topics)
    val ds = is.cache()
    ds.checkpoint(Milliseconds(1000))
    ds.saveAsTextFiles("./target/output/test/ds")
    val wordCountDs = countWords(ds)
    wordCountDs.repartitionByCassandraReplica(keyspaceName = "test", tableName = "kv", partitionsPerHost = 2)
    wordCountDs.saveToCassandra("test", "words", SomeColumns("word", "count"))
    streamingContext.start
    streamingContext.awaitTerminationOrTimeout(3000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }
}