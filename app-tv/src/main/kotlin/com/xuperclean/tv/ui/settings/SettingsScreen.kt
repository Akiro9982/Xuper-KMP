package com.xuperclean.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.xuperclean.core.update.AppUpdate
import com.xuperclean.core.update.UpdateController
import com.xuperclean.core.update.UpdateState
import com.xuperclean.core.update.formatBytes
import com.xuperclean.tv.ui.theme.tvCardColors
import com.xuperclean.tv.ui.theme.tvFocusBorder
import com.xuperclean.tv.update.AppUpdates

/** Ajustes de la app TV, con la sección de actualizaciones y el changelog. */
@Composable
fun SettingsScreen() {
    val controller = AppUpdates.current
    val estado by controller.state.collectAsState()
    LaunchedEffect(Unit) { controller.checkIfIdle() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 40.dp, top = 30.dp, end = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Text(
                    "Ajustes",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Versión ${controller.currentVersion.name} · ${controller.channel.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { UpdatesCard(controller, estado) }
        item { AboutCard(controller) }
    }
}

@Composable
private fun UpdatesCard(controller: UpdateController, estado: UpdateState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Actualizaciones", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Desde GitHub Releases, solo para Android TV",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Estado(controller, estado)

            val ocupado = estado is UpdateState.Checking ||
                estado is UpdateState.Downloading ||
                estado is UpdateState.Installing
            TvActionButton(
                label = if (estado is UpdateState.Checking) "Buscando…" else "Buscar actualizaciones",
                onClick = { if (!ocupado) controller.check(force = true) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Estado(controller: UpdateController, estado: UpdateState) {
    when (estado) {
        UpdateState.Idle -> Text(
            "La app comprueba al abrirse si hay una versión nueva publicada para este canal.",
            style = MaterialTheme.typography.bodyMedium,
        )

        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Buscando actualizaciones…", style = MaterialTheme.typography.bodyMedium)
        }

        is UpdateState.UpToDate -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
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
            TvActionButton(
                label = "Descargar e instalar",
                onClick = { controller.downloadAndInstall() },
                modifier = Modifier.fillMaxWidth(),
            )
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
            TvActionButton(
                label = "Instalar",
                onClick = { controller.installDownloaded() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Abriendo el instalador…", style = MaterialTheme.typography.bodyMedium)
        }

        is UpdateState.InstallLaunched -> Text(
            "El instalador de Android está abierto. Confirma la instalación y vuelve a abrir Xuper.",
            style = MaterialTheme.typography.bodyMedium,
        )

        is UpdateState.PermissionRequired -> {
            Text(estado.message, style = MaterialTheme.typography.bodyMedium)
            TvActionButton(
                label = "Ya concedí el permiso, instalar",
                onClick = { controller.installDownloaded() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is UpdateState.Failed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    estado.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (estado.update != null) {
                TvActionButton(
                    label = "Reintentar descarga",
                    onClick = { controller.downloadAndInstall() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Changelog(update: AppUpdate, titulo: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            titulo,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            update.notes.ifBlank { "Este release no incluye notas de cambios." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Release ${update.tag} en GitHub",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutCard(controller: UpdateController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text("Acerca de", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Xuper ${controller.currentVersion.name} (${controller.channel.label})",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Cliente libre y multiplataforma. Las actualizaciones se consultan y descargan únicamente de los releases oficiales de github.com/Akiro9982/Xuper-KMP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = tvCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = tvFocusBorder(),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
