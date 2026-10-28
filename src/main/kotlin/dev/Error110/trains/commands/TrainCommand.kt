package dev.Error110.trains.commands

import dev.Error110.trains.Trains
import dev.Error110.trains.utils.countRoofDepth
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class TrainCommand(private val plugin: Trains) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("create").filter { it.startsWith(args[0].lowercase()) }
            else -> emptyList()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
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

                if (countRoofDepth(block) > 4) {
                    sender.sendMessage(mm.deserialize("<red>Station is too underground! Max 4 solid blocks above allowed."))
                    return true
                }

                val (id, error) = plugin.stationManager.createStation(block.location)
                if (error != null) {
                    sender.sendMessage(mm.deserialize("<red>$error"))
                    return true
                }

                sender.sendMessage(mm.deserialize("<green>Station created with ID: <yellow>$id"))
                sender.sendMessage(mm.deserialize("<gray>Connections update every 1 minute"))
            }
            else -> {
                sender.sendMessage(mm.deserialize("<gold>Train Commands:"))
                sender.sendMessage(mm.deserialize("<yellow>/trains create <gray>- Create station at gold block"))
            }
        }
        return true
    }
}
