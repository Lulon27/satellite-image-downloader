package tile

import CollectionHandler
import util.MapUtil
import util.Point
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import util.NetUtil
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

object TileDownloader
{
    private val tileFactory = TileJavaFXFactory()
    private var min = Point()
    private var max = Point()
    private val minPrev = Point()
    private val maxPrev = Point()
    private var z = 0
    private var zPrev = 0
    private val current = Point()
    @Volatile
    private var threadRunning = false
    private val mutex = Mutex()
    private var job: Job? = null
    private var deferred = CompletableDeferred<Unit>()
    @Volatile
    private var downloadURL: String? = null
    private val lock = Any()
    val requestPause = AtomicInteger(500)

    fun start()
    {
        if(threadRunning)
        {
            throw IllegalStateException("Thread is already running")
        }
        threadRunning = true;
        job = GlobalScope.launch(Dispatchers.Default) { run() }
    }

    fun stopAndJoin() = runBlocking {
        if(!threadRunning)
        {
            throw IllegalStateException("Thread is already stopped")
        }
        threadRunning = false
        deferred.complete(Unit)
        job?.cancelAndJoin()
    }

    private fun run() = runBlocking {
        while(threadRunning)
        {
            delay(requestPause.get().toLong())
            if(!threadRunning)
            {
                break
            }
            if(!downloadNextTile())
            {
                deferred.await()
                deferred = CompletableDeferred()
            }
        }
    }

    fun setRequestedArea(x1: Long, y1: Long, x2: Long, y2: Long, z: Int) = synchronized(lock)
    {
        min = Point(maxOf(minOf(x1, x2), 0), maxOf(minOf(y1, y2), 0))
        max = Point(minOf(maxOf(x1, x2), MapUtil.getNumTilesOnZ(z) - 1), minOf(maxOf(y1, y2), MapUtil.getNumTilesOnZ(z) - 1))
        TileDownloader.z = z
        if (z != zPrev || min.x != minPrev.x || min.y != minPrev.y || max.x != maxPrev.x || max.y != maxPrev.y)
        {
            current.x = min.x
            current.y = min.y
        }
        minPrev.x = min.x
        minPrev.y = min.y
        maxPrev.x = max.x
        maxPrev.y = max.y
        zPrev = z
        deferred.complete(Unit)
    }

    fun setDownloadURL(url: String?) = synchronized(lock)
    {
        downloadURL = url
        deferred.complete(Unit)
    }

    private fun downloadNextTile(): Boolean = synchronized(lock) {
        if(downloadURL == null)
        {
            return false
        }

        var downloadedTile = false
        val maxTiles = MapUtil.getNumTilesOnZ(z)
        while (!(current.x > max.x || current.y > max.y) && !downloadedTile)
        {
            val downloadX = (current.x % maxTiles + maxTiles) % maxTiles
            val downloadY = (current.y % maxTiles + maxTiles) % maxTiles
            if (TileCache.getTile(downloadX, downloadY, z) == null)
            {
                GlobalScope.launch {
                    val newTile = tileFactory.createTile()
                    var error: Throwable? = null

                    NetUtil.downloadTileImage(URL(CollectionHandler.formatURL(downloadURL!!, downloadX, downloadY, z)), {
                        newTile.readTile(it)
                    }, {
                        error = it
                    })

                    if(error == null && newTile.tileImage != null)
                    {
                        println("Downloaded tile ($downloadX | $downloadY)")
                        TileCache.putTile(downloadX, downloadY, z, newTile)
                    }
                    else
                    {
                        println("Failed to download tile ($error)")
                    }
                }
                downloadedTile = true
            }
            else
            {
                println("Tile ($downloadX | $downloadY) is already in cache")
            }
            current.x++
            if (current.x > max.x)
            {
                current.x = min.x
                current.y++
            }
        }
        return downloadedTile
    }
}
