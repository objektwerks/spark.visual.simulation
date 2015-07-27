package vs

import com.datastax.spark.connector.cql.CassandraConnector
import kafka.admin.AdminUtils
import kafka.utils.ZKStringSerializer
import org.I0Itec.zkclient.ZkClient
import org.apache.spark.sql.cassandra.CassandraSQLContext
import org.apache.spark.{SparkConf, SparkContext}

import scala.io.Source

object AppInstance {
  val conf = new SparkConf().setMaster("local[2]").setAppName("sparky").set("spark.cassandra.connection.host", "127.0.0.1")
  val context = new SparkContext(conf)
  val sqlContext = new CassandraSQLContext(context)
  val connector = CassandraConnector(conf)
  connector.withSessionDo { session =>
    session.execute("DROP KEYSPACE IF EXISTS test;")
    session.execute("CREATE KEYSPACE test WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1 };")
    session.execute("CREATE TABLE test.words(word text PRIMARY KEY, count int);")
  }
  val license = Source.fromInputStream(getClass.getResourceAsStream("/license.mit")).getLines.toSeq
  val topic = "license"

  createTopic()

  private def createTopic(): Unit = {
    val zkClient = new ZkClient("localhost:2181", 3000, 3000, ZKStringSerializer)
    val metadata = AdminUtils.fetchTopicMetadataFromZk(topic, zkClient)
    metadata.partitionsMetadata.foreach(println)
    if (metadata.topic != topic) {
      AdminUtils.createTopic(zkClient, topic, 1, 1)
      println(s"Created topic: $topic")
    }
  }
}