import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream

package object vs {
  def toWords(rdd: RDD[String]): RDD[(String)] = {
    rdd.flatMap(l => l.split("\\P{L}+")).filter(_.nonEmpty).map(_.toLowerCase)
  }

  def countWords(ds: DStream[(String, String)]): DStream[(String, Int)] = {
    ds.map(kv => (kv._1, kv._2.toInt)).reduceByKey(_ + _)
  }
}