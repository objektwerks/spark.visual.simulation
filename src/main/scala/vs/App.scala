package vs

import javafx.scene.{chart => jfxsc}

import scala.concurrent.ExecutionContext
import scalafx.Includes._
import scalafx.application.{Platform, JFXApp}
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

  val sourceTable = new TableView[Rating]() {
    columns += new TableColumn[Rating, String]("Program")
    columns += new TableColumn[Rating, Int]("Season")
    columns += new TableColumn[Rating, Int]("Episode")
    columns += new TableColumn[Rating, Int]("Rating")
  }

  val flowLabel = new Label {
    text = "Flow"
  }

  val flowChart = new LineChart(NumberAxis(axisLabel = "Episodes", lowerBound = 1, upperBound = 10, tickUnit =1),
                                NumberAxis(axisLabel = "Ratings", lowerBound = 1, upperBound = 10, tickUnit = 1)) {
    title = "Episode Ratings"
  }

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
      stylesheets foreach println
      root = appPane
    }
  }

  def hanldePlaySimulationButton(): Unit = {
    playSimulationButton.disable = true
    val simulation = new Simulation()
    val result = simulation.play()
    result map { r =>
      Platform.runLater(buildSource(r))
      Platform.runLater(buildFlow(r))
      Platform.runLater(buildSink(r))
    }
    playSimulationButton.disable = false
  }
  
  def buildSource(result: Result): Unit = {
    val messages: Seq[Rating] = result.producedKafkaMessages
    val model = new ObservableBuffer[Rating]()
    messages map(model += _)
    sourceTable.items = model
  }
  
  def buildFlow(result: Result): Unit = {
    val programs: Map[String, Seq[(Int, Int)]] = result.selectedLineChartDataFromCassandra
    val model = new ObservableBuffer[jfxsc.XYChart.Series[Number, Number]]()
    programs foreach { p =>
      val series = new XYChart.Series[Number, Number] { name = p._1 }
      p._2 foreach { t => series.data() += XYChart.Data[Number, Number]( t._1, t._2) }
      model += series
    }
    flowChart.data = model
  }

  def buildSink(result: Result): Unit = {
    val model: Seq[(String, Long)] = result.selectedPieChartDataFromCassandra
    sinkChart.data = model map { case (x, y) => PieChart.Data(x, y) }
  }
}