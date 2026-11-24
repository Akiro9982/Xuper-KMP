package com.xuperclean.core.update

import com.xuperclean.core.net.HttpEngine
import com.xuperclean.core.net.HttpResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateLogicTest {

    private val sha = "a".repeat(64)

    private fun releasesJson(channelMobile: Boolean = true): String {
        val apk = "xuper-mobile-0.2.1.apk"
        val tvApk = "xuper-tv-0.2.1.apk"
        val msi = "xuper-desktop-0.2.0.msi"
        return """
        [
          {
            "tag_name": "mobile-v0.2.1",
            "name": "Android 0.2.1",
            "body": "## Novedades\n- **Arreglo** de móvil\n<!-- xuper-release channel=mobile -->",
            "draft": false,
            "prerelease": false,
            "published_at": "2026-02-01T10:00:00Z",
            "html_url": "https://github.com/Akiro9982/Xuper-KMP/releases/tag/mobile-v0.2.1",
            "assets": [
              {"name": "$apk", "browser_download_url": "https://example.com/$apk", "size": 24000000, "digest": "sha256:$sha"},
              {"name": "$apk.sha256", "browser_download_url": "https://example.com/checksum", "size": 65}
            ]
          },
          {
            "tag_name": "mobile-v0.3.0",
            "name": "Borrador",
            "body": "no debe salir",
            "draft": true,
            "prerelease": false,
            "published_at": "2026-03-01T10:00:00Z",
            "html_url": "https://example.com/draft",
            "assets": [
              {"name": "$apk", "browser_download_url": "https://example.com/draft.apk", "size": 1, "digest": "sha256:$sha"}
            ]
          },
          {
            "tag_name": "tv-v0.2.1",
            "name": "TV 0.2.1",
            "body": "TV",
            "draft": false,
            "prerelease": false,
            "published_at": "2026-02-01T10:00:00Z",
            "html_url": "https://example.com/tv",
            "assets": [
              {"name": "$tvApk", "browser_download_url": "https://example.com/$tvApk", "size": 24000000}
            ]
          },
          {
            "tag_name": "desktop-v0.2.0",
            "name": "Escritorio 0.2.0",
            "body": "Escritorio",
            "draft": false,
            "prerelease": false,
            "published_at": "2026-02-01T10:00:00Z",
            "html_url": "https://example.com/desktop",
            "assets": [
              {"name": "$msi", "browser_download_url": "https://example.com/$msi", "size": 100000000}
            ]
          }
        ]
        """.trimIndent()
    }

    private class FakeEngine(
        private val releases: String,
        private val checksum: String? = null,
    ) : HttpEngine {
        var calls = 0
        override suspend fun post(url: String, headers: Map<String, String>, body: ByteArray): HttpResult =
            error("no usado")

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            calls++
            return if (url.startsWith("https://api.github.com")) {
                HttpResult(200, emptyMap(), releases.encodeToByteArray())
            } else {
                val texto = checksum ?: ""
                HttpResult(200, emptyMap(), "$texto  $url\n".encodeToByteArray())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reintentaLaDescargaTrasUnFallo() = runTest {
        val downloader = object : UpdateDownloader {
            var descargas = 0

            override suspend fun download(
                update: AppUpdate,
                onProgress: (downloaded: Long, total: Long) -> Unit,
            ): String {
                descargas++
                error("sin red")
            }

            override suspend fun install(update: AppUpdate, path: String): InstallResult =
                InstallResult.Started
        }
        val controller = UpdateController(
            client = UpdateClient(FakeEngine(releasesJson())),
            channel = UpdateChannel.MOBILE,
            currentVersion = AppVersion(0, 2, 0),
            downloader = downloader,
            scope = this,
        )

        controller.check()
        advanceUntilIdle()
        assertTrue(controller.state.value is UpdateState.Available)

        controller.downloadAndInstall()
        advanceUntilIdle()
        assertTrue(controller.state.value is UpdateState.Failed)

        // El botón Reintentar debe volver a descargar, no quedarse bloqueado.
        controller.downloadAndInstall()
        advanceUntilIdle()
        assertEquals(2, downloader.descargas)
    }

    @Test
    fun versionSeComparaYGeneraVersionCode() {
        assertEquals(0, AppVersion.parse("0.2.0")!!.compareTo(AppVersion(0, 2, 0)))
        assertTrue(AppVersion.parse("0.2.1")!! > AppVersion.parse("0.2.0")!!)
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
        assertNull(AppVersion.parse("0.2"))
        assertNull(AppVersion.parse("0.2.0-beta"))
        assertEquals(2000, AppVersion.parse("0.2.0")!!.versionCode)
        assertEquals(1, AppVersion.parse("0.0.1")!!.versionCode)
        assertEquals(AppVersion(1, 2, 3), AppVersion.fromTag("mobile-v1.2.3", "mobile-v"))
        assertNull(AppVersion.fromTag("tv-v1.2.3", "mobile-v"))
    }

    @Test
    fun soloLlegaLaVersionDelCanal() = runTest {
        val engine = FakeEngine(releasesJson())
        val client = UpdateClient(engine)

        val movil = client.check(UpdateChannel.MOBILE, AppVersion(0, 2, 0))
        assertTrue(movil.isNewer)
        assertEquals("0.2.1", movil.latest?.version?.name)
        assertEquals("xuper-mobile-0.2.1.apk", movil.latest?.asset?.name)
        assertEquals(sha, movil.latest?.asset?.sha256)
        assertEquals("Novedades\n• Arreglo de móvil", movil.latest?.notes)

        val tv = client.check(UpdateChannel.TV, AppVersion(0, 2, 0))
        assertTrue(tv.isNewer)
        assertEquals("xuper-tv-0.2.1.apk", tv.latest?.asset?.name)
        // La TV no publica digest: se lee el .sha256 del release (si existe).
        assertNull(tv.latest?.asset?.sha256)

        val escritorio = client.check(UpdateChannel.DESKTOP, AppVersion(0, 2, 0))
        assertFalse(escritorio.isNewer)
        val escritorioUpdate = assertNotNull(escritorio.latest)
        assertEquals("xuper-desktop-0.2.0.msi", escritorioUpdate.asset.name)
    }

    @Test
    fun usaElSidecarCuandoNoHayDigest() = runTest {
        val json = releasesJson().replace(", \"digest\": \"sha256:$sha\"", "")
        val engine = FakeEngine(json, checksum = sha)
        val client = UpdateClient(engine)
        val movil = client.check(UpdateChannel.MOBILE, AppVersion(0, 2, 0))
        assertEquals(sha, movil.latest?.asset?.sha256)
        assertEquals(2, engine.calls)
    }

    @Test
    fun limpiaElMarkdownDelChangelog() {
        val texto = ReleaseNotes.clean(
            """
            <!-- comentario interno -->
            ## 0.2.0
            - **Corregido** el *directo*
            - Enlace [repo](https://github.com/Akiro9982/Xuper-KMP)
            """.trimIndent(),
        )
        assertEquals(
            "0.2.0\n• Corregido el directo\n• Enlace repo (https://github.com/Akiro9982/Xuper-KMP)",
            texto,
        )
    }
}