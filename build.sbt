name := "spark.visual.simulation"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.12.20"
libraryDependencies ++= {
  val sparkVersion = "2.4.8"
  Seq(
    "org.scalafx" %% "scalafx" % "24.0.2-R36",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "org.apache.spark" %% "spark-sql" % sparkVersion,
    "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion,
    "org.apache.kafka" %% "kafka" % "2.8.2",
    "com.datastax.spark" %% "spark-cassandra-connector" % "3.3.0",
    "org.slf4j" % "slf4j-api" % "2.0.12",
    "org.scalatest" %% "scalatest" % "3.2.19" % Test
  )
}
