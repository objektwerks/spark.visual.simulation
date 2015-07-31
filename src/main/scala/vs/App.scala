package vs

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.chart.{LineChart, NumberAxis, PieChart}
import scalafx.scene.control._
import scalafx.scene.layout.VBox

object App extends JFXApp {
  implicit def ec = ExecutionContext.global

  val sourceLabel = new Label { text = "Source"}

  val sourceResultLabel = new Label

  val flowLabel = new Label { text = "Flow"}

  val flowChart = new LineChart(NumberAxis("Episodes", 0, 100, 10), NumberAxis("Ratings", 0, 100, 10))

  val sinkLabel = new Label { text = "Sink"}

  val sinkChart = new PieChart { clockwise = false }

  val simulationPane = new VBox {
    children = List(sourceLabel, sourceResultLabel, flowLabel, flowChart, sinkLabel, sinkChart)
  }

  val playSimulationButton = new Button {
    text = "Play"
    onAction = handle {
      try {
        playSimulationButton { disable = true }
        val simulation = new Simulation()
        val result = simulation.play()
        sourceResultLabel.text = s"${result.producedKafkaMessages} produced."
        // Todo
        sinkChart.data = result.selectedCassandraRatings map { t => PieChart.Data(t._1, t._2) }
      } finally { playSimulationButton { disable = false } }
    }
  }

  val toolbar = new ToolBar {
    content = List( playSimulationButton )
  }

  val appPane = new VBox {
    maxWidth = 800
    maxHeight = 600
    spacing = 6
    padding = Insets(6)
    children = List(toolbar, simulationPane)
  }

  stage = new JFXApp.PrimaryStage {
    title.value = "Visual Spark"
    scene = new Scene {
      root = appPane
    }
  }
}