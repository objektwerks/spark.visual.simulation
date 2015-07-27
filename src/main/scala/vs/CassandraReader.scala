package vs

import org.apache.spark.streaming.{Milliseconds, StreamingContext}

class CassandraReader {
  def context = AppInstance.context

  def read(): Unit = {
    import com.datastax.spark.connector.streaming._
    val streamingContext = new StreamingContext(context, Milliseconds(1000))
    val rdd = streamingContext.cassandraTable("test", "words").select("word", "count").cache
    println(rdd.count == 95)
    println(rdd.map(_.getInt("count")).sum)
    streamingContext.stop(stopSparkContext = false, stopGracefully = true)
  }
}