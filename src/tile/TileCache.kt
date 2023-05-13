package tile

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import util.MapUtil
import java.nio.file.Path
import kotlin.collections.ArrayList
import kotlin.io.path.*
import kotlin.math.pow

object TileCache
{
    enum class PersistentState
    {
        NOT_PERSISTENT,
        DOWNLOADING,
        PERSISTENT,
        FAILED,
        DELETING
    }

    private class TileCacheInfo
    {
        var tile: Tile? = null
        var timestamp: Long = 0

        var x: Long = 0
        var y: Long = 0
        var z: Int = 0

        var persistentState = PersistentState.NOT_PERSISTENT
    }

    private val tileFactory: TileFactory<TileJavaFXImage> = TileJavaFXFactory()

    /** Maximum amount of tiles stored in main memory. */
    private const val maxTiles = 500

    /** Percentage of tiles that are removed if the cache is full. */
    private const val cacheDeleteBatchPercentage = 0.2;

    private val itemsToMove: Int
        get() = (maxTiles * cacheDeleteBatchPercentage).toInt()

    private val tileTrees: ArrayList<TileTree<TileCacheInfo>> = ArrayList()

    private const val tileTreeMaxPower = 6

    private val cache: ArrayList<TileCacheInfo> = ArrayList(maxTiles)

    private val lock = Any()

    private fun getTileTree(zoomLevel: Int): TileTree<TileCacheInfo>
    {
        if(tileTrees.size <= zoomLevel)
        {
            tileTrees.ensureCapacity(zoomLevel + 1)
            for(z in tileTrees.size until zoomLevel + 1)
            {
                tileTrees.add(TileTree(*MapUtil.calculateTreeLayerSizes(z, 6).toIntArray()))
                println("Created TileTree for z=$z with values ${MapUtil.calculateTreeLayerSizes(z, 6)}")
            }
        }
        return tileTrees[zoomLevel]
    }

    fun clear()
    {

    }

    fun putTile(x: Long, y: Long, z: Int, tile: Tile) = synchronized(lock)
    {
        val ti = TileCacheInfo()
        ti.tile = tile
        ti.timestamp = System.currentTimeMillis()
        ti.x = x
        ti.y = y
        ti.z = z

        if(cache.size >= maxTiles)
        {
            cache.sortByDescending { it.timestamp }

            for(i in cache.size - 1 downTo cache.size - itemsToMove)
            {
                getTileTree(cache[i].z).removeTile(cache[i].x, cache[i].y)
                cache[i].tile = null
                cache.removeAt(i)
            }
        }

        cache.add(ti)

        getTileTree(z).putTile(x, y, ti)
    }

    fun getTile(x: Long, y: Long, z: Int): Tile? = synchronized(lock)
    {
        val tile = getTileTree(z).getTile(x, y) ?: return null
        tile.timestamp = System.currentTimeMillis()
        return tile.tile
    }

    fun setPersistentState(x: Long, y: Long, z: Int, state: PersistentState) = synchronized(lock)
    {
        var tile = getTileTree(z).getTile(x, y)
        if(tile == null)
        {
            if(state == PersistentState.NOT_PERSISTENT)
            {
                // If tile is not there, do nothing
                return
            }
            tile = TileCacheInfo()
            tile.tile = null
            tile.x = x
            tile.y = y
            tile.z = z
            getTileTree(z).putTile(x, y, tile)
        }
        if(state == PersistentState.NOT_PERSISTENT && tile.tile == null)
        {
            getTileTree(z).removeTile(x, y)
            return
        }
        tile.persistentState = state
    }

    fun getPersistentState(x: Long, y: Long, z: Int): PersistentState = synchronized(lock)
    {
        return getTileTree(z).getTile(x, y)?.persistentState?: PersistentState.NOT_PERSISTENT
    }
}
