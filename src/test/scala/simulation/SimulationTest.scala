package simulation

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SimulationTest extends AnyFunSuite with Matchers {
  test("simulation") {
    val simulation = new Simulation()
    val result = simulation.play()
    result.ratings.nonEmpty shouldBe true
    result.programToEpisodesRatings.nonEmpty shouldBe true
    result.programRatings.nonEmpty shouldBe true
    println(result.toString)
  }
}