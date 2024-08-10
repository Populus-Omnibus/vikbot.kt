import org.gradle.api.Project
import java.util.*

val Project.fullName: String
    get() = "${rootProject.name}-${project.name}"

fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

val Project.versionString: String
    get() = project.version as? String ?: project.version.toString()

val Project.groupString: String
    get() = project.group as? String ?: project.group.toString()


object Repository {
    val projectUser = "Populus-Omnibus"
    val projectRepo = "vikbot.kt"
    val projectBaseUri = "github.com/$projectUser/$projectRepo"
    val projectUrl = "https://$projectBaseUri"
}

