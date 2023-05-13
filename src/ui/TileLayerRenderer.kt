package ui

import util.MapUtil
import java.awt.geom.Rectangle2D
import kotlin.math.pow

class TileLayerRenderer
{
    var posX: Double = 268.7217777777778 / 512
        private set
    var posY: Double = 168.4189932310405 / 512
        private set

    var tileX: Long = 0
        private set
    var tileY: Long = 0
        private set

    var numTilesX = 0
        private set
    var numTilesY = 0
        private set

    var zoomLevel = 0
        private set
    var drawnZoomLevel = 0
        private set

    var tileScale = 1.0

    var tileSizePx = 256
        private set

    private var width = 0.0
    private var height = 0.0

    fun drawAll(renderFunc: (tileX: Long, tileY: Long, x: Double, y: Double, width: Double, height: Double) -> Unit)
    {
        val maxTiles = MapUtil.getNumTilesOnZ(this.drawnZoomLevel)

        var currentTileX: Long
        var currentTileY: Long
        var currentTileImgX: Long
        var currentTileImgY: Long

        val pixelCoords = Rectangle2D.Double()

        for (x in 0 until this.numTilesX)
        {
            for (y in 0 until this.numTilesY)
            {
                currentTileX = this.tileX + x - this.numTilesX / 2
                currentTileY = this.tileY + y - this.numTilesY / 2
                currentTileImgX = (currentTileX % maxTiles + maxTiles) % maxTiles
                currentTileImgY = (currentTileY % maxTiles + maxTiles) % maxTiles

                calculateTilePixelCoordinates(pixelCoords, currentTileX, currentTileY, this.zoomLevel, drawnZoomLevel)

                renderFunc(currentTileImgX, currentTileImgY, pixelCoords.x, pixelCoords.y, pixelCoords.width, pixelCoords.height)
            }
        }
    }

    fun drawTile(tileX: Long, tileY: Long, renderFunc: (x: Double, y: Double, width: Double, height: Double) -> Unit)
    {
        val pixelCoords = Rectangle2D.Double()
        calculateTilePixelCoordinates(pixelCoords, tileX, tileY, this.zoomLevel, drawnZoomLevel)
        renderFunc(pixelCoords.x, pixelCoords.y, pixelCoords.width, pixelCoords.height)
    }

    private fun calculateTilePixelCoordinates(outData: Rectangle2D.Double, tileX: Long, tileY: Long, zoomLevelCamera: Int, zoomLevelTile: Int)
    {
        val pixelPerWorldCoord = MapUtil.pixelPerWorldCoord(zoomLevelCamera, this.tileSizePx) * this.tileScale
        val tileSize = this.tileSizePx * this.tileScale * 2.0.pow(zoomLevelCamera - zoomLevelTile)
        outData.x = tileX * tileSize - this.posX * pixelPerWorldCoord + width * 0.5
        outData.y = tileY * tileSize - this.posY * pixelPerWorldCoord + height * 0.5
        outData.width = tileSize
        outData.height = tileSize
    }

    private fun calculateCurrentTile()
    {
        this.tileX = MapUtil.worldToTileCoords(this.posX, this.drawnZoomLevel)
        this.tileY = MapUtil.worldToTileCoords(this.posY, this.drawnZoomLevel)
    }

    private fun calculateTileAreaSize()
    {
        val tileSize = this.tileSizePx * this.tileScale * 2.0.pow(zoomLevel - drawnZoomLevel)
        this.numTilesX = (width / tileSize).toInt() + 3
        this.numTilesY = (height / tileSize).toInt() + 3
    }

    fun setMapTileSize(tileSize: Int)
    {
        this.tileSizePx = tileSize
        this.calculateTileAreaSize()
    }

    fun setMapPosition(x: Double, y: Double)
    {
        this.posX = x
        this.posY = y
        this.calculateCurrentTile()
    }

    fun setViewportSize(width: Double, height: Double)
    {
        this.width = width
        this.height = height
        this.calculateTileAreaSize()
    }

    fun setMapZoomLevel(zoomLevel: Int)
    {
        this.zoomLevel = zoomLevel
        // calculateCurrentTile()
        calculateTileAreaSize()
    }

    fun setDrawnMapZoomLevel(zoomLevel: Int)
    {
        this.drawnZoomLevel = zoomLevel
        calculateCurrentTile()
        calculateTileAreaSize()
    }
}