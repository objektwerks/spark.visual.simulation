package vs

import com.datastax.spark.connector.SomeColumns
import kafka.serializer.StringDecoder
import org.apache.spark.sql.Row
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Milliseconds, StreamingContext}

class SparkProcessor {
  private val context = AppInstance.context
  private val sqlContext = AppInstance.sqlContext
  private val topic = AppInstance.topic

  def process(): Unit = {
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    streamingContext.checkpoint("./target/output/test/checkpoint/kss")
    val kafkaParams = Map("metadata.broker.list" -> "localhost:9092", "auto.offset.reset" -> "smallest")
    val topics = Set(topic)
    val ds = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](streamingContext, kafkaParams, topics).cache
    ds.checkpoint(Milliseconds(1000))
    ds.saveAsTextFiles("./target/output/test/ds")
    val wordCountDs = countWords(ds)
    saveToCassandra(wordCountDs)
    streamingContext.start
    streamingContext.awaitTerminationOrTimeout(3000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }

  private def saveToCassandra(ds: DStream[(String, Int)]): Unit = {
    import com.datastax.spark.connector.streaming._
    ds.repartitionByCassandraReplica(keyspaceName = "test", tableName = "words", partitionsPerHost = 2)
    ds.saveToCassandra("test", "words", SomeColumns("word", "count"))
  }

  private def selectFromCassandra(streamingContext: StreamingContext): Array[Row] = {
    val df = sqlContext.sql("select * from test.words")
    df.collect()
    // Todo
  }
}