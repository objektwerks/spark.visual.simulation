package vs

import org.scalatest.FunSuite

class SimulationTest extends FunSuite {
  test("simulation") {
    val simulation = new Simulation()
    val result = simulation.play()
    println(result.toString)
    assert(result.selectedCassandraRatings.nonEmpty)
  }
}