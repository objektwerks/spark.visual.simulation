Spark Visual Simulation
-----------------------
>Spark app that executes a visual simulation of a spark source ~> flow ~> sink pipeline.

Installation
------------
1. brew tap homebrew/services
2. brew install scala
3. brew install sbt
4. brew install cassandra
5. brew install zookeeper

Services
--------
1. brew services start cassandra & zookeeper & kafka
2. brew services stop cassandra & kafka * zookeeper

Test
----
1. sbt clean test

Run
---
1. sbt clean compile run

Logging
-------
>Spark depends on log4j. Providing a log4j.properties file works great during testing and lauching of a spark app within an IDE.
Spark, however, ignores a jar-based log4j.properties file whether a job is run by spark-submit.sh or SparkLauncher. You have to
place a log4j.properties file in the $SPARK_HOME/conf directory. A log4j.properties.template file is provided in the same directory.

Logs
----
1. ./target/test.log
2. ./target/main.log

Kafka Topics
------------
- kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic license
- kafka-topics.sh --zookeeper localhost:2181 --list