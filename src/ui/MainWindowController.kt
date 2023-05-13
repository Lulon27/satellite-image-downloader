package ui

import AutosaveHandler
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.layout.AnchorPane
import tile.PersistentTileDownloader
import tile.TileDownloader
import java.net.URL
import java.util.*

class MainWindowController : Initializable
{
    @FXML
    private lateinit var canvas_container: AnchorPane

    @FXML
    private lateinit var label_selectCollection: Label

    @FXML
    private lateinit var slider_request_pause: Slider

    @FXML
    private lateinit var slider_grid: Slider

    @FXML
    private lateinit var spinner_request_pause: Spinner<Double>

    @FXML
    private lateinit var spinner_grid: Spinner<Double>

    @FXML
    private lateinit var checkbox_grid: CheckBox

    @FXML
    private lateinit var slider_selectionZoomLevel: Slider

    @FXML
    private lateinit var spinner_selectionZoomLevel: Spinner<Double>

    @FXML
    private lateinit var button_startSelecting: Button

    @FXML
    private lateinit var button_cancelSelection: Button

    @FXML
    private lateinit var button_finishSelection: Button

    @FXML
    private lateinit var button_edit_collections: Button

    @FXML
    lateinit var label_downloadFolderStatus: Label

    @FXML
    lateinit var combobox_collections: ComboBox<CollectionWrappers.CollectionWrapper>

    private val canvas = MapCanvas()

    val isInSelectionMode = SimpleBooleanProperty(false)
    val hasDownloadFolderError = SimpleBooleanProperty(false)
    val isPersistentTileDownloaderLoading = SimpleBooleanProperty(false)

    override fun initialize(location: URL?, resources: ResourceBundle?)
    {
        CollectionWrappers.refresh()
        combobox_collections.items = CollectionWrappers.wrappers
        combobox_collections.disableProperty().bind(isInSelectionMode)

        combobox_collections.selectionModel.selectedItemProperty().addListener { _, _, newVal ->
            if(newVal == null)
            {
                TileDownloader.setDownloadURL(null)
                PersistentTileDownloader.downloadURL = null
                this.label_selectCollection.isVisible = true
                this.canvas.isVisible = false
                return@addListener
            }
            TileDownloader.setDownloadURL(newVal.obj.url)
            PersistentTileDownloader.downloadURL = newVal.obj.url

            var ex: Exception? = null
            try
            {
                PersistentTileDownloader.setDownloadFolder((newVal.obj.downloadFolder))
            }
            catch (e: Exception)
            {
                ex = e
            }
            if(ex == null)
            {
                label_downloadFolderStatus.text = ""
                hasDownloadFolderError.value = false
            }
            else
            {
                label_downloadFolderStatus.text = ex.message
                hasDownloadFolderError.value = true
            }

            this.label_selectCollection.isVisible = false
            this.canvas.isVisible = true
            canvas.setMapTileSize(newVal.obj.tileSize.get())
        }

        combobox_collections.selectionModel.select(0)

        button_edit_collections.disableProperty().bind(isInSelectionMode)

        slider_selectionZoomLevel.disableProperty().bind(isInSelectionMode)
        spinner_selectionZoomLevel.disableProperty().bind(isInSelectionMode)

        spinner_request_pause.valueProperty().addListener { _, _, newValue ->
            TileDownloader.requestPause.set(newValue.toInt())
            PersistentTileDownloader.requestPause.set(newValue.toInt())
        }

        canvas.gridLevelProperty.bind(spinner_grid.valueProperty())
        canvas.showGridProperty.bind(checkbox_grid.selectedProperty())

        this.canvas_container.widthProperty().addListener {
            _, _, newVal -> this.canvas.width = newVal.toDouble()
        }
        this.canvas_container.heightProperty().addListener {
            _, _, newVal -> this.canvas.height = newVal.toDouble()
        }
        this.canvas_container.children.add(this.canvas)

        button_startSelecting.disableProperty().bind(combobox_collections.selectionModel.selectedItemProperty().isNull.or(isInSelectionMode).or(hasDownloadFolderError).or(isPersistentTileDownloaderLoading))
        button_cancelSelection.disableProperty().bind(isInSelectionMode.not())
        button_finishSelection.disableProperty().bind(isInSelectionMode.not())

        label_downloadFolderStatus.visibleProperty().bind(hasDownloadFolderError)

        JavaFXUtil.sliderSpinnerLink(this.slider_grid, this.spinner_grid, 0.0, 21.0, 0.0, 30.0, 2.0,1.0, true);
        JavaFXUtil.sliderSpinnerLink(this.slider_request_pause, this.spinner_request_pause, 50.0, 1000.0, 20.0, 10000.0, 500.0,1.0, true);
        JavaFXUtil.sliderSpinnerLink(this.slider_selectionZoomLevel, this.spinner_selectionZoomLevel, 0.0, 21.0, 0.0, 30.0, 2.0,1.0, true);
    }

    @FXML
    private fun onClickEditCollections(event: ActionEvent)
    {
        WindowHandler.stageCollections.stage.show()
    }

    @FXML
    private fun onClickStartSelecting(event: ActionEvent)
    {
        isPersistentTileDownloaderLoading.value = true
        isInSelectionMode.value = true
        canvas.startSelectionMode(spinner_selectionZoomLevel.value.toInt())
        PersistentTileDownloader.loadPersistentInfo {
            Platform.runLater {
                isPersistentTileDownloaderLoading.value = false
            }
        }
    }

    @FXML
    private fun onClickSelectionCancel(event: ActionEvent)
    {
        PersistentTileDownloader.cancelDownloads()
        isInSelectionMode.value = false
        canvas.quitSelectionMode()
        AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_PERSISTENT_INFO, 0)
    }

    @FXML
    private fun onClickSelectionFinish(event: ActionEvent)
    {
        PersistentTileDownloader.cancelDownloads()
        isInSelectionMode.value = false
        canvas.quitSelectionMode()
        AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_PERSISTENT_INFO, 0)
    }
}