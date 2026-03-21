package org.saintqd.vineriumfishing.fishing

import io.lumine.mythic.api.skills.Skill
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.core.config.MythicConfigImpl
import io.lumine.mythic.core.skills.MetaSkill
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.configuration.file.FileConfiguration
import java.io.File

class FishingTemplate(val name : String, config : FileConfiguration, file : File) {

    val mob : Pair<String,Double>? = if (config.contains("$name.MobType")) {
        Pair(config.getString("$name.MobType")!!,config.getDouble("$name.MobChance",0.05))
    } else null
    val categories = mutableListOf<FishingCategory>()

    init {
        config.getConfigurationSection("$name.Categories")?.let { categoriesConfig ->
            for (categoryName in categoriesConfig.getKeys(false)) {
                val chance = categoriesConfig.getDouble("$categoryName.Chance", 100.0)
                val rare = categoriesConfig.getBoolean("$categoryName.Rare")
                val container: Pair<String, Double>? = if (categoriesConfig.contains("$categoryName.ContainerName")) {
                    Pair(
                        categoriesConfig.getString("$categoryName.ContainerName")!!,
                        categoriesConfig.getDouble("$categoryName.ContainerChance", 0.1)
                    )
                } else null
                val baits = categoriesConfig.getStringList("$categoryName.Baits").toHashSet()
                val fishData = hashMapOf<String, MutableList<FishData>>()

                categoriesConfig.getConfigurationSection("$categoryName.List")?.let { biomesConfig ->
                    for (unparsedBiomeType in biomesConfig.getKeys(false)) {
                        val splitBiomeType = unparsedBiomeType.split(",").toMutableList()
                        val firstBiomeType = splitBiomeType.first()
                        biomesConfig.getConfigurationSection(unparsedBiomeType)?.let { fishDataConfig ->
                            for (fishName in fishDataConfig.getKeys(false)) {
                                var conditionSkill : Skill? = null

                                if (fishDataConfig.contains("$fishName.Conditions")
                                    || fishDataConfig.contains("$fishName.TargetConditions")
                                    || fishDataConfig.contains("$fishName.TriggerConditions")
                                    || fishDataConfig.contains("$fishName.Skills")) {
                                    val mythicConfig = MythicConfigImpl(
                                        "$name.Categories.$categoryName.List.$unparsedBiomeType.$fishName",
                                        file,
                                        config
                                    )
                                    val skillName = "FishingTemplate_${name}_${categoryName}_${firstBiomeType}_${fishName}"
                                    val pack = MythicBukkit.inst().packManager.basePack
                                    conditionSkill = MetaSkill(
                                        MythicBukkit.inst().skillManager,
                                        pack,
                                        file,
                                        skillName,
                                        mythicConfig
                                    )
                                }

                                val message = fishDataConfig.getString("$fishName.Message",null)
                                var sound : Sound? = null
                                if (fishDataConfig.contains("$fishName.Sound")) {
                                    val splitSoundData = fishDataConfig.getString("$fishName.Sound")!!.split(",")
                                    val pitch = if (splitSoundData.size > 1) splitSoundData[1].toFloat() else 1f
                                    sound = Sound.sound(Key.key("minecraft",splitSoundData[0]),Sound.Source.NEUTRAL,1f,pitch)
                                }

                                val biomeFishData = fishData.getOrDefault(firstBiomeType, mutableListOf())
                                biomeFishData.add(FishData(fishName,conditionSkill,message,sound))
                                fishData[firstBiomeType] = biomeFishData
                            }
                        }
                        splitBiomeType.removeFirst()
                        for (otherBiomeType in splitBiomeType) {
                            fishData[otherBiomeType] = fishData[firstBiomeType] ?: mutableListOf()
                        }
                    }
                }
                val fishingCategory = FishingCategory(categoryName,chance,fishData, container, baits)
                categories.add(fishingCategory)
            }
        }
    }

    data class FishingCategory(
        val name : String,
        val chance : Double,
        // Список рыбы - HashMap<Биом,MutableList<инфа о рыбе>>
        val fishData : HashMap<String,MutableList<FishData>>,
        val container : Pair<String,Double>?,
        val baits : HashSet<String>
    )

    data class FishData(
        val name : String,
        val conditionSkill: Skill?,
        val message : String?,
        val sound : Sound?
    )
}