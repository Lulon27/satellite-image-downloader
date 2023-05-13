package tile

import util.MapUtil
import kotlin.math.floor
import kotlin.math.pow

class TileTree<T : Any>(private vararg val nodeSizes: Int)
{
    init
    {
        if(nodeSizes.isEmpty())
        {
            throw IllegalArgumentException("nodeSizes cannot be empty")
        }
    }

    inner class Node(size: Int, private var parent: Node?, private val indexInParent: Int)
    {
        private val children = arrayOfNulls<Any>(size)
        private var valueCounter: Int = 0

        init
        {
            println("Created node with size=$size")
        }

        fun getChild(index: Int): Any?
        {
            return children[index]
        }

        fun setChild(index: Int, value: Any?)
        {
            if(children[index] == null && value != null) valueCounter++
            else if(children[index] != null && value == null) valueCounter--
            children[index] = value

            if(valueCounter == 0)
            {
                parent?.setChild(indexInParent, null)
                parent = null
            }
        }
    }

    private val root = Node(nodeSizes[0].toDouble().pow(2).toInt(), null, -1)

    private val remainingNodeSizesMultiplied = calculateRemainingNodeSizes(nodeSizes)

    private fun traverseTo(x: Long, y: Long, growTree: Boolean, onNodeReached: (node: Node, childIndex: Int) -> Unit)
    {
        var currentNode = root
        val path = getPath(x, y, nodeSizes, remainingNodeSizesMultiplied)
        for (i in 0 until path.size - 1)
        {
            if(currentNode.getChild(path[i]) == null)
            {
                if(growTree)
                {
                    currentNode.setChild(path[i], Node(nodeSizes[i + 1].toDouble().pow(2).toInt(), currentNode, path[i]))
                }
                else
                {
                    return
                }
            }
            currentNode = currentNode.getChild(path[i]) as TileTree<T>.Node
        }
        onNodeReached(currentNode, path.last())
    }

    fun putTile(x: Long, y: Long, tile: T?)
    {
        traverseTo(x, y, true) { node: Node, childIndex: Int ->
            node.setChild(childIndex, tile)
        }
    }

    fun getTile(x: Long, y: Long): T?
    {
        var ret: T? = null
        traverseTo(x, y, false) { node: Node, childIndex: Int ->
            ret = node.getChild(childIndex) as T?
        }
        return ret
    }

    fun removeTile(x: Long, y: Long)
    {
        putTile(x, y, null)
    }

    companion object
    {
        private fun calculateRemainingNodeSizes(nodeSizes: IntArray): LongArray
        {
            val remainingNodeSizesMultiplied = LongArray(nodeSizes.size)
            var value: Long = 1
            for (i in nodeSizes.indices)
            {
                remainingNodeSizesMultiplied[nodeSizes.size - i - 1] = value
                value *= nodeSizes[nodeSizes.size - i - 1].toDouble().toInt()
            }
            return remainingNodeSizesMultiplied
        }

        private fun getChildIndex(x: Long, level: Int, nodeSizes: IntArray, remainingNodeSizesMultiplied: LongArray): Int =
            (floor((x / remainingNodeSizesMultiplied[level]).toDouble()).toLong() % (nodeSizes[level])).toInt()

        fun getPath(x: Long, y: Long, nodeSizes: IntArray, remainingNodeSizesMultiplied: LongArray? = null): IntArray
        {
            var childIndexX = 0
            var childIndexY = 0
            var childIndexMapped = 0
            val rnm = remainingNodeSizesMultiplied ?: calculateRemainingNodeSizes(nodeSizes)
            val ret = IntArray(nodeSizes.size)

            for (i in nodeSizes.indices)
            {
                childIndexX = getChildIndex(x, i, nodeSizes, rnm)
                childIndexY = getChildIndex(y, i, nodeSizes, rnm)
                childIndexMapped = MapUtil.map2Dto1D(childIndexX.toLong(), childIndexY.toLong(), nodeSizes[i]).toInt()
                ret[i] = childIndexMapped
            }

            return ret
        }
    }
}