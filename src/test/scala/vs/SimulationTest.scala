package vs

import org.scalatest.FunSuite

class SimulationTest extends FunSuite {
  test("simulation") {
    val simulation = new Simulation()
    val result = simulation.play()
    assert(result.producedKafkaMessages > 0)
    assert(result.selectedCassandraRatings.nonEmpty)
    println(result.toString)
  }
}