package com.xuperclean.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xuperclean.core.update.AppUpdate
import com.xuperclean.core.update.UpdateConfig
import com.xuperclean.core.update.UpdateController
import com.xuperclean.core.update.UpdateState
import com.xuperclean.core.update.formatBytes
import com.xuperclean.desktop.update.AppUpdates
import java.awt.Desktop
import java.net.URI

/** Ajustes de escritorio: actualizaciones, changelog y acerca de. */
@Composable
fun SettingsScreen(onExit: () -> Unit) {
    val controller = AppUpdates.current
    val estado by controller.state.collectAsState()
    LaunchedEffect(Unit) { controller.checkIfIdle() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, top = 28.dp, end = 32.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Text("Ajustes", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Versión ${controller.currentVersion.name} · ${controller.channel.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { UpdatesCard(controller, estado, onExit) }
        item { AboutCard(controller) }
    }
}

@Composable
private fun UpdatesCard(controller: UpdateController, estado: UpdateState, onExit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Actualizaciones", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Desde GitHub Releases, solo para escritorio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Estado(controller, estado, onExit)

            val ocupado = estado is UpdateState.Checking ||
                estado is UpdateState.Downloading ||
                estado is UpdateState.Installing
            OutlinedButton(
                onClick = { controller.check(force = true) },
                enabled = !ocupado,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (estado is UpdateState.Checking) "Buscando…" else "Buscar actualizaciones")
            }
        }
    }
}

@Composable
private fun Estado(controller: UpdateController, estado: UpdateState, onExit: () -> Unit) {
    when (estado) {
        UpdateState.Idle -> Text(
            "La app comprueba al abrirse si hay una versión nueva publicada para escritorio.",
            style = MaterialTheme.typography.bodyMedium,
        )

        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Buscando actualizaciones…", style = MaterialTheme.typography.bodyMedium)
        }

        is UpdateState.UpToDate -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tienes la última versión (${controller.currentVersion.name}).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            estado.latest?.let { Changelog(it, "Novedades de la última versión publicada") }
        }

        is UpdateState.Available -> {
            Text(
                "Nueva versión ${estado.update.version.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Publicada el ${estado.update.publishedDateLabel()} · ${estado.update.asset.sizeLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Changelog(estado.update, "Changelog de ${estado.update.version.name}")
            if (AppUpdates.puedeInstalar) {
                Button(
                    onClick = { controller.downloadAndInstall() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Descargar e instalar") }
            } else {
                Text(
                    "Estás ejecutando Xuper en modo desarrollo. La instalación automática solo está " +
                        "disponible en la versión instalada con el MSI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { abrirNavegador(UpdateConfig.releasesUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Abrir releases en GitHub") }
            }
        }

        is UpdateState.Downloading -> {
            val fraccion = if (estado.total > 0) {
                (estado.downloaded.toFloat() / estado.total).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(progress = { fraccion }, modifier = Modifier.fillMaxWidth())
            Text(
                if (estado.total > 0) {
                    "Descargando ${estado.update.version.name}… ${formatBytes(estado.downloaded)} de ${formatBytes(estado.total)}"
                } else {
                    "Descargando ${estado.update.version.name}… ${formatBytes(estado.downloaded)}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is UpdateState.Ready -> {
            Text("Descarga verificada. Lista para instalar.", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { controller.installDownloaded() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Instalar") }
        }

        is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Preparando el instalador…", style = MaterialTheme.typography.bodyMedium)
        }

        is UpdateState.InstallLaunched -> {
            Text(
                "La actualización se aplicará cuando cierres Xuper y la app se volverá a abrir sola. " +
                    "Si Windows muestra un aviso de seguridad, confirma que el instalador es de Xuper.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar Xuper y actualizar")
            }
        }

        is UpdateState.PermissionRequired -> Text(
            estado.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        is UpdateState.Failed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    estado.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (estado.update != null && AppUpdates.puedeInstalar) {
                OutlinedButton(
                    onClick = { controller.downloadAndInstall() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reintentar descarga") }
            }
        }
    }
}

@Composable
private fun Changelog(update: AppUpdate, titulo: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            update.notes.ifBlank { "Este release no incluye notas de cambios." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { abrirNavegador(update.releaseUrl) }) {
            Text("Ver release en GitHub")
        }
    }
}

@Composable
private fun AboutCard(controller: UpdateController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Acerca de", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Xuper ${controller.currentVersion.name} (${controller.channel.label})",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Cliente libre y multiplataforma. Las actualizaciones se consultan y descargan " +
                    "únicamente de los releases oficiales del repositorio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Los MSI se guardan en %LOCALAPPDATA%\\Xuper\\updates y se instalan por usuario, " +
                    "sin pedir permisos de administrador.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { abrirNavegador(UpdateConfig.projectUrl) }) {
                Text("Código y releases en GitHub")
            }
        }
    }
}

private fun abrirNavegador(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}
