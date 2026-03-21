package org.saintqd.vineriumfishing

import org.bukkit.plugin.java.JavaPlugin
import org.saintqd.vineriumfishing.commands.VinFishingCommands
import org.saintqd.vineriumfishing.listeners.PlayerListener
import org.saintqd.vineriumfishing.managers.FishingManager
import org.saintqd.vineriumfishing.worldguard.Flags
import org.saintqd.vineriumlib.VineriumLib
import org.saintqd.vineriumlib.utils.ResourceUtils
import java.io.File

class VineriumFishing : JavaPlugin() {

    companion object {
        private var plugin : VineriumFishing? = null

        fun inst() : VineriumFishing {
            return plugin!!
        }
    }

    override fun onLoad() {
        plugin = this
        Flags.registerFlags()
    }

    override fun onEnable() {
        ResourceUtils.fetchAllResources(this, file)

        loadData()

        VinFishingCommands.setupCommands(this)

        server.pluginManager.registerEvents(PlayerListener(), this)
    }

    fun loadData() {
        reloadConfig()

        val selectedLang = getConfig().getString("Language")
        val langLines = VineriumLib.inst().langManager.loadLanguageFile(
            this,
            dataFolder.path + File.separator + "lang" + File.separator + selectedLang + ".yml"
        )
        VineriumLib.inst().langManager.registerLangLines(langLines)

        var prevTime = System.currentTimeMillis()
        FishingManager.instance.loadParams(this)
        var time = System.currentTimeMillis()
        logger.info("Loaded " + FishingManager.instance.fishingTemplates.size + " fishing templates. ("+(time-prevTime)+" ms)");
        prevTime = System.currentTimeMillis()
    }
}