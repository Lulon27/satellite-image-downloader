package ui

import AutosaveHandler
import CollectionHandler
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.stage.DirectoryChooser
import tile.PersistentTileDownloader
import tile.TileDownloader
import java.lang.NumberFormatException
import java.net.URL
import java.util.*

class CollectionsWindowController : Initializable
{
    @FXML
    private lateinit var fxml_list: ListView<CollectionWrappers.CollectionWrapper>

    @FXML
    private lateinit var button_delete: Button

    @FXML
    private lateinit var textfield_name: TextField

    @FXML
    private lateinit var textfield_tileSize: TextField

    @FXML
    private lateinit var label_tileSize_status: Label

    @FXML
    private lateinit var textarea_url: TextArea

    @FXML
    private lateinit var textarea_preview: TextArea

    @FXML
    private lateinit var textfield_previewZ: TextField

    @FXML
    private lateinit var textfield_previewX: TextField

    @FXML
    private lateinit var textfield_previewY: TextField

    @FXML
    private lateinit var textfield_downloadFolder: TextField

    @FXML
    private lateinit var button_setDownloadFolder: Button

    @FXML
    private lateinit var label_downloadFolderStatus: Label

    private val dirChooser = DirectoryChooser()

    override fun initialize(location: URL?, resources: ResourceBundle?)
    {
        dirChooser.title = "Choose a folder for downloaded tiles"

        fxml_list.items = CollectionWrappers.wrappers

        button_delete.disableProperty().bind(fxml_list.selectionModel.selectedItemProperty().isNull)
        textfield_name.disableProperty().bind(fxml_list.selectionModel.selectedItemProperty().isNull)
        textfield_tileSize.disableProperty().bind(fxml_list.selectionModel.selectedItemProperty().isNull)
        textarea_url.disableProperty().bind(fxml_list.selectionModel.selectedItemProperty().isNull)
        button_setDownloadFolder.disableProperty().bind(fxml_list.selectionModel.selectedItemProperty().isNull)
        textfield_downloadFolder.disableProperty().bind(fxml_list.selectionModel.selectedItemProperty().isNull)

        fxml_list.selectionModel.selectedItemProperty().addListener { _, _, newVal ->
            textfield_name.text = newVal?.nameProperty?.get()
            textarea_url.text = newVal?.obj?.url
            textfield_tileSize.text = newVal?.obj?.tileSize.toString()
            textfield_downloadFolder.text = newVal?.obj?.downloadFolder
        }

        textfield_name.textProperty().addListener { _, _, newVal ->
            fxml_list.selectionModel.selectedItem?.nameProperty?.set(newVal.trim())
            AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_CONFIG, AutosaveHandler.DELAY_CONFIG)
        }

        textarea_url.textProperty().addListener { _, _, newVal ->
            val urlTrimmed = newVal?.trim() ?: ""
            fxml_list.selectionModel.selectedItem?.obj?.url = urlTrimmed
            TileDownloader.setDownloadURL(urlTrimmed)
            AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_CONFIG, AutosaveHandler.DELAY_CONFIG)
        }

        textfield_tileSize.textProperty().addListener { _, _, newVal ->
            if(newVal == null)
                return@addListener

            val trimmed = newVal.trim()
            if(trimmed.isEmpty())
            {
                label_tileSize_status.text = "Should not be empty"
                return@addListener
            }
            val intVal: Int
            try
            {
                intVal = trimmed.toInt()
            }
            catch(e: NumberFormatException)
            {
                label_tileSize_status.text = "Should be an integer number"
                return@addListener
            }
            label_tileSize_status.text = ""
            fxml_list.selectionModel.selectedItem?.obj?.tileSize?.set(intVal)
            AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_CONFIG, AutosaveHandler.DELAY_CONFIG)
        }

        textarea_preview.textProperty().bind(Bindings.createStringBinding({
            CollectionHandler.formatURL(
                textarea_url.text ?: "",
                textfield_previewX.text,
                textfield_previewY.text,
                textfield_previewZ.text
            )
        }, textarea_url.textProperty(), textfield_previewX.textProperty(), textfield_previewY.textProperty(), textfield_previewZ.textProperty()))
    }

    @FXML
    private fun onClickNew(event: ActionEvent)
    {
        CollectionHandler.newCollection("Unnamed", "")
        AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_CONFIG, AutosaveHandler.DELAY_CONFIG)
    }

    @FXML
    private fun onClickDelete(event: ActionEvent)
    {
        CollectionHandler.removeCollection(fxml_list.selectionModel.selectedItem.obj)
        AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_CONFIG, AutosaveHandler.DELAY_CONFIG)
    }

    @FXML
    private fun onClickSetDownloadFolder(event: ActionEvent)
    {
        val mainWindowController = WindowHandler.stagePrimary.controller as MainWindowController
        val file = dirChooser.showDialog(WindowHandler.stageCollections.stage) ?: return
        fxml_list.selectionModel.selectedItem?.obj?.downloadFolder = file.path
        textfield_downloadFolder.text = file.path

        if(mainWindowController.combobox_collections.selectionModel.selectedItem == null)
        {
            val result = PersistentTileDownloader.fastCheckFolder(file)
            label_downloadFolderStatus.text = result.msg
            println(result.msg)
            // Skip the rest if no collection is selected in the main window
            return
        }

        var ex: Exception? = null
        try
        {
            PersistentTileDownloader.setDownloadFolder(file.path)
        }
        catch (e: Exception)
        {
            ex = e
        }
        if(ex == null)
        {
            label_downloadFolderStatus.text = ""
            mainWindowController.label_downloadFolderStatus.text = ""
            mainWindowController.hasDownloadFolderError.value = false
        }
        else
        {
            label_downloadFolderStatus.text = ex.message
            mainWindowController.label_downloadFolderStatus.text = ex.message
            mainWindowController.hasDownloadFolderError.value = true
        }
    }
}