package app.nastavnik.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Свежая сборка в GitHub Releases: номер, ссылка на APK, размер и название. */
data class UpdateInfo(
    val build: Int,
    val apkUrl: String,
    val sizeBytes: Long,
    val title: String,
    val version: String = "",
)

/** Разбор ответа GitHub API `releases/latest`. Без Android — проверяется тестами. */
object UpdateCheck {
    const val REPO = "Ee1evch/Nastavnik"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    fun parseSemver(v: String): Triple<Int, Int, Int>? {
        val m = Regex("""v?(\d+)\.(\d+)(?:\.(\d+))?""").find(v.trim()) ?: return null
        val maj = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val patch = m.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        return Triple(maj, min, patch)
    }

    private fun compareSemver(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        if (a.second != b.second) return a.second.compareTo(b.second)
        return a.third.compareTo(b.third)
    }

    /** Обновление, если релиз новее [currentBuild] или [currentVersionName] и в нём есть APK; иначе null. */
    fun parse(
        body: String,
        currentBuild: Int,
        apkName: String = "nastavnik.apk",
        currentVersionName: String = "",
    ): UpdateInfo? {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val tag = obj.str("tag_name") ?: return null
        val name = obj.str("name").orEmpty()

        val releaseSemver = parseSemver(tag) ?: parseSemver(name)
        val currentSemver = parseSemver(currentVersionName)

        val cmp = if (releaseSemver != null && currentSemver != null) {
            compareSemver(releaseSemver, currentSemver)
        } else null

        val isNewer = when {
            cmp != null && cmp > 0 -> true
            cmp != null && cmp < 0 -> false
            else -> {
                val build = Regex("""(\d+)$""").find(tag)?.value?.toIntOrNull()
                if (build != null && build > currentBuild) {
                    true
                } else if (cmp == null && releaseSemver != null) {
                    val tagBuild = releaseSemver.first * 10000 + releaseSemver.second * 100 + releaseSemver.third
                    tagBuild > currentBuild
                } else {
                    false
                }
            }
        }

        if (!isNewer) return null

        // В релизе несколько APK — «Наставник», «Студент» и приложение для родителя; нам — только свой.
        val apks = (obj["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.filter { it.str("name")?.endsWith(".apk") == true }.orEmpty()
        val asset = apks.firstOrNull { it.str("name") == apkName } ?: return null
        val url = asset.str("browser_download_url") ?: return null
        if (!url.startsWith("https://github.com/$REPO/")) return null

        val buildNumber = Regex("""(\d+)$""").find(tag)?.value?.toIntOrNull()
            ?: releaseSemver?.let { it.first * 10000 + it.second * 100 + it.third }
            ?: (currentBuild + 1)

        val title = name.takeIf { it.isNotBlank() } ?: tag

        return UpdateInfo(
            build = buildNumber,
            apkUrl = url,
            sizeBytes = asset.str("size")?.toLongOrNull() ?: 0,
            title = title,
            version = tag.removePrefix("v").trim(),
        )
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}
