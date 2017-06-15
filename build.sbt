name := "visual.spark"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.11.11"
ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
libraryDependencies ++= {
  val sparkVersion = "2.1.0"
  Seq(
    "org.scalafx" % "scalafx_2.11" % "8.0.102-R11",
    "org.apache.spark" % "spark-core_2.11" % sparkVersion,
    "org.apache.spark" % "spark-streaming_2.11" % sparkVersion,
    "org.apache.spark" % "spark-sql_2.11" % sparkVersion,
    "org.apache.spark" % "spark-streaming-kafka-0-10_2.11" % sparkVersion,
    "org.apache.kafka" % "kafka_2.11" % "0.10.1.1",
    "com.datastax.spark" % "spark-cassandra-connector_2.11" % "2.0.0-M3",
    "org.slf4j" % "slf4j-api" % "1.7.21",
    "org.scalatest" % "scalatest_2.11" % "3.0.1" % "test"
  )
}
unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/ext/jfxrt.jar"))
scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:reflectiveCalls",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-feature",
  "-Ywarn-unused-import",
  "-Ywarn-unused",
  "-Ywarn-dead-code",
  "-unchecked",
  "-deprecation",
  "-Xfatal-warnings",
  "-Xlint:missing-interpolator",
  "-Xlint"
)
javaOptions += "-server -Xss1m -Xmx4g"
fork in test := true
run in Compile <<= Defaults.runTask(fullClasspath in (Compile, run), mainClass in (Compile, run), runner in (Compile, run))
