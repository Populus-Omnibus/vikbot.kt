package io.github.populus_omnibus.vikbot.api.annotations

@Target(AnnotationTarget.CLASS)
annotation class Command(
    val global: Boolean = true
)

@Target(AnnotationTarget.FUNCTION)
annotation class Module
