package simulation

import org.scalatest.{FunSuite, Matchers}

class SimulationTest extends FunSuite with Matchers {
  test("simulation") {
    val simulation = new Simulation()
    val result = simulation.play()
    result.ratings.nonEmpty shouldBe true
    result.programToEpisodesRatings.nonEmpty shouldBe true
    result.programRatings.nonEmpty shouldBe true
    println(result.toString)
  }
}