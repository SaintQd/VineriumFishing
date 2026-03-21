package org.saintqd.vineriumfishing.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.saintqd.vineriumfishing.VineriumFishing
import org.saintqd.vineriumlib.VineriumLib

class VinFishingCommands {

    companion object {

        fun setupCommands(plugin : VineriumFishing) {
            val manager = plugin.lifecycleManager
            manager.registerEventHandler(LifecycleEvents.COMMANDS, {
                val commands: Commands = it.registrar()
                commands.register(
                    Commands.literal("vinfishing")
                        .executes { commandContext: CommandContext<CommandSourceStack?>? ->
                            commandContext!!.getSource()!!.sender.sendMessage(
                                VineriumLib.inst().langManager.parseLangString(VineriumFishing.inst(), "not_enough_arguments")
                            )
                            Command.SINGLE_SUCCESS
                        }
                        .then(
                            Commands.literal("reload")
                                .requires { predicate: CommandSourceStack? ->
                                    predicate!!.sender.hasPermission("vineriumfishing.admin")
                                }
                                .executes { ctx: CommandContext<CommandSourceStack?>? ->
                                    reloadCommand(
                                        ctx!!.getSource()!!.sender
                                    )
                                    Command.SINGLE_SUCCESS
                                }
                        )
                        .build(),
                    "Основная команда."
                )
            })
        }

        private fun reloadCommand(sender: CommandSender?) {
            VineriumFishing.inst().loadData()
            if (sender is Player) sender.sendMessage(
                VineriumLib.inst().langManager.parseLangString(VineriumFishing.inst(), "reload_message")
            )
        }
    }

}