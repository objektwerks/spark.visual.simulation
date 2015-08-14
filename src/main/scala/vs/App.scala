package vs

import javafx.scene.{chart => jfxsc}
import javafx.{concurrent => jfxc}

import org.apache.commons.lang3.time.StopWatch

import scala.concurrent.ExecutionContext
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

object SimulationTask extends Task(new jfxc.Task[Result] {
  override def call(): Result = {
    updateMessage("Release the hounds...")
    val stopWatch = new StopWatch()
    stopWatch.start()
    val result = new Simulation().play()
    stopWatch.stop()
    val elapased = stopWatch.getTime / 1000
    updateMessage(s"${result.ratings.size} messages processed in $elapased seconds.")
    result
  }
})

object App extends JFXApp {
  class RatingProperty(program_ : String, season_ : String, episode_ : String, rating_ : String) {
    def this(rating: (String, String, String, String)) {
      this(rating._1, rating._2, rating._3, rating._4)
    }
    val program = new StringProperty(this, "program", program_)
    val season = new StringProperty(this, "season", season_)
    val episode = new StringProperty(this, "episode", episode_)
    val rating = new StringProperty(this, "rating", rating_)
  }

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
    maxHeight = 820
    spacing = 6
    padding = Insets(6)
    children = List(sourceLabel, sourceTable, flowLabel, flowChart, sinkLabel, sinkChart)
  }

  val playSimulationButton = new Button {
    text = "Play"
    disable <== SimulationTask.running
    onAction = { ae: ActionEvent => ExecutionContext.global.execute(SimulationTask) }
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
    maxHeight = 820
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
    buildSourceTable(result.ratings)
    buildFlowChart(result.programToEpisodesRatings)
    buildSinkChart(result.programRatings)
  }

  def buildSourceTable(ratings: Seq[(String, String, String, String)]): Unit = {
    val model = new ObservableBuffer[RatingProperty]()
    ratings foreach { rating =>
      model += new RatingProperty(rating)
    }
    sourceTable.items = model
  }

  def buildFlowChart(programToEpisodesRatings: Map[String, Seq[(Int, Int)]]): Unit = {
    val model = new ObservableBuffer[jfxsc.XYChart.Series[Number, Number]]()
    programToEpisodesRatings foreach { program =>
      val series = new XYChart.Series[Number, Number] { name = program._1 }
      program._2 foreach { episodeAndRating =>
        series.data() += XYChart.Data[Number, Number]( episodeAndRating._1, episodeAndRating._2)
      }
      model += series
    }
    flowChart.data = model
  }

  def buildSinkChart(programRatings: Seq[(String, Long)]): Unit = {
    val ratingTotal: Float = programRatings.map(_._2).sum
    sinkChart.data = programRatings map {
      case (program, rating) => PieChart.Data( f"$program(${ (rating / ratingTotal) * 100}%.0f%%)", rating )
    }
  }
}