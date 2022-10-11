name := "spark.visual.simulation"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.13.10"
libraryDependencies ++= {
  val sparkVersion = "3.3.0"
  Seq(
    "org.scalafx" %% "scalafx" % "19.0.0-R30",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "org.apache.spark" %% "spark-sql" % sparkVersion,
    "org.apache.spark" %% "spark-streaming-kafka-0-10" % "3.2.0",
    "org.apache.kafka" %% "kafka" % "2.8.1",
    "com.datastax.spark" %% "spark-cassandra-connector" % "3.1.0",
    "org.slf4j" % "slf4j-api" % "2.0.3",
    "org.scalatest" %% "scalatest" % "3.2.14" % Test
  )
}
