package org.saintqd.vineriumfishing.listeners

import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import io.lumine.mythic.api.MythicProvider
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.MythicBukkit
import net.kyori.adventure.audience.Audience
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Item
import org.bukkit.entity.Mob
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.saintqd.vineriumfishing.VineriumFishing
import org.saintqd.vineriumfishing.fishing.FishingTemplate
import org.saintqd.vineriumfishing.managers.FishingManager
import org.saintqd.vineriumfishing.utils.MMAbilityData
import org.saintqd.vineriumfishing.worldguard.Flags
import org.saintqd.vineriumlib.VineriumLib
import org.saintqd.vineriumlib.utils.VinUtils
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class PlayerListener : Listener {

    val antiFishFarmTimers = hashMapOf<UUID, Long>()

    @EventHandler
    fun onPlayerUse(event: PlayerInteractEvent) {
        if (!event.action.toString().contains("RIGHT")) return
        if (event.hand == EquipmentSlot.OFF_HAND) return
        if (event.getPlayer().isDead) return

        event.clickedBlock?.let { _ ->
            if (event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.player.gameMode != org.bukkit.GameMode.CREATIVE)
                antiFishFarmTimers[event.player.uniqueId] = VinUtils.getCurrentTick() + 100
        }
    }

    @EventHandler
    fun onPlayerFish(event : PlayerFishEvent) {

        if (event.state == PlayerFishEvent.State.CAUGHT_FISH) {

            if (!VineriumFishing.inst().config.getBoolean("Enabled",true))
                return

            event.caught.let { caught ->
                if (caught !is Item)
                    return
                val caughtItem = caught.itemStack
                when (caughtItem.type) {
                    Material.COD, Material.SALMON, Material.PUFFERFISH, Material.TROPICAL_FISH -> {}
                    else -> return
                }
                caught.itemStack = ItemStack.of(Material.AIR)
                if (VineriumFishing.inst().config.getBoolean("AntiAfk.Enabled",true)) {
                    val timer = antiFishFarmTimers.getOrDefault(event.player.uniqueId, 0L)
                    if (timer > VinUtils.getCurrentTick() || event.player.isInsideVehicle) {
                        event.player.sendMessage(
                            VineriumLib.inst().langManager.parseLangString(
                                VineriumFishing.inst(),
                                "fishing_catch_fail"
                            )
                        )
                        return
                    }
                    else {
                        val centerX = event.hook.location.blockX
                        val centerY = event.hook.location.blockY
                        val centerZ = event.hook.location.blockZ
                        val world = event.hook.world
                        for (x in centerX-1..centerX+1) {
                            for (z in centerZ-1..centerZ+1) {
                                val block = world.getBlockAt(x,centerY,z)
                                if (block.type != Material.WATER) {
                                    event.player.sendMessage(
                                        VineriumLib.inst().langManager.parseLangString(
                                            VineriumFishing.inst(),
                                            "fishing_catch_fail"
                                        )
                                    )
                                    return
                                }
                            }
                        }
                    }
                }
            }

            val container = WorldGuard.getInstance().platform.regionContainer
            val localPlayer = WorldGuardPlugin.inst().wrapPlayer(event.player)
            val regionSet = container.createQuery().getApplicableRegions(localPlayer.location)
            val dropTableName = regionSet.queryValue(localPlayer, Flags.FISHING_TEMPLATE)
                ?: VineriumFishing.inst().config.getString("DefaultTemplate","default")

            val fishingTemplate = FishingManager.instance.fishingTemplates[dropTableName] ?: return

            // Поймать моба возможно только при активной рыбалке
            if ((event.state != PlayerFishEvent.State.BITE) && fishingTemplate.mob != null
                && ThreadLocalRandom.current().nextDouble() < fishingTemplate.mob.second ) {
                event.player.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumFishing.inst(), "fishing_catch_mob"))
                val mobName = fishingTemplate.mob.first
                val mythicBukkit = MythicProvider.get() as MythicBukkit
                val activeMob = mythicBukkit.mobManager.spawnMob(mobName, BukkitAdapter.adapt(event.hook.location),
                    io.lumine.mythic.api.mobs.entities.SpawnReason.NATURAL, 1.0)
                val bukkitEntity = activeMob.entity.bukkitEntity
                event.hook.hookedEntity = bukkitEntity
                event.hook.pullHookedEntity()
                if (bukkitEntity is Mob) bukkitEntity.target = event.getPlayer()
                return
            }

            var selectedBaitName : String? = null
            val offHandItem = event.player.inventory.itemInOffHand
            if (offHandItem.type != Material.AIR) {
                if (offHandItem.persistentDataContainer.has(FishingManager.BAIT_KEY)) {
                    selectedBaitName = MythicProvider.get().itemManager.getMythicTypeFromItem(offHandItem)
                }
            }
            VinUtils.sendDebugMessage(3, "Looking for possible categories for selected bait $selectedBaitName.")

            val possibleCategories = mutableListOf<FishingTemplate.FishingCategory>()
            for (category in fishingTemplate.categories) {
                VinUtils.sendDebugMessage(4, "Checking category ${category.name}:")
                var possible = false
                if (category.baits.isNotEmpty()) {
                    VinUtils.sendDebugMessage(4, "Category has bait requirement of ${category.baits} types.")
                    if (selectedBaitName != null) {
                        for (baitName in category.baits) {
                            if (selectedBaitName == baitName) {
                                VinUtils.sendDebugMessage(4, "Required bait found.")
                                possible = true
                            } else if (baitName.startsWith("$")) {
                                val tagItems = FishingManager.instance.baitTags[baitName.substring(1)] ?: continue
                                if (tagItems.contains(selectedBaitName)) {
                                    VinUtils.sendDebugMessage(4, "Required bait found.")
                                    possible = true
                                }
                            }
                        }
                    } else if (category.baits.contains("NONE"))
                        possible = true
                } else
                    possible = true
                if (possible) {
                    VinUtils.sendDebugMessage(4, "Category ${category.name} is possible.")
                    possibleCategories.add(category)
                }
            }

            var dropAmount = 1
            val finalDrops = mutableListOf<ItemStack>()

            val fishingRod = event.player.inventory.itemInMainHand
            val bonusCoef = 1 + (fishingRod.enchantments[Enchantment.LUCK_OF_THE_SEA] ?: 0) * 0.1
            var selectedCategory: FishingTemplate.FishingCategory? = null
            val chance = ThreadLocalRandom.current().nextDouble()
            for (possibleCategory in possibleCategories) {
                if (chance < possibleCategory.chance * bonusCoef)
                    selectedCategory = possibleCategory
            }

            if (selectedCategory == null) {
                event.player.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumFishing.inst(), "fishing_catch_fail"))
                return
            }
            val playerBiomeName = event.player.location.block.biome.key.key
            val possibleFishList = mutableListOf<FishingTemplate.FishData>()
            selectedCategory.fishData["ANY"]?.let {
                possibleFishList.addAll(it)
            }
            var checkConditions = true
            selectedCategory.fishData[playerBiomeName.uppercase()]?.let {
                possibleFishList.addAll(it)
            }
            if (checkConditions) {
                possibleFishList.removeIf { possibleFish ->
                    if (possibleFish.conditionSkill != null) {
                        val skillMetadata = MMAbilityData.prepareMMSkillData(event.player)
                        skillMetadata.setEntityTarget(BukkitAdapter.adapt(event.player))
                        if (!possibleFish.conditionSkill.isUsable(skillMetadata))
                            return@removeIf true
                    }
                    return@removeIf false
                }
            }

            if (possibleFishList.isEmpty()) {
                event.player.sendMessage(VineriumLib.inst().langManager.parseLangString(VineriumFishing.inst(), "fishing_catch_fail"))
                return
            }
            val selectedFish = possibleFishList.random()
            val selectedFishName = FishingManager.instance.parseFishTag(selectedFish.name)
            val selectedFishItem = MythicProvider.get().itemManager.getItem(selectedFishName) ?: return
            if (selectedFishItem.isPresent) {
                finalDrops.add(BukkitAdapter.adapt(selectedFishItem.get().generateItemStack(1)))
                if (selectedFish.message != null) event.player.sendRichMessage(selectedFish.message)
                if (selectedFish.sound != null) {
                    val players = event.hook.location.getNearbyPlayers(25.0, 25.0, 25.0)
                    val audience: Audience = Audience.audience(players)
                    audience.playSound(
                        selectedFish.sound,
                        event.hook.x,
                        event.hook.y,
                        event.hook.z
                    )
                }
                selectedFish.conditionSkill?.let {
                    val skillMetadata = MMAbilityData.prepareMMSkillData(event.player)
                    skillMetadata.setEntityTarget(BukkitAdapter.adapt(event.player))
                    it.execute(skillMetadata)
                }
            }

            if (selectedBaitName != null) {
                event.player.inventory.itemInOffHand.amount--
            }
            if (finalDrops.isEmpty())
                return
            val selectedItem = finalDrops.first()
            // Весь дополнительный дроп помимо первого бросаем под игрока
            if (finalDrops.size > 1) {
                finalDrops.removeFirst()
                for (drop in finalDrops) {
                    event.player.world.dropItem(event.player.location, drop)
                }
            }
            if (selectedItem.type != Material.AIR) {
                event.caught?.let {
                    if (it is Item) {
                        it.itemStack = selectedItem
                    }
                }
            }
        }
    }

    @EventHandler
    fun onItemClick(event: InventoryClickEvent) {
        if (event.action != InventoryAction.SWAP_WITH_CURSOR) return
        if (event.clickedInventory != event.whoClicked.inventory) return
        if (event.slot == -1 || event.slot == -999) return

        event.currentItem?.let { currentItem -> // Предмет, на который кликают
            if (event.whoClicked.itemOnCursor.type != Material.AIR && currentItem.type != Material.AIR
            ) {
                val cursorItem = event.whoClicked.itemOnCursor // Предмет в курсоре
                if (FishingManager.instance.salvageTools.contains(cursorItem.type.name.uppercase())) {
                    if (currentItem.persistentDataContainer.has(FishingManager.SALVAGE_MATS_KEY)) {
                        event.isCancelled = true
                        FishingManager.instance.salvageFish(event.whoClicked, currentItem)
                    }
                }
            }
        }
    }
}