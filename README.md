Visual Spark
------------
>The purpose of the project is to build a Spark simulator that executes a visual simulation of a data source ~> spark
flow ~> data sink pipeline.

***

Homebrew
--------
>Install Homebrew on OSX. [How-To] (http://coolestguidesontheplanet.com/installing-homebrew-os-x-yosemite-10-10-package-manager-unix-apps/)

Installation
------------
>Install the following packages via Homebrew:

1. brew tap homebrew/services [Homebrew Services] (https://robots.thoughtbot.com/starting-and-stopping-background-services-with-homebrew)
2. brew install scala
3. brew install sbt
4. brew install cassandra
5. brew install zookeeper

Services
--------
>Start:

1. brew services start cassandra
2. brew services start zookeeper
3. brew services start kafka

>Homebrew uses launchctl/launchd to ensure services are booted up on each system start, unless you stop them. Take a
 look at the apache.kafka.plist in this project. Homebrew service plist files are ideally stored in ~/Library/LaunchAgents
 See [launchd] (http://launchd.info) for details. Ideally you should use Xcode to edited a plist.

>Stop:

1. brew services stop cassandra
2. brew services stop kafka
3. brew services stop zookeeper

Run
---
1. sbt clean compile run

Logging
-------
>Spark depends on log4j. Providing a log4j.properties file works great during testing and lauching of a spark app within an IDE.
Spark, however, ignores a jar-based log4j.properties file whether a job is run by spark-submit.sh or SparkLauncher. You have to
place a log4j.properties file in the $SPARK_HOME/conf directory. A log4j.properties.template file is provided in the same directory.

Output
------
>Output is directed to these directories:

1. ./target/output/test
2. ./target/output/main

Kafka Topics
------------
- kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic license
- kafka-topics.sh --zookeeper localhost:2181 --list