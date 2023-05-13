import javafx.application.Application
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tile.TileDownloader
import ui.MainWindowController
import ui.WindowHandler

class Main : Application()
{
    override fun start(primaryStage: Stage)
    {
        WindowHandler.init(primaryStage)

        WindowHandler.stagePrimary.stage.setOnHidden {
            AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_CONFIG, 0)
            if((WindowHandler.stagePrimary.controller as MainWindowController).isInSelectionMode.value)
            {
                AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_PERSISTENT_INFO, 0)
            }
            println("Stopping tile downloader...")
            TileDownloader.stopAndJoin()
            println("Tile downloader stopped")
            AutosaveHandler.join()
        }

        AutosaveHandler.restoreSettings()

        WindowHandler.stagePrimary.stage.show()

        TileDownloader.start()
    }
}
