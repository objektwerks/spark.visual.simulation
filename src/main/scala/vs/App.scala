package vs

import javafx.scene.{chart => jfxsc}

import scala.concurrent.ExecutionContext
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.chart._
import scalafx.scene.control._
import scalafx.scene.layout.VBox

object App extends JFXApp {
  implicit def ec = ExecutionContext.global

  val sourceLabel = new Label {
    text = "Source"
  }

  val flowLabel = new Label {
    text = "Flow"
  }

  val flowChart = new LineChart(NumberAxis(axisLabel = "Episodes", lowerBound = 1, upperBound = 10, tickUnit =1),
                                NumberAxis(axisLabel = "Ratings", lowerBound = 1, upperBound = 10, tickUnit = 1))

  val sinkLabel = new Label {
    text = "Sink"
  }

  val sinkChart = new PieChart {
    clockwise = false
  }

  val simulationPane = new VBox {
    children = List(sourceLabel, flowLabel, flowChart, sinkLabel, sinkChart)
  }

  val playSimulationButton = new Button {
    text = "Play"
    onAction = handle { hanldePlaySimulationButton() }
  }

  def hanldePlaySimulationButton(): Unit = {
    try {
      val simulation = new Simulation()
      val result = simulation.play()
      playSimulationButton.disable = true
      sourceLabel.text = s"Source: ${result.producedKafkaMessages} messages produced for topic ratings."

      val programs: Map[String, Seq[(Long, Long)]] = result.selectedLineChartDataFromCassandra
      val model = new ObservableBuffer[jfxsc.XYChart.Series[Number, Number]]()
      programs foreach { p =>
        val series = new XYChart.Series[Number, Number] { name = p._1 }
        p._2 foreach { t =>
          series.data() += XYChart.Data[Number, Number]( t._1, t._2)
        }
        model += series
      }
      flowChart.data = model

      sinkChart.data = result.selectedPieChartDataFromCassandra map { t => PieChart.Data(t._1, t._2) }
    } finally {
      playSimulationButton.disable = false
    }
  }

  def toChartData = (xy: (Double, Double)) => XYChart.Data[Number, Number](xy._1, xy._2)

  val toolbar = new ToolBar {
    content = List(playSimulationButton)
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