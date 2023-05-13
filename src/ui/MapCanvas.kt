package ui

import util.MapUtil
import javafx.animation.AnimationTimer
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.event.EventHandler
import javafx.geometry.VPos
import javafx.scene.canvas.Canvas
import javafx.scene.image.Image
import javafx.scene.input.MouseButton
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment
import tile.*
import util.Point
import java.awt.geom.Rectangle2D
import kotlin.math.pow

class MapCanvas : Canvas()
{
    private var mouseXprev = 0.0
    private var mouseYprev = 0.0
    private var mouseX = 0.0
    private var mouseY = 0.0

    private var zoom = 0.0

    private var timerShouldPause = false

    private var animTimer: AnimationTimer? = null

    private var selectionMode = false
    private var selectionModeZoomLevel = 0
    private val selection = ArrayList<Point>(128)
    private var selectionTileX: Long = 0
    private var selectionTileY: Long = 0

    private val renderer = TileLayerRenderer()

    val showGridProperty = SimpleBooleanProperty()
    val gridLevelProperty = SimpleIntegerProperty()

    init
    {
        this.onMousePressed = EventHandler {
            this.mouseXprev = it.x;
            this.mouseYprev = it.y;

            if(selectionMode && it.isPrimaryButtonDown)
            {
                val maxTiles = MapUtil.getNumTilesOnZ(selectionModeZoomLevel)
                val xMod = (selectionTileX % maxTiles + maxTiles) % maxTiles
                val yMod = (selectionTileY % maxTiles + maxTiles) % maxTiles
                PersistentTileDownloader.downloadTile(xMod, yMod, selectionModeZoomLevel)
            }
        }

        this.onMouseDragged = EventHandler {
            if(selectionMode && it.isPrimaryButtonDown)
            {
                return@EventHandler
            }
            val mouseMovedX = it.x - this.mouseXprev
            val mouseMovedY = it.y - this.mouseYprev
            this.mouseXprev = it.x
            this.mouseYprev = it.y
            mouseX = it.x
            mouseY = it.y
            val worldCoordsPerPixel = MapUtil.worldCoordsPerPixel(this.renderer.zoomLevel, (this.renderer.tileSizePx * this.renderer.tileScale).toInt())
            this.setMapPosition(this.renderer.posX - mouseMovedX * worldCoordsPerPixel, this.renderer.posY - mouseMovedY * worldCoordsPerPixel)
        }

        this.onMouseMoved = EventHandler {
            mouseX = it.x
            mouseY = it.y
            if(selectionMode)
            {
                val mouseXDiff = mouseX - width / 2
                val mouseYDiff = mouseY - height / 2
                val worldCoordsPerPixel = MapUtil.worldCoordsPerPixel(this.renderer.zoomLevel, (this.renderer.tileSizePx * this.renderer.tileScale).toInt())
                val mouseXworld = this.renderer.posX + mouseXDiff * worldCoordsPerPixel
                val mouseYworld = this.renderer.posY + mouseYDiff * worldCoordsPerPixel
                selectionTileX = MapUtil.worldToTileCoords(mouseXworld, selectionModeZoomLevel)
                selectionTileY = MapUtil.worldToTileCoords(mouseYworld, selectionModeZoomLevel)
            }
        }

        this.onScroll = EventHandler {
            this.zoom += it.deltaY * 0.003
            if(zoom < 0.0)
            {
                zoom = 0.0
            }
            val newZoomLevel = maxOf(0.0, this.zoom + 1).toInt()
            if (newZoomLevel != this.renderer.zoomLevel) {
                setMapZoomLevel(newZoomLevel)
            }
            this.renderer.tileScale = 0.5 + (this.zoom - this.zoom.toInt()) * 0.5
            renderer.setViewportSize(width, height)
        }

        this.widthProperty().addListener { _, _, newVal ->
            renderer.setViewportSize(newVal.toDouble(), height)
            this.setDownloadArea()
        }

        this.heightProperty().addListener { _, _, newVal ->
            renderer.setViewportSize(width, newVal.toDouble())
            this.setDownloadArea()
        }

        this.animTimer = object : AnimationTimer()
        {
            override fun handle(now: Long) {
                draw()
                if (timerShouldPause) {
                    animTimer!!.stop()
                }
            }
        }
        (this.animTimer as AnimationTimer).start()
    }

    private fun draw()
    {
        clear()

        // Draw main
        renderer.setDrawnMapZoomLevel(renderer.zoomLevel)
        renderer.drawAll { tileX, tileY, x, y, width, height ->
            var currentTileImgX = tileX
            var currentTileImgY = tileY
            val tileXworld = MapUtil.tileToWorldCoords(currentTileImgX, this.renderer.zoomLevel)
            val tileYworld = MapUtil.tileToWorldCoords(currentTileImgY, this.renderer.zoomLevel)
            var texCoordX = 0.0
            var texCoordY = 0.0
            var texCoordWidth = 2.0
            var tileInfo: TileJavaFXImage?

            var currentZclimbs = 0

            do
            {
                val tileX2 = MapUtil.worldToTileCoordsRaw(tileXworld, this.renderer.zoomLevel - currentZclimbs)
                val tileY2 = MapUtil.worldToTileCoordsRaw(tileYworld, this.renderer.zoomLevel - currentZclimbs)
                currentTileImgX = tileX2.toLong()
                currentTileImgY = tileY2.toLong()
                texCoordX = tileX2 - currentTileImgX
                texCoordY = tileY2 - currentTileImgY
                texCoordWidth /= 2.0
                tileInfo = TileCache.getTile(currentTileImgX, currentTileImgY, this.renderer.zoomLevel - currentZclimbs) as TileJavaFXImage?
                currentZclimbs++
            }
            while (tileInfo == null && currentZclimbs < minOf(10, this.renderer.zoomLevel + 1))

            drawTile(
                tileInfo?.tileImage,
                x,
                y,
                width,
                height,
                currentTileImgX,
                currentTileImgY,
                texCoordX * renderer.tileSizePx,
                texCoordY * renderer.tileSizePx,
                texCoordWidth * renderer.tileSizePx
            )
        }

        if(selectionMode && selectionModeZoomLevel - renderer.zoomLevel < 6)
        {
            // Draw only if the selection is not more than 5 zoom levels away

            renderer.setDrawnMapZoomLevel(selectionModeZoomLevel)
            renderer.drawAll { tileX, tileY, x, y, width, height ->

                val state = TileCache.getPersistentState(tileX, tileY, selectionModeZoomLevel)

                if(state == TileCache.PersistentState.NOT_PERSISTENT)
                {
                    return@drawAll
                }
                else if (state == TileCache.PersistentState.DOWNLOADING)
                {
                    graphicsContext2D.fill = Color.color(0.0, 0.0, 0.0, 0.25)
                    graphicsContext2D.fillRect(x, y, width, height)
                }
                else if (state == TileCache.PersistentState.PERSISTENT)
                {
                    graphicsContext2D.fill = Color.color(0.0, 1.0, 0.0, 0.25)
                    graphicsContext2D.fillRect(x, y, width, height)
                }
                else if (state == TileCache.PersistentState.DELETING)
                {
                    graphicsContext2D.fill = Color.color(1.0, 0.0, 1.0, 0.25)
                    graphicsContext2D.fillRect(x, y, width, height)
                }
                else if (state == TileCache.PersistentState.FAILED)
                {
                    graphicsContext2D.fill = Color.color(1.0, 0.0, 0.0, 0.25)
                    graphicsContext2D.fillRect(x, y, width, height)
                }
            }

            renderer.drawTile(selectionTileX, selectionTileY) { x: Double, y: Double, width: Double, height: Double ->
                graphicsContext2D.fill = Color.color(1.0, 1.0, 0.75, 0.25)
                graphicsContext2D.fillRoundRect(x, y, width, height, 15.0, 15.0)
                graphicsContext2D.stroke = Color.color(0.0, 0.0, 0.0, 0.5)
                graphicsContext2D.strokeRoundRect(x, y, width, height, 15.0, 15.0)
                graphicsContext2D.fill = Color.BLACK
                graphicsContext2D.stroke = Color.BLACK

                val maxWidth = width
                val maxHight = height
                val str = "X: $selectionTileX\nY: $selectionTileY"
                val font = Font(18.0)
                val text = Text(str)
                text.font = font
                text.wrappingWidth = 0.0
                text.lineSpacing = 0.0

                graphicsContext2D.fill = Color.color(0.0, 0.0, 0.0, 0.4)
                graphicsContext2D.fillRoundRect(x + 5, y + 5, minOf(text.layoutBounds.width + 10, maxWidth - 10), minOf(text.layoutBounds.height, maxHight - 10), 15.0, 15.0)

                graphicsContext2D.fill = Color.color(1.0, 1.0, 1.0, 0.4)
                graphicsContext2D.font = font
                graphicsContext2D.textAlign = TextAlignment.LEFT
                graphicsContext2D.textBaseline = VPos.TOP
                graphicsContext2D.fillText(str, x + 10, y + 5, maxWidth - 20)
            }
        }

        if(showGridProperty.value && gridLevelProperty.value - renderer.zoomLevel < 6)
        {
            // Draw only if the selection is not more than 5 zoom levels away

            renderer.setDrawnMapZoomLevel(gridLevelProperty.value)
            renderer.drawAll { tileX, tileY, x, y, width, height ->
                graphicsContext2D.stroke = Color.BLACK
                graphicsContext2D.strokeRect(x, y, width, height)
            }
        }

        this.graphicsContext2D.fill = Color.RED
        this.graphicsContext2D.fillRect(width / 2 - 2, height / 2 - 2, 4.0, 4.0)
        this.graphicsContext2D.fill = Color.BLACK
        this.timerShouldPause = false
    }

    private fun clear()
    {
        this.graphicsContext2D.fill = Color.DIMGRAY
        this.graphicsContext2D.fillRect(0.0, 0.0, this.width, this.height)
        this.graphicsContext2D.fill = Color.BLACK
    }

    private fun drawTile(img: Image?, x: Double, y: Double, width: Double, height: Double, tileX: Long, tileY: Long, srcX: Double, srcY: Double, srcSize: Double)
    {
        if (img == null)
        {
            graphicsContext2D.stroke = Color.color(0.0, 0.0, 0.0, 0.25)
            this.graphicsContext2D.font = Font(30.0)
            this.graphicsContext2D.strokeRect(x, y, width, height)
        }
        else
        {
            this.graphicsContext2D.drawImage(img, srcX, srcY, srcSize, srcSize, x, y, width, height)
        }
    }

    private fun setDownloadArea()
    {
        this.renderer.setDrawnMapZoomLevel(renderer.zoomLevel)
        val topLeftX = this.renderer.tileX - this.renderer.numTilesX / 2
        val topLeftY = this.renderer.tileY - this.renderer.numTilesY / 2
        TileDownloader.setRequestedArea(topLeftX, topLeftY, topLeftX + this.renderer.numTilesX, topLeftY + this.renderer.numTilesY, this.renderer.zoomLevel)
    }

    fun setMapZoomLevel(zoomLevel: Int)
    {
        this.renderer.setMapZoomLevel(zoomLevel)
        setDownloadArea()
    }

    fun setMapTileSize(tileSize: Int)
    {
        this.renderer.setMapTileSize(tileSize)
        this.setDownloadArea()
    }

    fun setMapPosition(x: Double, y: Double)
    {
        renderer.setMapPosition(x, y)
        this.setDownloadArea()
    }

    fun startSelectionMode(zoomLevel: Int)
    {
        if(selectionMode)
        {
            return
        }
        selectionMode = true
        selectionModeZoomLevel = zoomLevel
        selection.clear()
    }

    fun quitSelectionMode()
    {
        if(!selectionMode)
        {
            return
        }
        selectionMode = false
    }
}