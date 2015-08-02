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

  val sourceTable = new TableView[(String, Int, Int, Int)]() {
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
    title = "Program Ratings"
    clockwise = false
  }

  val simulationPane = new VBox {
    maxWidth = 800
    maxHeight = 800
    spacing = 6
    padding = Insets(6)
    children = List(sourceLabel, sourceTable, flowLabel, flowChart, sinkLabel, sinkChart)
  }

  val playSimulationButton = new Button {
    text = "Play"
    onAction = handle { hanldePlaySimulationButton() }
  }

  val toolbar = new ToolBar {
    content = List(playSimulationButton)
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

  def hanldePlaySimulationButton(): Unit = {
    try {
      val simulation = new Simulation()
      val result = simulation.play()
      playSimulationButton.disable = true
      buildSource(result)
      buildFlow(result)
      buildSink(result)
    } finally {
      playSimulationButton.disable = false
    }
  }
  
  def buildSource(result: Result): Unit = {
    val messages: Seq[(String, Int, Int, Int)] = result.producedKafkaMessages
    sourceLabel.text = s"Source: ${messages.size} messages produced for topic ratings."
  }
  
  def buildFlow(result: Result): Unit = {
    val programs: Map[String, Seq[(Long, Long)]] = result.selectedLineChartDataFromCassandra
    val flowModel = new ObservableBuffer[jfxsc.XYChart.Series[Number, Number]]()
    programs foreach { p =>
      val series = new XYChart.Series[Number, Number] { name = p._1 }
      p._2 foreach { t =>
        series.data() += XYChart.Data[Number, Number]( t._1, t._2)
      }
      flowModel += series
    }
    flowChart.data = flowModel
  }

  def buildSink(result: Result): Unit = {
    sinkChart.data = result.selectedPieChartDataFromCassandra map { case (x, y) => PieChart.Data(x, y) }
  }
}