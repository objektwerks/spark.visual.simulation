package vs

import javafx.scene.{chart => jfxsc}
import javafx.{concurrent => jfxc}

import org.apache.commons.lang3.time.StopWatch

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.concurrent.Task
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.chart._
import scalafx.scene.control.TableColumn._
import scalafx.scene.control._
import scalafx.scene.layout.VBox

class RatingProperty(program_ : String, season_ : String, episode_ : String, rating_ : String) {
  def this(rating: Rating) {
    this(rating.program, rating.season.toString, rating.episode.toString, rating.rating.toString)
  }
  val program = new StringProperty(this, "program", program_)
  val season = new StringProperty(this, "season", season_)
  val episode = new StringProperty(this, "episode", episode_)
  val rating = new StringProperty(this, "rating", rating_)
}

object SimulationTask extends Task(new jfxc.Task[Result] {
  override def call(): Result = {
    updateMessage("Release the hounds...")
    val stopWatch = new StopWatch()
    stopWatch.start()
    val result = new Simulation().play()
    stopWatch.stop()
    val elapased = stopWatch.getTime / 1000
    updateMessage(s"${result.kafkaMessages.size} messages processed in $elapased seconds.")
    result
  }
})

object App extends JFXApp {
  val sourceLabel = new Label { text = "Source" }

  val sourceTable = new TableView[RatingProperty]() {
    columns ++= List(
      new TableColumn[RatingProperty, String] { text = "Program"; cellValueFactory = { _.value.program } },
      new TableColumn[RatingProperty, String] { text = "Season"; cellValueFactory = { _.value.season } },
      new TableColumn[RatingProperty, String] { text = "Episode"; cellValueFactory = { _.value.episode } },
      new TableColumn[RatingProperty, String] { text = "Rating"; cellValueFactory = { _.value.rating } }
    )
  }

  val flowLabel = new Label { text = "Flow" }

  val flowChart = new LineChart(NumberAxis(axisLabel = "Episodes", lowerBound = 1, upperBound = 10, tickUnit =1),
                                NumberAxis(axisLabel = "Ratings", lowerBound = 1, upperBound = 10, tickUnit = 1)) {
    title = "Episode Ratings"
  }

  val sinkLabel = new Label { text = "Sink" }

  val sinkChart = new PieChart { title = "Program Ratings" }

  val simulationPane = new VBox {
    maxWidth = 700
    maxHeight = 800
    spacing = 6
    padding = Insets(6)
    children = List(sourceLabel, sourceTable, flowLabel, flowChart, sinkLabel, sinkChart)
  }

  val playSimulationButton = new Button {
    text = "Play"
    disable <== SimulationTask.running
    onAction = { ae: ActionEvent => new Thread(SimulationTask).start() }
  }

  val simulationTaskCompleted = new ObjectProperty[Result]()
  simulationTaskCompleted <== SimulationTask.value
  simulationTaskCompleted.onChange({
    build(simulationTaskCompleted.value)
  })

  val progressIndicator = new ProgressIndicator {
    prefWidth = 50
    progress = -1.0
    visible <== SimulationTask.running
  }

  val statusBar = new Label {
    text <== SimulationTask.message
  }

  val toolbar = new ToolBar {
    content = List(playSimulationButton, new Separator(), progressIndicator, new Separator(), statusBar)
  }

  val appPane = new VBox {
    maxWidth = 700
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

  def build(result: Result): Unit = {
    buildSource(result)
    buildFlow(result)
    buildSink(result)
  }

  def buildSource(result: Result): Unit = {
    val messages: Seq[Rating] = result.kafkaMessages
    val model = new ObservableBuffer[RatingProperty]()
    messages foreach { r => model += new RatingProperty(r) }
    sourceTable.items = model
  }
  
  def buildFlow(result: Result): Unit = {
    val programs: Map[String, Seq[(Int, Int)]] = result.lineChartData
    val model = new ObservableBuffer[jfxsc.XYChart.Series[Number, Number]]()
    programs foreach { p =>
      val series = new XYChart.Series[Number, Number] { name = p._1 }
      p._2 foreach { t => series.data() += XYChart.Data[Number, Number]( t._1, t._2) }
      model += series
    }
    flowChart.data = model
  }

  def buildSink(result: Result): Unit = {
    val model: Seq[(String, Long)] = result.pieChartData
    sinkChart.data = model map { case (x, y) => PieChart.Data(x, y) }
  }
}