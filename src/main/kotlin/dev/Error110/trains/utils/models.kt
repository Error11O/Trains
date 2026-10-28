package dev.Error110.trains.utils

import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace

fun countRoofDepth(block: Block): Int {
    var depth = 0
    var above = block.getRelative(BlockFace.UP)
    while (depth < 256 && above.type.isSolid) {
        depth++
        above = above.getRelative(BlockFace.UP)
    }
    return depth
}

data class Station(
    var id: Int = 0,
    val location: Location,
    var banned: Boolean = false
)

data class RailEdge(
    var id: Int = 0,
    val stationA: Int,
    val stationB: Int?,
    val railStart: Location,
    val railEnd: Location,
    val distance: Int
)
