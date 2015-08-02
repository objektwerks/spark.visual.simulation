package vs

import org.scalatest.FunSuite

import scala.concurrent.ExecutionContext

class SimulationTest extends FunSuite {
  implicit def ec = ExecutionContext.global

  test("simulation") {
    val simulation = new Simulation()
    val result = simulation.play()
    result map { r =>
      assert(r.producedKafkaMessages.nonEmpty)
      assert(r.selectedLineChartDataFromCassandra.nonEmpty)
      assert(r.selectedPieChartDataFromCassandra.nonEmpty)
      println(result.toString)
    }
  }
}