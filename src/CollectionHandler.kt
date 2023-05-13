import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList

object CollectionHandler
{
    class Collection(val id: Int, @Volatile var name: String = "", @Volatile var url: String = "", @Volatile var tileSize: AtomicInteger = AtomicInteger(256), @Volatile var downloadFolder: String = "")
    {
        override fun toString(): String
        {
            return name
        }
    }

    private val collections = ArrayList<Collection>()

    private val eventListeners = ArrayList<() -> Unit>()

    private val lock = Any()
    private val lockListeners = Any()

    init
    {
        newCollection("Google Maps Satellite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}")
    }

    fun newCollection(name: String = "", url: String = "", tileSize: Int = 256) = runBlocking {
        synchronized(lock) {
            var id = 0
            while(collections.any { it.id == id })
            {
                ++id;
            }
            collections.add(Collection(id, name, url, AtomicInteger(tileSize)))
        }
        synchronized(lockListeners) { eventListeners.forEach{ it.invoke() } }
    }

    fun removeCollection(collection: Collection) = runBlocking {
        synchronized(lock) { collections.remove(collection) }
        synchronized(lockListeners) { eventListeners.forEach{ it.invoke() } }
    }

    fun <T> getCollections(action: (ArrayList<Collection>) -> T) = synchronized(lock)
    {
        action(collections)
    }

    fun addOnCollectionsChanged(event: () -> Unit) = synchronized(lockListeners)
    {
        eventListeners.add(event)
    }

    fun formatURL(url: String, x: Long, y: Long, z: Int): String
    {
        return formatURL(url, x.toString(), y.toString(), z.toString())
    }

    fun formatURL(url: String, x: String, y: String, z: String): String
    {
        return url.replace("{x}", x).replace("{y}", y).replace("{z}", z)
    }

    fun saveToJson() = buildJsonArray {
        getCollections { collections ->
            collections.forEach { c ->
                addJsonObject {
                    put("id", c.id)
                    put("name", c.name)
                    put("url", c.url)
                    put("tileSize", c.tileSize.get())
                    put("downloadFolder", c.downloadFolder)
                }
            }
        }
    }

    fun restoreFromJson(json: JsonArray)
    {
        synchronized(lock)
        {
            collections.clear()
            json.forEach {
                val obj = it.jsonObject
                val id = obj["id"]?.jsonPrimitive?.int
                if(id != null)
                {
                    collections.add(Collection(id,
                        obj["name"]?.jsonPrimitive?.content?: "",
                        obj["url"]?.jsonPrimitive?.content?: "",
                        AtomicInteger(obj["tileSize"]?.jsonPrimitive?.int ?: 256),
                        obj["downloadFolder"]?.jsonPrimitive?.content ?: ""))
                }
            }
        }
        synchronized(lockListeners) { eventListeners.forEach{ it.invoke() } }
    }
}
