import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream

package object vs {
  def toWords(rdd: RDD[String]): RDD[(String)] = {
    rdd.flatMap(l => l.split("\\P{L}+")).filter(_.nonEmpty).map(_.toLowerCase)
  }

  def countWords(rdd: RDD[String]): RDD[(String, Int)] = {
    rdd.flatMap(l => l.split("\\P{L}+")).filter(_.nonEmpty).map(_.toLowerCase).map(w => (w, 1)).reduceByKey(_ + _)
  }

  def countWords(ds: DStream[String]): DStream[(String, Int)] = {
    ds.flatMap(l => l.split("\\P{L}+")).filter(_.nonEmpty).map(_.toLowerCase).map(w => (w, 1)).reduceByKey(_ + _)
  }
}