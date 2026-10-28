package dev.Error110.trains

import dev.Error110.trains.commands.TrainAdminCommand
import dev.Error110.trains.commands.TrainCommand
import dev.Error110.trains.listeners.StationInteractListener
import dev.Error110.trains.managers.DatabaseManager
import dev.Error110.trains.managers.StationManager
import org.bukkit.plugin.java.JavaPlugin

class Trains : JavaPlugin() {

    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var stationManager: StationManager
        private set

    override fun onEnable() {
        saveDefaultConfig()

        databaseManager = DatabaseManager(this)
        stationManager = StationManager(this)
        stationManager.start()

        getCommand("trains")?.setExecutor(TrainCommand(this))
        getCommand("trainadmin")?.setExecutor(TrainAdminCommand(this))
        server.pluginManager.registerEvents(StationInteractListener(this), this)

        logger.info("Trains plugin enabled!")
    }

    override fun onDisable() {
        try {
            if (::stationManager.isInitialized) stationManager.shutdown()
        } finally {
            try {
                if (::databaseManager.isInitialized) databaseManager.close()
            } finally {
                server.scheduler.cancelTasks(this)
            }
        }
        logger.info("Trains plugin disabled!")
    }
}
