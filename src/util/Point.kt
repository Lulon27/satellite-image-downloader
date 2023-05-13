package util

import java.util.*

class Point(var x: Long = 0, var y: Long = 0)
{
    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val point = other as Point
        return x == point.x && y == point.y
    }

    override fun hashCode(): Int
    {
        return Objects.hash(x, y)
    }
}
