package tile

import AutosaveHandler
import CollectionHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import util.MapUtil
import util.NetUtil
import java.awt.image.BufferedImage
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO

object PersistentTileDownloader
{
    private data class PersistentTile(val coordinate: TileCoordinate, val timestamp: Long)

    enum class CheckFolderResult(val msg: String = "")
    {
        DOES_NOT_EXIST("The folder does not exist"),
        NOT_A_DIRECTORY("Must be a directory"),
        NOT_SUITABLE("persistent.dat does not exist and folder is not empty"),
        EMPTY(),
        OK()
    }

    var downloadFolder = ""
        private set

    @Volatile
    var downloadURL: String? = null

    private val lock = Any()
    private val lockLoadListeners = Any()

    private val loadListeners = ArrayList<(ex: Exception?) -> Unit>()

    private val persistentTiles = ArrayList<PersistentTile>()

    private val lastDownloadTime = AtomicLong(0)

    private val downloaderScope = CoroutineScope(Dispatchers.IO)

    val requestPause = AtomicInteger(500)

    fun addLoadListener(callback: (ex: Exception?) -> Unit) = synchronized(lockLoadListeners)
    {
        loadListeners.add(callback)
    }

    private fun firePostPersistentInfoLoaded(ex: Exception?) = synchronized(lockLoadListeners)
    {
        loadListeners.forEach { it(ex) }
    }

    /**
     * Sets the download folder. The download folder is the folder where tiles that the user
     * selects are downloaded to.
     * Reloads the persistent.dat file if found.
     * @param downloadFolder path to the new download folder
     * @throws IllegalArgumentException if the folder does not exist
     * @throws IllegalArgumentException if the path does not point to a folder
     * @throws IOException if the persistent.dat file was found but could not be read
     */
    fun setDownloadFolder(downloadFolder: String): Unit = synchronized(lock)
    {
        val result = fastCheckFolder(File(downloadFolder))
        if(result == CheckFolderResult.EMPTY || result == CheckFolderResult.OK)
        {
            this.downloadFolder = downloadFolder
            return
        }

        throw IllegalStateException(result.msg)
    }

    fun fastCheckFolder(folder: File): CheckFolderResult
    {
        if(!folder.exists())
        {
            return CheckFolderResult.DOES_NOT_EXIST
        }
        if(!folder.isDirectory)
        {
            return CheckFolderResult.NOT_A_DIRECTORY
        }
        val file = Paths.get(folder.path, "persistent.dat").toFile()
        if(folder.list()!!.isEmpty())
        {
            // If persistent.dat is not there then this is an empty folder
            // that the user has not used yet which is OK
            return CheckFolderResult.EMPTY
        }
        else if(!file.exists())
        {
            return CheckFolderResult.NOT_SUITABLE
        }
        return CheckFolderResult.OK
    }

    fun loadPersistentInfo(onSuccess: (() -> Unit) = {}) = synchronized(persistentTiles)
    {
        GlobalScope.launch {
            loadPersistentInfo_()
            onSuccess()
        }
    }

    private fun loadPersistentInfo_() = synchronized(persistentTiles)
    {
        val path = Paths.get(downloadFolder, "persistent.dat")

        // Clear all persistent state from the tile tree too
        for (t in persistentTiles)
        {
            TileCache.setPersistentState(t.coordinate.x, t.coordinate.y, t.coordinate.z, TileCache.PersistentState.NOT_PERSISTENT)
        }
        persistentTiles.clear()

        DataInputStream(FileInputStream(path.toFile())).use { input ->
            val magicStr = String(input.readNBytes(8))
            if(magicStr != "LULONSID")
            {
                throw IllegalStateException("Invalid magic in persistent.dat")
            }
            input.readInt() // File version (maybe used in the future?)
            val size = input.readInt()

            for(i in 0 until size)
            {
                val x = input.readLong()
                val y = input.readLong()
                val z = input.readInt()
                val timestamp = input.readLong()
                persistentTiles.add(PersistentTile(TileCoordinate(x, y, z), timestamp))
                TileCache.setPersistentState(x, y, z, TileCache.PersistentState.PERSISTENT)
            }
        }
    }

    fun downloadTile(x: Long, y: Long, z: Int): Unit = synchronized(lock)
    {
        if(downloadURL == null)
        {
            throw IllegalStateException("downloadURL is null")
        }
        val state = TileCache.getPersistentState(x, y, z)
        if(!(state == TileCache.PersistentState.NOT_PERSISTENT || state == TileCache.PersistentState.FAILED))
        {
            return
        }

        TileCache.setPersistentState(x, y, z, TileCache.PersistentState.DOWNLOADING)



        downloaderScope.launch {
            synchronized(lastDownloadTime)
            {
                val timePassed = System.currentTimeMillis() - lastDownloadTime.get()
                val timeLeft = requestPause.get() - timePassed
                runBlocking { delay(timeLeft) }
                if(isActive)
                {
                    lastDownloadTime.set(System.currentTimeMillis())
                    downloadTile_(x, y, z)
                }
                else
                {
                    // If the download is cancelled
                    TileCache.setPersistentState(x, y, z, TileCache.PersistentState.NOT_PERSISTENT)
                }
            }
        }
    }

    fun cancelDownloads()
    {
        downloaderScope.coroutineContext.cancelChildren()
    }

    private fun calculateTileFilePath(x: Long, y: Long, z: Int): Path
    {
        val layerSizes = MapUtil.calculateTreeLayerSizes(z, 5) // 5 => 256 files per folder
        val tileSpecificPath = TileTree.getPath(x, y, layerSizes.toIntArray()).joinToString(File.separator, limit = layerSizes.size - 1, truncated = "")
        return Paths.get(downloadFolder, "$z", tileSpecificPath,"${x}_${y}.jpg")
    }

    private fun downloadTile_(x: Long, y: Long, z: Int)
    {
        val urlStr = CollectionHandler.formatURL(downloadURL!!, x, y, z)

        var bufferedImage: BufferedImage? = null

        NetUtil.downloadTileImage(URL(urlStr), {
            bufferedImage = ImageIO.read(it)
        }, {
            TileCache.setPersistentState(x, y, z, TileCache.PersistentState.FAILED)
            return@downloadTileImage
        })

        if(bufferedImage == null)
        {
            // No reader available for this image
            TileCache.setPersistentState(x, y, z, TileCache.PersistentState.FAILED)
            return
        }

        val filePath = calculateTileFilePath(x, y, z).toFile()
        File(filePath.parent).mkdirs()
        val writerFound: Boolean
        try
        {
            writerFound = ImageIO.write(bufferedImage, "JPG", filePath)
        }
        catch(e: IOException)
        {
            // Failed to save image
            TileCache.setPersistentState(x, y, z, TileCache.PersistentState.FAILED)
            return
        }
        if(!writerFound)
        {
            // Could not save the image because no writer was found
            TileCache.setPersistentState(x, y, z, TileCache.PersistentState.FAILED)
            return
        }
        synchronized(persistentTiles)
        {
            persistentTiles.add(PersistentTile(TileCoordinate(x, y, z), System.currentTimeMillis()))
        }
        TileCache.setPersistentState(x, y, z, TileCache.PersistentState.PERSISTENT)
        AutosaveHandler.scheduleAutosave(AutosaveHandler.JOB_PERSISTENT_INFO, AutosaveHandler.DELAY_PERSISTENT)
    }

    fun savePersistentInfo() = synchronized(persistentTiles)
    {
        val path = Paths.get(downloadFolder, "persistent.dat")

        try
        {
            DataOutputStream(FileOutputStream(path.toFile())).use { output ->
                output.writeBytes("LULONSID") // Magic
                output.writeInt(0) // File version (maybe used in the future?)
                output.writeInt(persistentTiles.size)
                for(t in persistentTiles)
                {
                    output.writeLong(t.coordinate.x)
                    output.writeLong(t.coordinate.y)
                    output.writeInt(t.coordinate.z)
                    output.writeLong(t.timestamp)
                }
            }
        }
        catch(e: IOException)
        {
            println("Failed to write cache file: $e")
        }
    }
}