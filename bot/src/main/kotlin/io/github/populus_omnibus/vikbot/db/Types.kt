package io.github.populus_omnibus.vikbot.db

import kotlinx.serialization.Serializable

enum class VoiceChannelType {
    VoiceRequest, Temp
}


enum class MessageLoggingLevel {
    NONE, DELETED, ANY
}
