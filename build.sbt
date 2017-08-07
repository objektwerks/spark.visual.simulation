name := "visual.spark"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.11.11"
libraryDependencies ++= {
  val sparkVersion = "2.2.0"
  Seq(
    "org.scalafx" % "scalafx_2.11" % "8.0.102-R11",
    "org.apache.spark" % "spark-core_2.11" % sparkVersion,
    "org.apache.spark" % "spark-streaming_2.11" % sparkVersion,
    "org.apache.spark" % "spark-sql_2.11" % sparkVersion,
    "org.apache.spark" % "spark-streaming-kafka-0-10_2.11" % sparkVersion,
    "org.apache.kafka" % "kafka_2.11" % "0.11.0.0",
    "com.datastax.spark" % "spark-cassandra-connector_2.11" % "2.0.2",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "org.scalatest" % "scalatest_2.11" % "3.0.3" % "test"
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
