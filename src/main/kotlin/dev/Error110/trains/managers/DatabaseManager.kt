package dev.Error110.trains.managers

import dev.Error110.trains.Trains
import dev.Error110.trains.utils.RailEdge
import dev.Error110.trains.utils.Station
import org.bukkit.Bukkit
import org.bukkit.Location
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

data class StationSnapshot(val id: Int, val world: String, val x: Int, val y: Int, val z: Int, val banned: Boolean)
data class EdgeSnapshot(
    val id: Int, val stationA: Int, val stationB: Int?,
    val startWorld: String, val startX: Int, val startY: Int, val startZ: Int,
    val endWorld: String, val endX: Int, val endY: Int, val endZ: Int, val distance: Int
)

class DatabaseManager(private val plugin: Trains) {

    private lateinit var connection: Connection
    private val dbFile = File(plugin.dataFolder, "trains.db")

    init {
        try {
            setupDatabase()
        } catch (e: Exception) {
            try { close() } catch (closeError: Exception) { e.addSuppressed(closeError) }
            throw e
        }
    }

    private fun setupDatabase() {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }

        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

        connection.createStatement().use { stmt ->
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS stations (
                    id INTEGER PRIMARY KEY,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    banned INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(world, x, y, z)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS edges (
                    id INTEGER PRIMARY KEY,
                    station_a INTEGER NOT NULL,
                    station_b INTEGER,
                    rail_start_world TEXT NOT NULL,
                    rail_start_x INTEGER NOT NULL,
                    rail_start_y INTEGER NOT NULL,
                    rail_start_z INTEGER NOT NULL,
                    rail_end_world TEXT NOT NULL,
                    rail_end_x INTEGER NOT NULL,
                    rail_end_y INTEGER NOT NULL,
                    rail_end_z INTEGER NOT NULL,
                    distance INTEGER NOT NULL,
                    FOREIGN KEY(station_a) REFERENCES stations(id) ON DELETE CASCADE,
                    FOREIGN KEY(station_b) REFERENCES stations(id) ON DELETE CASCADE,
                    UNIQUE(rail_start_world, rail_start_x, rail_start_y, rail_start_z)
                )
            """.trimIndent())

            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_edges_station_a ON edges(station_a)")
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_edges_station_b ON edges(station_b)")
        }
    }

    fun saveAll(stations: List<StationSnapshot>, edges: List<EdgeSnapshot>, deletedStationIds: List<Int>) {
        connection.autoCommit = false
        try {
            deletedStationIds.forEach { id ->
                connection.prepareStatement("DELETE FROM edges WHERE station_a = ? OR station_b = ?").use { stmt ->
                    stmt.setInt(1, id)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
                connection.prepareStatement("DELETE FROM stations WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    stmt.executeUpdate()
                }
            }

            val stationSql = """
                INSERT OR REPLACE INTO stations (id, world, x, y, z, banned)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            connection.prepareStatement(stationSql).use { stmt ->
                stations.forEach { station ->
                    stmt.setInt(1, station.id)
                    stmt.setString(2, station.world)
                    stmt.setInt(3, station.x)
                    stmt.setInt(4, station.y)
                    stmt.setInt(5, station.z)
                    stmt.setInt(6, if (station.banned) 1 else 0)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            val edgeSql = """
                INSERT OR REPLACE INTO edges (id, station_a, station_b, rail_start_world, rail_start_x, rail_start_y, rail_start_z,
                                             rail_end_world, rail_end_x, rail_end_y, rail_end_z, distance)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            connection.prepareStatement(edgeSql).use { stmt ->
                edges.forEach { edge ->
                    stmt.setInt(1, edge.id)
                    stmt.setInt(2, edge.stationA)
                    stmt.setObject(3, edge.stationB)
                    stmt.setString(4, edge.startWorld)
                    stmt.setInt(5, edge.startX)
                    stmt.setInt(6, edge.startY)
                    stmt.setInt(7, edge.startZ)
                    stmt.setString(8, edge.endWorld)
                    stmt.setInt(9, edge.endX)
                    stmt.setInt(10, edge.endY)
                    stmt.setInt(11, edge.endZ)
                    stmt.setInt(12, edge.distance)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    fun loadAllStations(): List<Station> {
        val stations = mutableListOf<Station>()
        val sql = "SELECT * FROM stations"
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            while (rs.next()) {
                val world = Bukkit.getWorld(rs.getString("world")) ?: continue
                stations.add(
                    Station(
                        id = rs.getInt("id"),
                        location = Location(
                            world,
                            rs.getInt("x").toDouble(),
                            rs.getInt("y").toDouble(),
                            rs.getInt("z").toDouble()
                        ),
                        banned = rs.getInt("banned") == 1
                    )
                )
            }
        }
        return stations
    }

    fun loadAllEdges(): List<RailEdge> {
        val edges = mutableListOf<RailEdge>()
        val sql = "SELECT * FROM edges"
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            while (rs.next()) {
                val startWorld = Bukkit.getWorld(rs.getString("rail_start_world")) ?: continue
                val endWorld = Bukkit.getWorld(rs.getString("rail_end_world")) ?: continue
                edges.add(
                    RailEdge(
                        id = rs.getInt("id"),
                        stationA = rs.getInt("station_a"),
                        stationB = rs.getObject("station_b") as? Int,
                        railStart = Location(
                            startWorld,
                            rs.getInt("rail_start_x").toDouble(),
                            rs.getInt("rail_start_y").toDouble(),
                            rs.getInt("rail_start_z").toDouble()
                        ),
                        railEnd = Location(
                            endWorld,
                            rs.getInt("rail_end_x").toDouble(),
                            rs.getInt("rail_end_y").toDouble(),
                            rs.getInt("rail_end_z").toDouble()
                        ),
                        distance = rs.getInt("distance")
                    )
                )
            }
        }
        return edges
    }

    fun close() {
        if (::connection.isInitialized && !connection.isClosed) {
            connection.close()
        }
    }
}