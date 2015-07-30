name := "objektwerks.visual.spark"

version := "0.1"

scalaVersion := "2.11.7"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= {
  val sparkVersion = "1.4.1"
  Seq(
    "org.scalafx" % "scalafx_2.11" % "8.0.40-R8",
    "org.apache.spark" % "spark-core_2.11" % sparkVersion,
    "org.apache.spark" % "spark-streaming_2.11" % sparkVersion,
    "org.apache.spark" % "spark-sql_2.11" % sparkVersion,
    "org.apache.spark" % "spark-streaming-kafka_2.11" % sparkVersion,
    "org.apache.kafka" % "kafka_2.11" % "0.8.2.1",
    "com.datastax.spark" % "spark-cassandra-connector_2.11" % "1.4.0-M1",
    "org.scala-lang.modules" % "scala-pickling_2.11" % "0.10.1",
    "org.slf4j" % "slf4j-api" % "1.7.12",
    "org.scalatest" % "scalatest_2.11" % "2.2.5" % "test"
  )
}

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:implicitConversions",
  "-language:reflectiveCalls",
  "-language:higherKinds",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Xfatal-warnings"
)

javaOptions += "-server -Xss1m -Xmx4g"

fork in test := true
