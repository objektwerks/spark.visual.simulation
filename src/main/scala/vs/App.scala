package vs

import org.apache.spark.metrics.source

import scalafx.Includes._
import scala.concurrent.ExecutionContext
import scalafx.application.JFXApp
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.chart.{PieChart, NumberAxis, LineChart}
import scalafx.scene.control._
import scalafx.scene.layout.VBox

object App extends JFXApp {
  implicit def ec = ExecutionContext.global

  val sourceLabel = new Label { text = "Source"}

  val sourceResultLabel = new Label

  val flowLabel = new Label { text = "Flow"}

  val flowChart = new LineChart(NumberAxis("Episodes", 0, 100, 10), NumberAxis("Ratings", 0, 100, 10)) {
  }

  val sinkLabel = new Label { text = "Sink"}

  val sinkChart = new PieChart() {
  }

  val simulationPane = new VBox {
    maxWidth = 800
    maxHeight = 800
    spacing = 6
    padding = Insets(6)
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