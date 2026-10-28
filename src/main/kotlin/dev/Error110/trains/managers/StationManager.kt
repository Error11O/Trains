package dev.Error110.trains.managers

import dev.Error110.trains.Trains
import dev.Error110.trains.utils.RailEdge
import dev.Error110.trains.utils.Station
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ExecutionException
import java.util.logging.Level

class StationManager(private val plugin: Trains) {

    private val stations = mutableMapOf<Int, Station>()
    private val stationsByLocation = mutableMapOf<Location, Int>()
    private val edges = mutableMapOf<Int, RailEdge>()
    private val edgesByRailStart = mutableMapOf<Location, Int>()
    private var nextStationId = 1
    private var nextEdgeId = 1
    private var scanTask: BukkitTask? = null
    private var saveTask: BukkitTask? = null

    private val saveExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Trains database writer")
    }
    private data class SaveBatch(
        val stations: List<StationSnapshot>,
        val edges: List<EdgeSnapshot>,
        val deletedStations: List<Int>
    )
    private var pendingSave: Pair<SaveBatch, Future<*>>? = null
    private var stopped = false

    private val dirtyStations = mutableSetOf<Int>()
    private val dirtyEdges = mutableSetOf<Int>()
    private val deletedStations = mutableSetOf<Int>()

    init {
        loadFromDatabase()
    }

    fun start() {
        startAutoScan()
        startAutoSave()
    }

    private fun loadFromDatabase() {
        val loadedStations = plugin.databaseManager.loadAllStations()
        for (station in loadedStations) {
            stations[station.id] = station
            stationsByLocation[station.location] = station.id
            if (station.id >= nextStationId) {
                nextStationId = station.id + 1
            }}

        val loadedEdges = plugin.databaseManager.loadAllEdges()
        for (edge in loadedEdges) {
            edges[edge.id] = edge
            edgesByRailStart[edge.railStart] = edge.id
            if (edge.id >= nextEdgeId) {
                nextEdgeId = edge.id + 1
            }
        }
    }

    fun createStation(location: Location): Pair<Int, String?> {
        val existing = stationsByLocation[location]
        if (existing != null) {
            return Pair(existing, "Station already exists at this location")
        }

        if (location.block.type != Material.GOLD_BLOCK) {
            return Pair(0, "Location must be a gold block")
        }

        val station = Station(id = nextStationId++, location = location)
        stations[station.id] = station
        stationsByLocation[location] = station.id
        dirtyStations.add(station.id)

        return Pair(station.id, null)
    }

    fun scanAndCreateEdgesForStation(stationId: Int): Int {
        val station = stations[stationId] ?: return 0
        val faces = listOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
        var edgesCreated = 0

        for (face in faces) {
            val adjacent = station.location.block.getRelative(face)
            if (adjacent.type == Material.RAIL) {
                createOrUpdateEdge(stationId, adjacent.location, face)
                edgesCreated++
            }
        }
        return edgesCreated
    }

    private fun createOrUpdateEdge(stationId: Int, railStart: Location, direction: BlockFace) {
        val (endLocation, distance, endStationId) = traceRailInDirection(railStart, direction)

        val existingEdgeId = edgesByRailStart[railStart]

        val edge = if (existingEdgeId != null) {
            val existing = edges[existingEdgeId]!!
            existing.copy(
                stationA = stationId,
                stationB = endStationId,
                railEnd = endLocation,
                distance = distance
            ).also {
                it.id = existing.id
            }
        } else {
            RailEdge(
                id = nextEdgeId++,
                stationA = stationId,
                stationB = endStationId,
                railStart = railStart,
                railEnd = endLocation,
                distance = distance
            )
        }

        edges[edge.id] = edge
        edgesByRailStart[railStart] = edge.id
        dirtyEdges.add(edge.id)
    }

    fun getStation(id: Int): Station? = stations[id]

    fun getStationByLocation(location: Location): Station? {
        val id = stationsByLocation[location] ?: return null
        return stations[id]
    }

    fun getAllStations(): List<Station> = stations.values.toList()

    fun getEdgesFromStation(stationId: Int): List<RailEdge> {
        return edges.values.filter { it.stationA == stationId || it.stationB == stationId }
    }

    fun findEdgeByRail(railLocation: Location): RailEdge? {
        val id = edgesByRailStart[railLocation] ?: return null
        return edges[id]
    }

    fun updateStation(station: Station) {
        stations[station.id] = station
        dirtyStations.add(station.id)
    }

    fun removeStation(id: Int) {
        val station = stations[id] ?: return
        stations.remove(id)
        stationsByLocation.remove(station.location)
        dirtyStations.remove(id)
        deletedStations.add(id)

        val edgesToRemove = edges.values.filter { it.stationA == id || it.stationB == id }
        edgesToRemove.forEach { edge ->
            edges.remove(edge.id)
            edgesByRailStart.remove(edge.railStart)
            dirtyEdges.remove(edge.id)
        }}

    private fun traceRailInDirection(start: Location, initialDirection: BlockFace): Triple<Location, Int, Int?> {
        var current = start.block
        var distance = 0
        val visited = mutableSetOf<Location>()
        var lastDirection = initialDirection

        while (distance < 10000) {
            if (!visited.add(current.location)) break

            if (current.type == Material.RAIL) {
                distance++

                val possibleDirections = getPossibleDirections(lastDirection)
                var foundNext = false

                for (direction in possibleDirections) {
                    for (yOffset in listOf(0, 1, -1)) {
                        val next = current.getRelative(direction).getRelative(BlockFace.UP, yOffset)

                        if (next.type == Material.GOLD_BLOCK) {
                            val stationId = stationsByLocation[next.location]
                            return Triple(next.location, distance, stationId)
                        }

                        if (next.type == Material.RAIL && !visited.contains(next.location)) {
                            current = next
                            lastDirection = direction
                            foundNext = true
                            break
                        }
                    }
                    if (foundNext) break
                }

                if (!foundNext) {
                    return Triple(current.location, distance, null)
                }
            } else {
                break
            }
        }
        return Triple(current.location, distance, null)
    }

    private fun getPossibleDirections(currentDirection: BlockFace): List<BlockFace> {
        return when (currentDirection) {
            BlockFace.NORTH -> listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST)
            BlockFace.SOUTH -> listOf(BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
            BlockFace.EAST -> listOf(BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH)
            BlockFace.WEST -> listOf(BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH)
            else -> listOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
        }
    }

    private fun startAutoScan() {
        scanTask = object : BukkitRunnable() {
            override fun run() {
                stations.values.forEach { station ->
                    scanAndCreateEdgesForStation(station.id)
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60)
    }

    private fun startAutoSave() {
        val saveInterval = plugin.config.getLong("auto-save-interval", 300) * 20
        saveTask = object : BukkitRunnable() {
            override fun run() {
                startSave()
            }
        }.runTaskTimer(plugin, saveInterval, saveInterval)
    }

    // All state and snapshots are owned by the server thread. The worker only sees values.
    private fun snapshot(): SaveBatch? {
        if (dirtyStations.isEmpty() && dirtyEdges.isEmpty() && deletedStations.isEmpty()) return null
        val batch = SaveBatch(
            dirtyStations.mapNotNull { stations[it] }.map { station ->
                val loc = station.location
                StationSnapshot(station.id, loc.world!!.name, loc.blockX, loc.blockY, loc.blockZ, station.banned)
            },
            dirtyEdges.mapNotNull { edges[it] }.map { edge ->
                val start = edge.railStart
                val end = edge.railEnd
                EdgeSnapshot(edge.id, edge.stationA, edge.stationB,
                    start.world!!.name, start.blockX, start.blockY, start.blockZ,
                    end.world!!.name, end.blockX, end.blockY, end.blockZ, edge.distance)
            },
            deletedStations.toList()
        )
        dirtyStations.clear()
        dirtyEdges.clear()
        deletedStations.clear()
        return batch
    }

    private fun restore(batch: SaveBatch) {
        // Resolve against current state so a later update or deletion wins over an old snapshot.
        dirtyStations.addAll(batch.stations.map { it.id }.filter { it in stations })
        dirtyEdges.addAll(batch.edges.map { it.id }.filter { it in edges })
        deletedStations.addAll(batch.deletedStations)
    }

    private fun write(batch: SaveBatch) {
        plugin.databaseManager.saveAll(batch.stations, batch.edges, batch.deletedStations)
    }

    private fun finishSave(wait: Boolean) {
        val (batch, future) = pendingSave ?: return
        if (!wait && !future.isDone) return
        var interrupted = false
        try {
            while (true) {
                try {
                    future.get()
                    break
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        } catch (e: ExecutionException) {
            restore(batch)
            plugin.logger.log(Level.SEVERE, "Failed to save train data; changes will be retried", e.cause ?: e)
        } finally {
            pendingSave = null
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun startSave() {
        if (stopped) return
        finishSave(wait = false)
        if (pendingSave != null) return
        val batch = snapshot() ?: return
        try {
            pendingSave = batch to saveExecutor.submit { write(batch) }
        } catch (e: Exception) {
            restore(batch)
            plugin.logger.log(Level.SEVERE, "Failed to start train data save", e)
        }
    }

    fun shutdown() {
        if (stopped) return
        stopped = true
        scanTask?.cancel()
        saveTask?.cancel()
        finishSave(wait = true)
        saveExecutor.shutdown()
        val batch = snapshot() ?: return
        try {
            write(batch)
        } catch (e: Exception) {
            restore(batch)
            plugin.logger.log(Level.SEVERE, "Failed to save train data during shutdown", e)
        }
    }
}
