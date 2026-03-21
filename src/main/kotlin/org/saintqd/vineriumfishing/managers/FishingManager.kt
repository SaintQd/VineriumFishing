package org.saintqd.vineriumfishing.managers

import io.lumine.mythic.api.MythicProvider
import io.lumine.mythic.bukkit.BukkitAdapter
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.checkerframework.checker.units.qual.min
import org.saintqd.vineriumfishing.VineriumFishing
import org.saintqd.vineriumfishing.fishing.FishingTemplate
import org.saintqd.vineriumlib.utils.VinUtils
import java.io.File
import java.util.logging.Level

class FishingManager {

    val fishingTemplates = hashMapOf<String, FishingTemplate>()
    val baitTags = hashMapOf<String, MutableList<String>>()
    val fishTags = hashMapOf<String, MutableList<String>>()

    val salvageTools = hashSetOf<String>()

    companion object {
        val instance: FishingManager by lazy { FishingManager() }

        val BAIT_KEY = NamespacedKey(VineriumFishing.inst(),"fishing_bait")
        val SALVAGE_MATS_KEY = NamespacedKey(VineriumFishing.inst(),"salvage_mats")
    }

    fun loadParams(plugin : JavaPlugin) {
        fishingTemplates.clear()
        baitTags.clear()
        fishTags.clear()
        salvageTools.clear()
        val templatesDir = File(plugin.dataFolder, "FishingTemplates")
        if (!templatesDir.exists()) {
            plugin.logger.log(Level.INFO,"FishingTemplates directory does not exist, creating it.")
            if (!templatesDir.mkdir()) {
                plugin.logger.log(Level.SEVERE,"Could not create FishingTemplates directory!")
                return
            }
        }
        val filePaths = VinUtils.listFilesInFolder(plugin.dataFolder.path + File.separator + "FishingTemplates")

        for (filePath in filePaths) {
            val file = filePath.toFile()
            val config = YamlConfiguration.loadConfiguration(filePath.toFile())
            for (templateName in config.getKeys(false)) {
                fishingTemplates[templateName] = FishingTemplate(templateName,config,file)
            }
        }

        val baitTagsFile = File(plugin.dataFolder, "BaitTags.yml")
        if (!baitTagsFile.exists()) {
            plugin.logger.log(Level.INFO,"BaitTags file does not exist, creating it.")
            baitTagsFile.createNewFile()
        }
        val baitTagsConfig = YamlConfiguration.loadConfiguration(baitTagsFile)
        baitTagsConfig.getConfigurationSection("BaitTags")?.let { config ->
            for (itemName in config.getKeys(false)) {
                val tagItems = baitTags.getOrDefault(itemName, ArrayList())
                tagItems.addAll(config.getStringList(itemName))
                baitTags[itemName] = tagItems
            }
        }

        val fishTagsFile = File(plugin.dataFolder, "FishTags.yml")
        if (!fishTagsFile.exists()) {
            plugin.logger.log(Level.INFO,"FishTags file does not exist, creating it.")
            fishTagsFile.createNewFile()
        }
        val fishTagsConfig = YamlConfiguration.loadConfiguration(fishTagsFile)
        fishTagsConfig.getConfigurationSection("FishTags")?.let { config ->
            for (itemName in config.getKeys(false)) {
                val tagItems = fishTags.getOrDefault(itemName, ArrayList())
                tagItems.addAll(config.getStringList(itemName))
                fishTags[itemName] = tagItems
            }
        }
        salvageTools.addAll(plugin.config.getStringList("SalvageTools"))
    }

    fun parseBaitTag(possibleTagName : String) : String {

        // Для тега используем символ $, так как символ # в YAML используется как комментарий
        if (possibleTagName.startsWith("$")) {
            val parsedTag = possibleTagName.substring(1)
            baitTags[parsedTag]?.let {
                if (it.isNotEmpty()) {
                    return it.random()
                }
            }
        }
        return possibleTagName
    }

    fun parseFishTag(possibleTagName : String) : String {

        // Для тега используем символ $, так как символ # в YAML используется как комментарий
        if (possibleTagName.startsWith("$")) {
            val parsedTag = possibleTagName.substring(1)
            fishTags[parsedTag]?.let {
                if (it.isNotEmpty()) {
                    return it.random()
                }
            }
        }
        return possibleTagName
    }

    fun salvageFish(player : HumanEntity, fishItem : ItemStack) {
        if (player !is Player) return
        var salvageMatsPdc =
            fishItem.persistentDataContainer.get(SALVAGE_MATS_KEY, PersistentDataType.TAG_CONTAINER) ?: return
        val mmItemName = MythicProvider.get().itemManager.getMythicTypeFromItem(fishItem)
        if (mmItemName != null) {
            val optionalMMItem = MythicProvider.get().itemManager.getItem(mmItemName)
            if (optionalMMItem != null && optionalMMItem.isPresent) {
                val mmItemStack = BukkitAdapter.adapt(optionalMMItem.get().generateItemStack(1))
                salvageMatsPdc = mmItemStack.persistentDataContainer.get(SALVAGE_MATS_KEY, PersistentDataType.TAG_CONTAINER) ?: return
            }
        }
        val salvageMats = mutableListOf<ItemStack>()
        val fishItemAmount = fishItem.amount
        for (key in salvageMatsPdc.keys) {
            var itemStack : ItemStack? = null
            val amount = salvageMatsPdc.getOrDefault(key, PersistentDataType.INTEGER,1) * fishItemAmount
            if (key.namespace == "mm") {
                val optionalMMItem = MythicProvider.get().itemManager.getItem(key.key)
                if (optionalMMItem != null && optionalMMItem.isPresent) {
                    itemStack = BukkitAdapter.adapt(optionalMMItem.get().generateItemStack(1))
                }
            }
            else {
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM).get(key)?.let { item ->
                    itemStack = item.createItemStack(1)
                }
            }
            if (itemStack != null) {
                if (amount > 64) {
                    var remainingAmount = amount
                    while (remainingAmount > 0) {
                        val clonedItemStack = itemStack.clone()
                        clonedItemStack.amount = kotlin.math.min(remainingAmount,64)
                        remainingAmount -= clonedItemStack.amount
                        salvageMats.add(clonedItemStack)
                    }
                }
                else {
                    itemStack.amount = amount
                    salvageMats.add(itemStack)
                }
            }
        }
        if (salvageMats.isNotEmpty()) {
            fishItem.amount = 0
            player.location.world.playSound(player.location,Sound.ENTITY_SLIME_DEATH, SoundCategory.PLAYERS,1f,1f)
            player.give(salvageMats)
        }
    }
}