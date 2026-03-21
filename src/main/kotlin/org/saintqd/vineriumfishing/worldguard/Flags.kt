package org.saintqd.vineriumfishing.worldguard

import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.StringFlag

class Flags {

    companion object {
        val FISHING_TEMPLATE = StringFlag("fishing-template")

        fun registerFlags() {
            val flagRegistry = WorldGuard.getInstance().flagRegistry
            flagRegistry.register(FISHING_TEMPLATE)
        }
    }
}