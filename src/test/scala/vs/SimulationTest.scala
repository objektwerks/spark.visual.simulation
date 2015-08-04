package vs

import org.scalatest.FunSuite

import scala.concurrent.ExecutionContext

class SimulationTest extends FunSuite {
  implicit def ec = ExecutionContext.global

  test("simulation") {
    val simulation = new Simulation()
    val result = simulation.play()
    assert(result.kafkaMessages.nonEmpty)
    assert(result.lineChartData.nonEmpty)
    assert(result.pieChartData.nonEmpty)
    println(result.toString)
  }
}