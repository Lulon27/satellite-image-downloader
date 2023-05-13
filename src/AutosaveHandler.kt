import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import tile.PersistentTileDownloader
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

object AutosaveHandler
{
    private class AutosaveJob(val action: () -> Unit)
    {
        var job: Job? = null
        @Volatile
        var jobPrev: Job? = null
    }

    private const val CONFIG_FILE = "config.json"
    const val DELAY_CONFIG = 2000L
    const val DELAY_PERSISTENT = 10000L

    const val JOB_CONFIG = "config"
    const val JOB_PERSISTENT_INFO = "persistent_info"

    private val autosaveJobs = HashMap<String, AutosaveJob>()

    init
    {
        autosaveJobs[JOB_CONFIG] = AutosaveJob {
            println("Saving config...")
            var json = CollectionHandler.saveToJson()
            saveJson(buildJsonObject {
                put("collections", json)
            })
        }
        autosaveJobs[JOB_PERSISTENT_INFO] = AutosaveJob {
            println("Saving persistent info...")
            PersistentTileDownloader.savePersistentInfo()
        }
    }

    fun scheduleAutosave(name: String, delay: Long) = synchronized(autosaveJobs)
    {
        val aj = autosaveJobs[name] ?: throw IllegalArgumentException("$name is not a key in autosaveJobs")

        aj.jobPrev = aj.job
        aj.job = GlobalScope.launch {
            aj.jobPrev?.cancelAndJoin()
            delay(delay)
            aj.action()
        }
    }

    private fun saveJson(value: JsonElement)
    {
        try
        {
            val s = BufferedOutputStream(FileOutputStream(File(CONFIG_FILE)))
            Json.encodeToStream(value, s)
            s.flush()
        }
        catch (e: Exception)
        {
            println("Failed to save config.json: $e")
        }
    }

    private fun loadJson(): JsonObject?
    {
        try {
            val s = BufferedInputStream(FileInputStream(File(CONFIG_FILE)))
            return Json.decodeFromStream(s)
        }
        catch (e: Exception)
        {
            println("Failed to save config.json: $e")
        }
        return null
    }

    private fun restoreSettings(json: JsonObject)
    {
        json["collections"]?.jsonArray?.run { CollectionHandler.restoreFromJson(this) }
    }

    fun restoreSettings()
    {
        loadJson()?.run { restoreSettings(this) }
    }

    fun join() = synchronized(autosaveJobs)
    {
        autosaveJobs.forEach { (_, job) ->
            runBlocking { job.job?.join() }
        }
    }
}
