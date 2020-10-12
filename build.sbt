enablePlugins(JlinkPlugin)

name := "spark.visual.simulation"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.12.12"
libraryDependencies ++= {
  val sparkVersion = "2.4.7"
  Seq(
    "org.scalafx" %% "scalafx" % "14-R19",
    "org.apache.spark" %% "spark-core" % sparkVersion,
    "org.apache.spark" %% "spark-streaming" % sparkVersion,
    "org.apache.spark" %% "spark-sql" % sparkVersion,
    "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion,
    "org.apache.kafka" %% "kafka" % "2.6.0",
    "com.datastax.spark" %% "spark-cassandra-connector" % "3.0.0",
    "org.slf4j" % "slf4j-api" % "1.7.26",
    "org.scalatest" %% "scalatest" % "3.2.2" % Test
  )
}
lazy val osName = System.getProperty("os.name") match {
  case n if n.startsWith("Linux")   => "linux"
  case n if n.startsWith("Mac")     => "mac"
  case n if n.startsWith("Windows") => "win"
  case _ => throw new Exception("Unknown platform!")
}
lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
libraryDependencies ++= javaFXModules.map( m => "org.openjfx" % s"javafx-$m" % "14.0.1" classifier osName )
jlinkModules := {
  jlinkModules.value :+ "jdk.unsupported"
}
jlinkIgnoreMissingDependency := JlinkIgnore.everything
