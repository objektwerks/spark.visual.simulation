package vs

import org.scalatest.FunSuite

class SimulationTest extends FunSuite {
  test("simulation") {
    val simulation = new Simulation()
    val result = simulation.play()
    assert(result.ratings.nonEmpty)
    assert(result.episodeRatings.nonEmpty)
    assert(result.programRatings.nonEmpty)
    println(result.toString)
  }
}