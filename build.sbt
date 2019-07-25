name := "spark.visual.simulation"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.11.12"
libraryDependencies ++= {
  val sparkVersion = "2.4.3"
  Seq(
    "org.scalafx" % "scalafx_2.11" % "8.0.102-R11",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "org.apache.spark" %% "spark-sql" % sparkVersion,
    "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion,
    "org.apache.kafka" %% "kafka" % "2.3.0",
    "com.datastax.spark" %% "spark-cassandra-connector" % "2.4.1",
    "org.slf4j" % "slf4j-api" % "1.7.26",
    "org.scalatest" %% "scalatest" % "3.0.8" % Test
  )
}
unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/ext/jfxrt.jar"))