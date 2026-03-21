package org.saintqd.vineriumfishing.utils

import io.lumine.mythic.api.MythicProvider
import io.lumine.mythic.api.adapters.AbstractLocation
import io.lumine.mythic.api.mobs.GenericCaster
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.SkillTrigger
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.core.adapters.VirtualEntity
import io.lumine.mythic.core.skills.SkillMetadataImpl
import io.lumine.mythic.core.utils.MythicUtil
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

class MMAbilityData {

    companion object Utils {
        val virtualCaster = GenericCaster(VirtualEntity(AbstractLocation("world", 0.0, 0.0, 0.0)))

        fun prepareMMSkillData(caster : Entity?) : SkillMetadata {
            var skillData : SkillMetadata? = null
            if (caster != null) {
                val possibleMobCaster = MythicProvider.get().mobManager.getSkillCaster(caster.uniqueId)
                val skillCaster = if (possibleMobCaster.isPresent)
                    possibleMobCaster.get()
                else GenericCaster(BukkitAdapter.adapt(caster))
                skillData = SkillMetadataImpl(SkillTrigger.get("CUSTOM"), skillCaster, skillCaster.entity)
                var targetEntity : LivingEntity? = null
                if (caster is Player)
                    targetEntity = MythicUtil.getTargetedEntity(caster)
                // Если имеется объект в перекрестии - используем как цель, иначе используем как цель локацию игрока
                val abstractLocation = BukkitAdapter.adapt(caster.location)
                skillData.origin = abstractLocation
                if (targetEntity != null)
                    skillData.setEntityTarget(BukkitAdapter.adapt(targetEntity))
                else
                    skillData.setLocationTarget(abstractLocation)
            }
            else {
                skillData = SkillMetadataImpl(SkillTrigger.get("CUSTOM"), GenericCaster(VirtualEntity(AbstractLocation("world_nether", 0.0, 0.0, 0.0))), null)
            }
            skillData.power = 1F
            return skillData
        }

        fun executeMMSkill(skillName : String, data : SkillMetadata) : Boolean {
            val possibleSkill = MythicProvider.get().skillManager.getSkill(skillName)
            if (possibleSkill.isPresent) {
                val skill = possibleSkill.get()
                if (skill.isUsable(data)) {
                    skill.execute(data)
                }
                return true
            }
            return false
        }
    }
}