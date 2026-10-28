package dev.Error110.trains.commands

import dev.Error110.trains.Trains
import dev.Error110.trains.utils.Station
import dev.Error110.trains.utils.countRoofDepth
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.UUID

class TrainAdminCommand(private val plugin: Trains) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()
    private val reviewSessions = mutableMapOf<UUID, Int>()

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("trains.admin")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> {
                listOf("create", "scan", "scanall", "tp", "ban", "unban", "list", "remove", "review", "reload")
                    .filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "scan", "tp", "ban", "unban", "remove" -> {
                        plugin.stationManager.getAllStations()
                            .map { it.id.toString() }
                            .filter { it.startsWith(args[1]) }
                    }
                    "review" -> listOf("next", "prev", "ban", "unban").filter { it.startsWith(args[1].lowercase()) }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("trains.admin")) {
            sender.sendMessage(mm.deserialize("<red>You don't have permission!"))
            return true
        }

        if (args.getOrNull(0)?.equals("reload", ignoreCase = true) == true) {
            plugin.reloadConfig()
            sender.sendMessage(mm.deserialize("<green>Config reloaded. New train speeds apply to new journeys."))
            return true
        }

        if (sender !is Player) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "create" -> {
                val block = sender.getTargetBlockExact(5)
                if (block?.type != Material.GOLD_BLOCK) {
                    sender.sendMessage(mm.deserialize("<red>You must look at a gold block!"))
                    return true
                }

                val (id, error) = plugin.stationManager.createStation(block.location)
                if (error != null) {
                    sender.sendMessage(mm.deserialize("<red>$error"))
                    return true
                }

                sender.sendMessage(mm.deserialize("<green>Station created with ID: <yellow>$id"))
                sender.sendMessage(mm.deserialize("<gray>Use /trainadmin scan $id to detect rail connections"))
            }
            "scan" -> {
                val id = args.getOrNull(1)?.toIntOrNull()
                if (id == null) {
                    sender.sendMessage(mm.deserialize("<red>Invalid station ID!"))
                    return true
                }

                if (plugin.stationManager.getStation(id) == null) {
                    sender.sendMessage(mm.deserialize("<red>Station not found!"))
                    return true
                }

                val count = plugin.stationManager.scanAndCreateEdgesForStation(id)
                sender.sendMessage(mm.deserialize("<green>Scanned station! Found <yellow>$count<green> rail connection(s)"))
            }
            "scanall" -> {
                var totalEdges = 0
                plugin.stationManager.getAllStations().forEach { station ->
                    totalEdges += plugin.stationManager.scanAndCreateEdgesForStation(station.id)
                }
                sender.sendMessage(mm.deserialize("<green>Rescanned all stations! Total edges: <yellow>$totalEdges"))
            }
            "tp" -> {
                val id = args.getOrNull(1)?.toIntOrNull()
                if (id == null) {
                    sender.sendMessage(mm.deserialize("<red>Invalid station ID!"))
                    return true
                }

                val station = plugin.stationManager.getStation(id)
                if (station == null) {
                    sender.sendMessage(mm.deserialize("<red>Station not found!"))
                    return true
                }

                sender.teleport(station.location.clone().add(0.5, 1.0, 0.5))
                sender.sendMessage(mm.deserialize("<green>Teleported to station <yellow>$id"))
            }
            "ban" -> {
                val id = args.getOrNull(1)?.toIntOrNull()
                if (id == null) {
                    sender.sendMessage(mm.deserialize("<red>Invalid station ID!"))
                    return true
                }

                val station = plugin.stationManager.getStation(id)
                if (station == null) {
                    sender.sendMessage(mm.deserialize("<red>Station not found!"))
                    return true
                }

                station.banned = true
                plugin.stationManager.updateStation(station)
                sender.sendMessage(mm.deserialize("<green>Station <yellow>$id <green>banned!"))
            }
            "unban" -> {
                val id = args.getOrNull(1)?.toIntOrNull()
                if (id == null) {
                    sender.sendMessage(mm.deserialize("<red>Invalid station ID!"))
                    return true
                }

                val station = plugin.stationManager.getStation(id)
                if (station == null) {
                    sender.sendMessage(mm.deserialize("<red>Station not found!"))
                    return true
                }

                station.banned = false
                plugin.stationManager.updateStation(station)
                sender.sendMessage(mm.deserialize("<green>Station <yellow>$id <green>unbanned!"))
            }
            "list" -> {
                sender.sendMessage(mm.deserialize("<gold>=== Stations ==="))
                plugin.stationManager.getAllStations().forEach { station ->
                    val status = if (station.banned) "<red>[BANNED]" else "<green>[ACTIVE]"
                    val edges = plugin.stationManager.getEdgesFromStation(station.id)
                    sender.sendMessage(mm.deserialize("$status <yellow>${station.id} <gray>(${edges.size} connections)"))
                }
            }
            "remove" -> {
                val id = args.getOrNull(1)?.toIntOrNull()
                if (id == null) {
                    sender.sendMessage(mm.deserialize("<red>Invalid station ID!"))
                    return true
                }

                plugin.stationManager.removeStation(id)
                sender.sendMessage(mm.deserialize("<green>Station <yellow>$id <green>removed!"))
            }
            "review" -> {
                val stations = plugin.stationManager.getAllStations().sortedBy { it.id }
                if (stations.isEmpty()) {
                    sender.sendMessage(mm.deserialize("<red>No stations exist!"))
                    return true
                }

                var idx = reviewSessions.getOrDefault(sender.uniqueId, 0).coerceIn(0, stations.size - 1)
                val subAction = args.getOrNull(1)?.lowercase()

                when (subAction) {
                    "next" -> idx = (idx + 1) % stations.size
                    "prev" -> idx = if (idx <= 0) stations.size - 1 else idx - 1
                    "ban" -> {
                        stations[idx].banned = true
                        plugin.stationManager.updateStation(stations[idx])
                    }
                    "unban" -> {
                        stations[idx].banned = false
                        plugin.stationManager.updateStation(stations[idx])
                    }
                }

                reviewSessions[sender.uniqueId] = idx

                if (subAction != "ban" && subAction != "unban") {
                    sender.teleport(stations[idx].location.clone().add(0.5, 1.0, 0.5))
                }

                showReviewUI(sender, stations, idx)
            }
            else -> {
            sender.sendMessage(mm.deserialize("<gold>Train Admin Commands:"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin create <gray>- Create station at gold block"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin scan <id> <gray>- Scan for rail connections"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin scanall <gray>- Rescan all stations"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin tp <id> <gray>- Teleport to station"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin ban/unban <id> <gray>- Ban/unban station"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin list <gray>- List all stations"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin remove <id> <gray>- Remove station"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin review <gray>- Cycle through stations to review"))
            sender.sendMessage(mm.deserialize("<yellow>/trainadmin reload <gray>- Reload the config"))
        }
        }
        return true
    }

    private fun showReviewUI(sender: Player, stations: List<Station>, idx: Int) {
        val station = stations[idx]
        val loc = station.location
        val roofDepth = countRoofDepth(loc.block)
        val statusText = if (station.banned) "<red>✗ BANNED" else "<green>✔ OK"
        val banCmd = if (station.banned) "unban" else "ban"
        val banLabel = if (station.banned) "<green>[UNBAN]" else "<red>[BAN]"

        sender.sendMessage(mm.deserialize("<gray>--- <gold>Station #${station.id} <gray>(${idx + 1} / ${stations.size}) ---"))
        sender.sendMessage(mm.deserialize("<gray>${loc.world?.name} | x=${loc.blockX}, y=${loc.blockY}, z=${loc.blockZ}  roof: <yellow>$roofDepth"))
        sender.sendMessage(mm.deserialize("<gray>Status: $statusText"))
        sender.sendMessage(mm.deserialize(
            "<click:run_command:/trainadmin review prev><gray>[<yellow>← PREV<gray>]</click>  " +
            "<click:run_command:/trainadmin review $banCmd>$banLabel</click>  " +
            "<click:run_command:/trainadmin review next><gray>[<yellow>NEXT →<gray>]</click>"
        ))
    }
}
