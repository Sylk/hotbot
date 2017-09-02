package me.aberrantfox.aegeus.commandframework.commands

import me.aberrantfox.aegeus.commandframework.ArgumentType
import me.aberrantfox.aegeus.commandframework.Command
import me.aberrantfox.aegeus.commandframework.stringToPermission
import me.aberrantfox.aegeus.commandframework.util.idToUser
import me.aberrantfox.aegeus.commandframework.util.muteMember
import me.aberrantfox.aegeus.commandframework.util.unmute
import me.aberrantfox.aegeus.services.Configuration
import me.aberrantfox.aegeus.services.MessageService
import me.aberrantfox.aegeus.services.MessageType
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent


@Command(ArgumentType.Integer)
fun nuke(event: GuildMessageReceivedEvent, args: List<Any>) {
    val amount = args[0] as Int

    if (amount <= 0) {
        event.channel.sendMessage("Yea, what exactly is the point in nuking nothing... ?").queue()
        return
    }

    event.channel.history.retrievePast(amount + 1).queue({
        it.forEach { it.delete().queue() }
        event.channel.sendMessage("Be nice. No spam.").queue()
    })
}

@Command(ArgumentType.String)
fun ignore(event: GuildMessageReceivedEvent, args: List<Any>, config: Configuration) {
    val target = args[0] as String

    if (config.ignoredIDs.contains(target)) {
        config.ignoredIDs.remove(target)
        event.channel.sendMessage("Unignored $target").queue()
    } else {
        config.ignoredIDs.add(target)
        event.channel.sendMessage("$target? Who? What? Don't know what that is. ;)").queue()
    }
}

@Command(ArgumentType.UserID, ArgumentType.Integer, ArgumentType.Joiner)
fun mute(event: GuildMessageReceivedEvent, args: List<Any>, config: Configuration) {
    val user = (args[0] as String).idToUser(event.jda)
    val time = (args[1] as Int).toLong() * 1000 * 60
    val reason = args[2] as String
    val guild = event.guild

    muteMember(guild, user, time, reason, config, event.author)
}

@Command
fun lockdown(event: GuildMessageReceivedEvent, args: List<Any>, config: Configuration) {
    config.lockDownMode = !config.lockDownMode
    event.channel.sendMessage("Lockdown mode is now set to: ${config.lockDownMode}.").queue()
}

@Command(ArgumentType.String)
fun prefix(event: GuildMessageReceivedEvent, args: List<Any>, config: Configuration) {
    val newPrefix = args[0] as String
    config.prefix = newPrefix
    event.channel.sendMessage("Prefix is now $newPrefix. Please invoke commands using that prefix in the future. " +
            "To save this configuration, use the saveconfigurations command.").queue()
    event.jda.presence.setPresence(OnlineStatus.ONLINE, Game.of("${config.prefix}help"))
}

@Command(ArgumentType.String)
fun setFilter(event: GuildMessageReceivedEvent, args: List<Any>, config: Configuration) {
    val desiredLevel = stringToPermission((args[0] as String).toUpperCase())

    if (desiredLevel == null) {
        event.channel.sendMessage("Don't know that permission level boss... ").queue()
        return
    }

    config.mentionFilterLevel = desiredLevel
    event.channel.sendMessage("Permission level now set to: ${desiredLevel.name} ; be sure to save configurations.").queue()
}

@Command(ArgumentType.String, ArgumentType.Integer, ArgumentType.String)
fun move(event: GuildMessageReceivedEvent, args: List<Any>) {
    val targets = getTargets((args[0] as String))
    val searchSpace = args[1] as Int
    val chan = args[2] as String

    event.message.delete().queue()

    if (searchSpace < 0) {
        event.channel.sendMessage("... move what").queue()
        return
    }

    if(searchSpace > 99) {
        event.channel.sendMessage("Yea buddy, I'm not moving the entire channel into another, 99 messages or less").queue()
        return
    }

    val channel = event.guild.textChannels.filter { it.id == chan }.first()

    if (channel == null) {
        event.channel.sendMessage("... to where?").queue()
        return
    }

    event.channel.history.retrievePast(searchSpace + 1).queue {
        handleResponse(it, channel, targets, event.channel, event.author.asMention)
    }
}

@Command(ArgumentType.UserID, ArgumentType.Joiner)
fun badname(event: GuildMessageReceivedEvent, args: List<Any>) {
    val target = args[0] as String
    val reason = args[1] as String
    val targetMember = event.guild.getMemberById(target)

    event.guild.controller.setNickname(targetMember, MessageService.getMessage(MessageType.Name)).queue {
        targetMember.user.openPrivateChannel().queue {
            it.sendMessage("Your name has been changed forcefully by a member of staff for reason: $reason").queue()
        }
    }
}

private fun handleResponse(past: List<Message>, channel: TextChannel, targets: List<String>, error: TextChannel,
                           source: String) {
    val messages = past.subList(1, past.size).filter { targets.contains(it.author.id) }
    if (messages.isEmpty()) {
        error.sendMessage("No messages found").queue()
        return
    }

    val response = messages
            .map { "${it.author.asMention} said ${it.rawContent}" }
            .reduce { a, b -> "$a\n$b" }

    channel.sendMessage("==Messages moved from ${channel.name} to here by $source\n$response")
            .queue{ messages.forEach { it.delete().queue() }}
}

private fun getTargets(msg: String): List<String> =
        if (msg.contains(",")) {
            msg.split(",")
        } else {
            listOf(msg)
        }
