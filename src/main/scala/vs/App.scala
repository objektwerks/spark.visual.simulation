package vs

import javafx.scene.{chart => jfxsc}

import scala.io.Source
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.chart._
import scalafx.scene.control.TableColumn._
import scalafx.scene.control._
import scalafx.scene.layout.VBox

class RatingView(program_ : String, season_ : String, episode_ : String, rating_ : String) {
  def this(rating: Rating) {
    this(rating.program, rating.season.toString, rating.episode.toString, rating.rating.toString)
  }
  val program = new StringProperty(this, "program", program_)
  val season = new StringProperty(this, "season", season_)
  val episode = new StringProperty(this, "episode", episode_)
  val rating = new StringProperty(this, "rating", rating_)
}

object App extends JFXApp {
  val sourceLabel = new Label {
    text = "Source"
  }

  val sourceTable = new TableView[RatingView]() {
    columns ++= List(
      new TableColumn[RatingView, String] { text = "Program"; cellValueFactory = { _.value.program } },
      new TableColumn[RatingView, String] { text = "Season"; cellValueFactory = { _.value.season } },
      new TableColumn[RatingView, String] { text = "Episode"; cellValueFactory = { _.value.episode } },
      new TableColumn[RatingView, String] { text = "Rating"; cellValueFactory = { _.value.rating } }
    )
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
      root = appPane
    }
  }

  def hanldePlaySimulationButton(): Unit = {
    playSimulationButton.disable = true
    val simulation = new Simulation()
    val result = simulation.play()
    buildSource(result)
    buildFlow(result)
    buildSink(result)
    playSimulationButton.disable = false
  }

  def buildSource(result: Result): Unit = {
    val messages: Seq[Rating] = result.producedKafkaMessages
    val model = new ObservableBuffer[RatingView]()
    messages foreach { r => model += new RatingView(r) }
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