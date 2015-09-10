name := "objektwerks.visual.spark"

version := "0.1"

scalaVersion := "2.11.7"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

libraryDependencies ++= {
  val sparkVersion = "1.5.0"
  Seq(
    "org.scalafx" % "scalafx_2.11" % "8.0.40-R8",
    "org.apache.spark" % "spark-core_2.11" % sparkVersion,
    "org.apache.spark" % "spark-streaming_2.11" % sparkVersion,
    "org.apache.spark" % "spark-sql_2.11" % sparkVersion,
    "org.apache.spark" % "spark-streaming-kafka_2.11" % sparkVersion,
    "org.apache.kafka" % "kafka_2.11" % "0.8.2.1",
    "com.datastax.spark" % "spark-cassandra-connector_2.11" % "1.4.0-M1",
    "org.slf4j" % "slf4j-api" % "1.7.12",
    "org.scalatest" % "scalatest_2.11" % "2.2.5" % "test"
  )
}

unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/ext/jfxrt.jar"))

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

run in Compile <<= Defaults.runTask(fullClasspath in (Compile, run), mainClass in (Compile, run), runner in (Compile, run))
