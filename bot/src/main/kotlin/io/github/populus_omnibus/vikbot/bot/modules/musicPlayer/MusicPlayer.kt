package io.github.populus_omnibus.vikbot.bot.modules.musicPlayer

import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import java.util.*

class MusicPlayer(
    val channel: VoiceChannel,
    val playlist: MutableList<MusicTrack> = mutableListOf(),
    val currentTrack: MusicTrack? = null,
    val timer: Timer = Timer(),
) {
}