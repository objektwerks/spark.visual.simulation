name := "spark.visual.simulation"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.12.14"
libraryDependencies ++= {
  val sparkVersion = "2.4.7"
  Seq(
    "org.scalafx" %% "scalafx" % "15.0.1-R21",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "org.apache.spark" %% "spark-sql" % sparkVersion,
    "org.apache.spark" %% "spark-streaming-kafka-0-10" % "3.1.0",
    "org.apache.kafka" %% "kafka" % "2.7.0",
    "com.datastax.spark" %% "spark-cassandra-connector" % "3.0.0",
    "org.slf4j" % "slf4j-api" % "1.7.26",
    "org.scalatest" %% "scalatest" % "3.2.9" % Test
  )
}
