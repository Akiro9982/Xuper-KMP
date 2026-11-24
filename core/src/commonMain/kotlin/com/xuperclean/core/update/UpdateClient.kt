package com.xuperclean.core.update

import com.xuperclean.core.net.HttpEngine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Consulta los releases de GitHub y elige el último del canal correspondiente.
 *
 * Cada plataforma publica con su propia etiqueta (`mobile-v0.2.0`, `tv-v0.2.0`,
 * `desktop-v0.2.0`). La app sólo mira las etiquetas de su canal, así que un arreglo
 * exclusivo de móvil no se ofrece como actualización en escritorio ni en TV.
 */
public class UpdateClient(
    private val http: HttpEngine,
    private val repository: String = UpdateConfig.repository,
    private val apiBase: String = UpdateConfig.apiBase,
    private val userAgent: String = UpdateConfig.userAgent,
) {

    public suspend fun check(channel: UpdateChannel, current: AppVersion): UpdateCheck {
        val release = fetchReleases()
            .asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { r ->
                val version = AppVersion.fromTag(r.tagName, channel.tagPrefix) ?: return@mapNotNull null
                val asset = r.assets.firstOrNull {
                    it.name.endsWith(channel.assetExtension, ignoreCase = true)
                } ?: return@mapNotNull null
                Triple(version, r, asset)
            }
            .maxByOrNull { it.first }

        if (release == null) {
            return UpdateCheck(channel, current, latest = null, isNewer = false)
        }

        val (version, gh, asset) = release
        val checksum = checksumFor(gh, asset)
        val update = AppUpdate(
            channel = channel,
            version = version,
            tag = gh.tagName,
            title = gh.name.ifBlank { "Xuper ${channel.label} ${version.name}" },
            notes = ReleaseNotes.clean(gh.body),
            publishedAt = gh.publishedAt.orEmpty(),
            releaseUrl = gh.htmlUrl,
            asset = UpdateAsset(
                name = asset.name,
                downloadUrl = asset.downloadUrl,
                size = asset.size,
                sha256 = checksum,
            ),
        )
        return UpdateCheck(channel, current, update, isNewer = version > current)
    }

    private suspend fun fetchReleases(): List<GitHubRelease> {
        val url = "$apiBase/repos/$repository/releases?per_page=${UpdateConfig.releasesPerPage}"
        val response = try {
            http.get(
                url,
                mapOf(
                    "Accept" to "application/vnd.github+json",
                    "X-GitHub-Api-Version" to "2022-11-28",
                    "User-Agent" to userAgent,
                ),
            )
        } catch (e: Exception) {
            throw UpdateException("No se pudo conectar con GitHub: ${e.message ?: "sin detalle"}", e)
        }

        when (response.status) {
            200 -> Unit
            403, 429 -> throw UpdateException(
                "GitHub limitó temporalmente las consultas. Inténtalo de nuevo en unos minutos.",
            )
            404 -> throw UpdateException("No se encontró el repositorio $repository.")
            else -> throw UpdateException("GitHub respondió HTTP ${response.status}.")
        }

        return try {
            JSON.decodeFromString<List<GitHubRelease>>(response.bodyText)
        } catch (e: Exception) {
            throw UpdateException("La respuesta de GitHub no se pudo interpretar.", e)
        }
    }

    /**
     * GitHub publica `digest` desde 2024. Si no viene (releases antiguos), se usa el
     * archivo `.sha256` que genera el workflow junto al APK/MSI.
     */
    private suspend fun checksumFor(gh: GitHubRelease, asset: GitHubAsset): String? {
        val digest = asset.digest?.substringAfter("sha256:", "")?.trim()?.lowercase()
        if (digest != null && digest.matches(SHA256)) return digest

        val sidecar = gh.assets.firstOrNull { it.name == "${asset.name}.sha256" } ?: return null
        return try {
            val res = http.get(sidecar.downloadUrl, mapOf("User-Agent" to userAgent))
            if (res.status !in 200..299) return null
            res.bodyText.trim().substringBefore(' ').trim().lowercase().takeIf { it.matches(SHA256) }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val SHA256 = Regex("""^[0-9a-f]{64}$""")
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

public class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    val digest: String? = null,
)

public object UpdateConfig {
    public const val repository: String = "Akiro9982/Xuper-KMP"
    public const val apiBase: String = "https://api.github.com"
    public const val releasesPerPage: Int = 100
    public const val userAgent: String = "Xuper-KMP-Update"
    public const val projectUrl: String = "https://github.com/Akiro9982/Xuper-KMP"
    public const val releasesUrl: String = "https://github.com/Akiro9982/Xuper-KMP/releases"
}
