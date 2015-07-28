package vs

import scalafx.Includes._
import scala.concurrent.ExecutionContext
import scalafx.application.JFXApp
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.chart.{PieChart, NumberAxis, LineChart}
import scalafx.scene.control.{ListView, Label, Button, ToolBar}
import scalafx.scene.layout.VBox

object App extends JFXApp {
  implicit def ec = ExecutionContext.global

  val playSimulationButton = new Button {
    text = "Play"
    onAction = handle {
/*
      val simulation = new Simulation()
      simulation.play()
*/
    }
  }

  val toolbar = new ToolBar {
    content = List( playSimulationButton )
  }

  val sourceLabel = new Label { text = "Source"}

  val sourceChart = new ListView[String]() {
  }

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
    children = List(sourceLabel, sourceChart, flowLabel, flowChart, sinkLabel, sinkChart)
  }

  val appPane = new VBox {
    maxWidth = 800
    maxHeight = 800
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