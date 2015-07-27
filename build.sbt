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
    "org.apache.spark" % "spark-streaming-kafka_2.11" % sparkVersion,
    "org.apache.kafka" % "kafka_2.11" % "0.8.2.1",
    "com.datastax.spark" % "spark-cassandra-connector_2.11" % "1.4.0-M1",
    "com.typesafe" % "config" % "1.3.0",
    "org.slf4j" % "slf4j-api" % "1.7.12"
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

javaOptions += "-server -Xss1m -Xmx2g"

fork in test := false
