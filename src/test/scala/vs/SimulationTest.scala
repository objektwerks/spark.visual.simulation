package vs

import org.scalatest.FunSuite

class SimulationTest extends FunSuite {
  test("simulation") {
    val simulation = new Simulation()
    val result = simulation.play()
    assert(result.kafkaMessages.nonEmpty)
    assert(result.cassandraMessages.nonEmpty)
    assert(result.cassandraRatings.nonEmpty)
  }
}