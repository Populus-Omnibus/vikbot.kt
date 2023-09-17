package io.github.populus_omnibus.vikbot.bot

import io.github.populus_omnibus.vikbot.VikBotHandler
import io.github.populus_omnibus.vikbot.api.annotations.Command
import io.github.populus_omnibus.vikbot.api.annotations.CommandType
import io.github.populus_omnibus.vikbot.api.annotations.Module
import io.github.populus_omnibus.vikbot.api.commands.SlashCommand
import org.slf4j.kotlin.error
import org.slf4j.kotlin.getLogger
import org.slf4j.kotlin.info
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.lang.reflect.Modifier


/**
 * # Stop!
 * Module loader is an annotation based loader using jvm reflect, **it does not have static type-check**.
 *
 * To maintain a quasi-static type check, all modules will be loaded or exception is thrown. Do not edit the following code unless you understand all of this file.
 *
 * Modifying it can easily lead to bugs or performance issues.
 *
 * If dynamic loading is necessary, use KSP (annotation processing) to check and cache
 */
object ModuleLoader {
    private val logger by getLogger()

    operator fun invoke(handler: VikBotHandler) {
        val classes = listClassesRecursive().toSet()
        logger.info { "Found ${classes.size} classes, filtering and loading..." }

        classes.forEach {

            val instance by lazy { it.kotlin.objectInstance ?: it.getConstructor().newInstance() }

            when {
                // Slash command type
                it.hasAnnotation<Command>() -> {
                    val cmd = it.getAnnotationsByType<Command>().first()

                    if(instance is SlashCommand) {
                        logger.info { "loading command ${instance::class.simpleName}" }
                        when (cmd.type) {
                            CommandType.GLOBAL -> handler.globalCommands += instance as SlashCommand
                            CommandType.SERVER -> handler.serverCommands += instance as SlashCommand
                            CommandType.OWNER -> handler.ownerServerCommands += instance as SlashCommand
                        }
                    } else {
                        error("Module ${instance::class.simpleName} should be SlashCommand")
                    }
                }

            }


            it.methods.filter { method: Method -> method.hasAnnotation<Module>() }.forEach { method ->
                logger.info { "Using module ${it.simpleName}#${method.name}" }
                try {
                    if (Modifier.isStatic(method.modifiers)) {
                        method.invoke(null, handler)
                    } else {
                        logger.info("Module is non-static")
                        method.invoke(instance, handler)
                    }
                } catch (e: ReflectiveOperationException) {
                    logger.error(e) { "Failed to load module: ${it.simpleName}.${method.name}" }
                    throw e
                }
            }
        }
    }

    private val modulesPackage = "${ModuleLoader::class.java.packageName}.modules"

    // recursive
    private fun listClassesRecursive(`package`: String = modulesPackage): Sequence<Class<*>> {
        logger.info { "Listing entries in $`package`" }

        val fileStream = ClassLoader.getSystemClassLoader().getResourceAsStream(`package`.replace(".", "/"))!!
        val reader = BufferedReader(InputStreamReader(fileStream))

        return reader.lineSequence().flatMap { file ->
            if (file.endsWith(".class")) {
                return@flatMap sequenceOf(getClass(file, `package`))
            } else {
                return@flatMap listClassesRecursive("$`package`.$file")
            }
        }
    }
    private fun getClass(className: String, packageName: String): Class<*> {
        try {
            return Class.forName(
                packageName + "."
                        + className.substring(0, className.lastIndexOf('.'))
            )
        } catch (e: ClassNotFoundException) {
            logger.error { "Class lookup failed for $className in $packageName" }
        }
        error("getClass")
    }


    private inline fun <reified T: Annotation> Class<*>.hasAnnotation(): Boolean {
        return this.isAnnotationPresent(T::class.java)
    }

    private inline fun <reified T: Annotation> Class<*>.getAnnotationsByType(): Array<T> {
        return this.getAnnotationsByType(T::class.java)
    }

    private inline fun <reified T: Annotation> Method.hasAnnotation(): Boolean {
        return this.isAnnotationPresent(T::class.java)
    }
}