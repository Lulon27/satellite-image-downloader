package ui

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import java.io.IOException

object WindowHandler
{
    data class StageInfo(val stage: Stage, val controller: Any? = null)

    lateinit var stagePrimary: StageInfo
    lateinit var stageCollections: StageInfo

    fun init(primaryStage: Stage)
    {
        stagePrimary = createStageFromFXMLRes("scene_main.fxml", primaryStage)
        stageCollections = createStageFromFXMLRes("scene_collections.fxml")

        stagePrimary.stage.width = 800.0
        stagePrimary.stage.height = 600.0
        stagePrimary.stage.title = "Satellite Imagery Downloader"

        stageCollections.stage.width = 600.0
        stageCollections.stage.height = 500.0
        stageCollections.stage.title = "Manage Collections"
    }

    private fun createStageFromFXMLRes(res: String, stage: Stage? = null): StageInfo
    {
        val sceneRoot: Parent?
        val controller: Any?
        val fxmlLoader = FXMLLoader()

        try
        {
            sceneRoot = fxmlLoader.load(ClassLoader.getSystemResource(res).openStream())
            controller = fxmlLoader.getController()
        }
        catch(e: IOException)
        {
            throw e
        }

        val newStage = stage ?: Stage()
        newStage.scene = Scene(sceneRoot)

        return StageInfo(newStage, controller)
    }
}