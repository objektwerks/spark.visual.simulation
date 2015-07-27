package vs

import scalafx.Includes._
import scala.concurrent.ExecutionContext
import scalafx.application.JFXApp
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.chart.{NumberAxis, LineChart}
import scalafx.scene.control.{Label, Button, ToolBar}
import scalafx.scene.layout.VBox

object App extends JFXApp {
  private implicit def ec = ExecutionContext.global
  // AppInstance.init()

  val toolbar = new ToolBar {
    content = List( new Button { text = "Play" } )
  }

  val sourceLabel = new Label { text = "Source"}

  val sourceChart = new LineChart(NumberAxis("Seconds", 0, 100, 10), NumberAxis("Words", 0, 100, 10)) {
  }

  val flowLabel = new Label { text = "Flow"}

  val flowChart = new LineChart(NumberAxis("Seconds", 0, 100, 10), NumberAxis("Words", 0, 100, 10)) {
  }

  val sinkLabel = new Label { text = "Sink"}

  val sinkChart = new LineChart(NumberAxis("Seconds", 0, 100, 10), NumberAxis("Words", 0, 100, 10)) {
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
      onCloseRequest = handle {
        // AppInstance.destroy()
      }
    }
  }
}