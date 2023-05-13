package tile

abstract class TileFactory<T : Tile>
{
    abstract fun createTile(): T
}
