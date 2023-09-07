import org.gradle.api.Project
import java.util.*

val Project.fullName: String
    get() = "${rootProject.name}-${project.name}"

fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

object Repository {

}
