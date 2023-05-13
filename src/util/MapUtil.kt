package util

import kotlin.math.floor
import kotlin.math.pow

object MapUtil
{
    fun worldToTileCoords(world: Double, zoomLevel: Int): Long
    {
        return floor(world * 2.0.pow(zoomLevel.toDouble())).toLong()
    }

    fun worldToTileCoordsRaw(world: Double, zoomLevel: Int): Double
    {
        return world * 2.0.pow(zoomLevel.toDouble())
    }

    fun tileToWorldCoords(tile: Long, zoomLevel: Int): Double
    {
        return tile / 2.0.pow(zoomLevel.toDouble())
    }

    fun getNumTilesOnZ(zoomLevel: Int): Long
    {
        return 2.0.pow(zoomLevel.toDouble()).toLong()
    }

    fun worldCoordsPerPixel(zoomLevel: Int, tileSize: Int): Double
    {
        return 1 / (2.0.pow(zoomLevel.toDouble()) * tileSize)
    }

    fun pixelPerWorldCoord(zoomLevel: Int, tileSize: Int): Long
    {
        return 2.0.pow(zoomLevel.toDouble()).toLong() * tileSize
    }

    fun map2Dto1D(x: Long, y: Long, gridWidth: Int): Long
    {
        return gridWidth * y + x;
    }

    fun calculateTreeLayerSizes(zoomLevel: Int, maxPower: Int): ArrayList<Int>
    {
        if(zoomLevel == 0)
        {
            return arrayListOf(1)
        }
        val values = ArrayList<Int>()

        if(zoomLevel % maxPower != 0)
        {
            values.add(2.0.pow(zoomLevel % maxPower).toInt())
        }

        values.addAll(List(zoomLevel / maxPower) { 2.0.pow(maxPower).toInt() })

        return values
    }
}
