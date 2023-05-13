package tile

import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.awt.image.BufferedImage
import java.awt.image.ColorConvertOp
import java.io.InputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories

class TileJavaFXImage : Tile
{
    override var tileImage: Image? = null
        private set

    override fun readTile(stream: InputStream)
    {
        this.tileImage = Image(stream)
        if(tileImage!!.isError)
        {
            val e = tileImage!!.exception
            tileImage = null
            throw e
        }
    }

    override fun writeTile(filePath: Path)
    {
        filePath.subpath(0, filePath.count() - 1).createDirectories()
        println("Write $filePath (${SwingFXUtils.fromFXImage(tileImage, null)}, ${tileImage?.exception})")
        val bi = SwingFXUtils.fromFXImage(tileImage, null)
        val rgbImg = ColorConvertOp(null).filter(bi, BufferedImage(bi.width, bi.height, BufferedImage.TYPE_INT_RGB))
        val ret = ImageIO.write(rgbImg, "JPEG", filePath.toFile())
        if(!ret)
        {
            println("ImageIO.write returned false")
        }
    }

    override fun readTile(filePath: Path)
    {
        tileImage = SwingFXUtils.toFXImage(ImageIO.read(filePath.toFile()), null)
    }
}
