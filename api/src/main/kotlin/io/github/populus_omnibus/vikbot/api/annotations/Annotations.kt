package io.github.populus_omnibus.vikbot.api.annotations

@Target(AnnotationTarget.CLASS)
annotation class Command(
    val type: CommandType = CommandType.GLOBAL
)

enum class CommandType {
    /**
     * Register the command globally
     */
    GLOBAL,

    /**
     * Register the command only on servers
     */
    SERVER,

    /**
     * Register the command only on the owner server
     */
    OWNER,
}

@Target(AnnotationTarget.FUNCTION)
annotation class Module
