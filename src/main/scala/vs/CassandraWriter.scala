package vs

import com.datastax.spark.connector.SomeColumns
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.{Milliseconds, StreamingContext}

import scala.collection.mutable

class CassandraWriter {
  def context = AppInstance.context

  def write(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    val queue = mutable.Queue[RDD[String]]()
    val ds = streamingContext.queueStream(queue)
    queue += context.makeRDD(AppInstance.license)
    val wordCountDs = countWords(ds)
    wordCountDs.repartitionByCassandraReplica(keyspaceName = "test", tableName = "kv", partitionsPerHost = 2)
    wordCountDs.saveToCassandra("test", "words", SomeColumns("word", "count"))
    streamingContext.start
    streamingContext.awaitTerminationOrTimeout(1000)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }
}