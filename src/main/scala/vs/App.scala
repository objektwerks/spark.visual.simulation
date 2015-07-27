package vs

import scala.concurrent.ExecutionContext
import scalafx.application.JFXApp
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.layout.VBox

object App extends JFXApp {
  private implicit def ec = ExecutionContext.global

  private val appPane = new VBox {
    maxWidth = 400
    maxHeight = 800
    spacing = 6
    padding = Insets(6)
  }


  stage = new JFXApp.PrimaryStage {
    title.value = "Visual Spark"
    scene = new Scene {
      root = appPane
    }
  }
}