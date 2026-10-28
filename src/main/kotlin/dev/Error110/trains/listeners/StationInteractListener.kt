package dev.Error110.trains.listeners

import dev.Error110.trains.Trains
import dev.Error110.trains.utils.countRoofDepth
import dev.Error110.trains.utils.progressBar10
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class StationInteractListener(private val plugin: Trains) : Listener {

    private val mm = MiniMessage.miniMessage()
    private val activeTasks = HashMap<UUID, BukkitTask>()


    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (activeTasks.containsKey(event.player.uniqueId)) {
            val from = event.from
            val to = event.to
            if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
                activeTasks[event.player.uniqueId]?.cancel()
                activeTasks.remove(event.player.uniqueId)
                event.player.sendMessage(mm.deserialize("<red>Train journey cancelled due to movement!"))
            }
        }
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (event.cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return
        }

        if (activeTasks.containsKey(event.player.uniqueId)) {
            activeTasks[event.player.uniqueId]?.cancel()
            activeTasks.remove(event.player.uniqueId)
            event.player.sendMessage(mm.deserialize("<red>Train journey cancelled due to teleportation!"))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.isCancelled) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.RAIL) return
        if (event.hand != EquipmentSlot.HAND) return

        if (activeTasks.containsKey(event.player.uniqueId)) {
            activeTasks[event.player.uniqueId]?.cancel()
            activeTasks.remove(event.player.uniqueId)
            event.player.sendMessage(mm.deserialize("<green>Rescheduled to new train!"))
        }

        val edge = plugin.stationManager.findEdgeByRail(block.location) ?: return
        val sourceStation = plugin.stationManager.getStation(edge.stationA) ?: return

        if (sourceStation.banned) {
            event.player.sendMessage(mm.deserialize("<red>This station is banned!"))
            return
        }

        if (countRoofDepth(event.player.location.block) > 4) {
            event.player.sendMessage(mm.deserialize("<red>You are too far underground to board a train!"))
            return
        }

        val destination = if (edge.stationB != null) {
            val destStation = plugin.stationManager.getStation(edge.stationB!!)
            if (destStation?.banned == true) {
                event.player.sendMessage(mm.deserialize("<red>Destination station is banned!"))
                return
            }
            destStation?.location ?: edge.railEnd
        } else {
            edge.railEnd
        }

        val blocksPerSecond = plugin.config.getDouble("blocks-per-second", 40.0)
        val travelTime = (edge.distance / blocksPerSecond * 20).toLong()
        val updateInterval = 2L

        var elapsed = 0L

        val task = object : BukkitRunnable() {
            override fun run() {
                if (elapsed >= travelTime) {
                    val targetLoc = destination.clone().add(0.5, 1.0, 0.5)
                    targetLoc.yaw = event.player.location.yaw
                    targetLoc.pitch = event.player.location.pitch
                    activeTasks.remove(event.player.uniqueId)
                    event.player.teleport(targetLoc)
                    event.player.sendMessage(mm.deserialize("<green>Arrived at destination!"))
                    cancel()
                    return
                }

                val progress = elapsed.toDouble() / travelTime.toDouble()
                val progressBar = progressBar10(progress)
                val speedText = String.format("%.1f", blocksPerSecond)

                val actionBarText = mm.deserialize("$progressBar <yellow>${speedText} blocks/s")
                event.player.sendActionBar(actionBarText)
                elapsed += updateInterval
            }
        }
        activeTasks[event.player.uniqueId] = task.runTaskTimer(plugin, 0L, updateInterval)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.GOLD_BLOCK) return

        val station = plugin.stationManager.getStationByLocation(event.block.location)
        if (station != null) {
            plugin.stationManager.removeStation(station.id)
            event.player.sendMessage(mm.deserialize("<yellow>Station <gold>${station.id} <yellow>has been removed!"))
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().filter { it.type == Material.GOLD_BLOCK }.forEach { block ->
            val station = plugin.stationManager.getStationByLocation(block.location)
            if (station != null) {
                plugin.stationManager.removeStation(station.id)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().filter { it.type == Material.GOLD_BLOCK }.forEach { block ->
            val station = plugin.stationManager.getStationByLocation(block.location)
            if (station != null) {
                plugin.stationManager.removeStation(station.id)
            }
        }
    }

    @EventHandler
    fun onGoldBlockPlace(event: BlockPlaceEvent) {
        if (event.block.type == Material.GOLD_BLOCK) {
            event.player.sendMessage(mm.deserialize("<gold>Gold block placed! <yellow>Use <green>/trains create <yellow>to make this a station!"))
        }
    }
}