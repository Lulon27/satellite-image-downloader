package tile

import java.io.InputStream
import java.nio.file.Path

interface Tile
{
    val tileImage: Any?
    fun readTile(stream: InputStream)
    fun readTile(filePath: Path)
    fun writeTile(filePath: Path)
}
