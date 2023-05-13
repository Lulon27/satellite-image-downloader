package tile

class TileJavaFXFactory : TileFactory<TileJavaFXImage>()
{
    override fun createTile(): TileJavaFXImage
    {
        return TileJavaFXImage()
    }
}
