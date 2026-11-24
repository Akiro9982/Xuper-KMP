# Actualizaciones de Xuper

Xuper se distribuye y se actualiza desde GitHub Releases. La app consulta el
repositorio oficial, elige el release de **su propio canal** y, si hay una versión
más nueva, la descarga, verifica su integridad y abre el instalador del sistema.
No hay servidores intermedios ni telemetría.

Repositorio: <https://github.com/Akiro9982/Xuper-KMP>

> Guía operativa para agentes de IA: [`AGENTS.md`](../AGENTS.md).

---

## Canales

Cada plataforma tiene su etiqueta y su archivo:

| Canal | Etiqueta | Archivo |
|---|---|---|
| Escritorio | `desktop-vX.Y.Z` | `.msi` |
| Android | `mobile-vX.Y.Z` | `.apk` |
| Android TV | `tv-vX.Y.Z` | `.apk` |

La app **solo mira las etiquetas de su canal**. Un release `mobile-v0.2.1` no
aparece como actualización en escritorio ni en TV, aunque sea más nuevo. Así un
arreglo exclusivo de móviles no molesta a las demás plataformas.

También se puede publicar una etiqueta global `vX.Y.Z`. En ese caso el workflow
compara las rutas modificadas desde el release anterior y publica únicamente los
canales afectados.

### Qué rutas afectan a cada canal

| Ruta modificada | Canales |
|---|---|
| `app-mobile/` | Android |
| `app-tv/` | Android TV |
| `desktop/` | Escritorio |
| `core/` | Los tres |
| `gradle/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | Los tres |
| `docs/`, `.github/`, `scripts/`, Markdown de la raíz (`README.md`, `AGENTS.md`, etc.), `version.properties` | Ninguno |

Cualquier ruta de código no reconocida se considera global por seguridad: es mejor
avisar de más que dejar una app sin un arreglo importante.

---

## Cómo comprueba la app

- Al arrancar se hace una comprobación silenciosa (`checkIfIdle`).
- En **Ajustes → Actualizaciones** se puede comprobar a mano, ver el changelog
  completo y descargar.
- La versión instalada se compara con la última del canal usando versión
  semántica (`major.minor.patch`).
- Se descarga el primer asset con la extensión correcta (`.msi` o `.apk`).
- Se verifica el **SHA-256** publicado por GitHub (`digest` del asset o el archivo
  `.sha256` adjunto). En escritorio es obligatorio; en Android/TV se usa cuando
  existe y además se valida la firma.
- En Android/TV se comprueba que el APK sea de este mismo paquete y que esté
  firmado con el **mismo certificado** que la app instalada. Si algo no coincide,
  se rechaza antes de abrir el instalador.
- El instalador solo se abre después de verificar. No hay instalación silenciosa.

Estados que muestra la interfaz: comprobando, al día, disponible, descargando
(con progreso), listo, instalando, permiso requerido, instalador abierto y error.

---

## Publicar una versión

### 1. Subir la versión

La fuente de verdad es `version.properties`:

```properties
app.versionName=0.2.0
```

Se puede editar a mano o usar el script:

```bash
python scripts/bump_version.py --patch   # 0.2.0 -> 0.2.1
python scripts/bump_version.py --minor   # 0.2.1 -> 0.3.0
python scripts/bump_version.py 0.4.0     # versión exacta
```

El `versionCode` de Android se calcula solo:

```text
major * 1_000_000 + minor * 1_000 + patch
```

Por eso `0.2.0` produce `2000` y siempre crece al subir la versión. No hay que
tocarlo a mano.

### 2. Etiquetar

```bash
git add version.properties
git commit -m "[UPDATE]: Prepara la versión 0.2.1"
git push origin main
# espera a que CI de main termine en verde
git tag v0.2.1
git push origin v0.2.1
```

La etiqueta debe coincidir con `version.properties` y apuntar al **último commit
de `main`**. Si no coincide, el workflow falla antes de compilar para evitar
releases con la versión equivocada o con código antiguo. Si es la primera release
y no hay una etiqueta anterior, se publican los tres canales.

Opciones:

- `vX.Y.Z`: publica solo los canales afectados por las rutas cambiadas.
- `mobile-vX.Y.Z`: fuerza **solo Android**, pero exige que `app-mobile/` o
  `core/` haya cambiado. Si el cambio solo afecta a TV, este tag falla.
- `tv-vX.Y.Z`: fuerza **solo Android TV**, con la misma comprobación.
- `desktop-vX.Y.Z`: fuerza **solo escritorio**, con la misma comprobación.

Las etiquetas de canal se pueden empujar por separado, por ejemplo para corregir
solo el APK de Android TV sin volver a publicar escritorio. El workflow manual
(`Run workflow`) permite forzar un canal sin esa comprobación; úsalo solo con
revisión y cuando sepas que el cambio no afecta a las demás apps.

### 3. Ejecución manual

En **Actions → Release → Run workflow** se puede elegir:

- `channel = auto`: calcula canales por rutas desde la etiqueta anterior. Si no
  hay cambios de código, falla; no publica una release vacía.
- `channel = all`: publica los tres canales (solo si de verdad quieres
  republicar).
- `channel = desktop`, `mobile` o `tv`: fuerza uno; úsalo con revisión.
- `version`: vacío usa `version.properties`; si se indica, debe tener formato
  `X.Y.Z` y coincidir con `version.properties`.

---

## Qué hace el workflow

`.github/workflows/release.yml`:

1. Calcula versión, etiqueta y canales afectados.
2. Genera un changelog distinto para cada canal a partir de los commits.
3. Compila los APK de Android y Android TV en firme si esos canales están
   seleccionados.
4. Compila el MSI de escritorio en Windows con `jpackage` y
   `perUserInstall = true`.
5. Verifica y renombra los artefactos:
   - `xuper-mobile-X.Y.Z.apk`
   - `xuper-tv-X.Y.Z.apk`
   - `xuper-desktop-X.Y.Z.msi`
   - cada uno con su `.sha256`.
6. Crea un **draft** por canal (`mobile-vX.Y.Z`, etc.), sube sus archivos y
   solo entonces lo publica.

El release de cada canal contiene solo su artefacto. La app filtra por etiqueta y
por extensión, así que nunca descarga el MSI desde Android ni el APK desde
escritorio. Una release ya publicada no se modifica: si hay que corregir algo, se
sube la versión y se publica una etiqueta nueva.

---

## Changelog

El changelog se genera de los mensajes de commit y se agrupa:

- `[ADD]`, `feat:`, `[NUEVO]` → **Novedades**
- `[FIX]`, `fix:`, `[ARREGLO]` → **Correcciones**
- `[UPDATE]`, `perf:`, `refactor:`, `[MEJORA]` → **Mejoras**
- `[SECURITY]`, `security:` → **Seguridad**
- `[REMOVE]`, `remove:` → **Eliminado**
- otros → **Otros cambios**

Los commits de documentación, CI, tests y mantenimiento no se listan. Las rutas
de cada commit se usan para que un changelog de Android no incluya un cambio que
solo existe en escritorio.

El workflow también genera un resumen en la página del release y la app muestra
ese texto limpio en **Ajustes → Actualizaciones**.

---

## Firma de Android

Android exige que la actualización esté firmada con el **mismo certificado** que la
app instalada. Si se pierde la clave, los usuarios tendrán que desinstalar y
volver a instalar (perdiendo los datos locales).

Crear una clave de release (solo la primera vez):

```bash
keytool -genkeypair -v -keystore xuper-release.jks -alias xuper \
  -keyalg RSA -keysize 4096 -validity 10000
```

Configurar estos secretos en GitHub (**Settings → Secrets and variables →
Actions**):

| Secreto | Contenido |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | El `.jks` codificado en base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `ANDROID_KEY_ALIAS` | Alias, por ejemplo `xuper` |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave |

En Linux/macOS:

```bash
base64 -w0 xuper-release.jks > keystore.b64
```

En Windows (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("xuper-release.jks")) |
  Set-Content -Encoding ascii keystore.b64
```

El workflow decodifica el keystore a `xuper-release.jks`, escribe
`keystore.properties` y compila `assembleRelease`. Si falta algún secreto y el
release incluye Android, falla a propósito: no se publica un APK firmado con la
clave de depuración.

---

## Escritorio: instalación por usuario

El MSI se genera con:

```kotlin
windows {
    perUserInstall = true
    dirChooser = false // fija %LOCALAPPDATA%\Xuper
}
```

Eso hace que Windows instale en el perfil del usuario
(`%LOCALAPPDATA%\Xuper`) y **no pida permisos de administrador**.

La actualización:

1. Descarga el MSI en `%LOCALAPPDATA%\Xuper\updates`.
2. Verifica tamaño y SHA-256.
3. Escribe `install-update.cmd`, que espera a que termine el proceso actual.
4. Ejecuta `msiexec /i ... /passive /norestart` (sin UAC en modo por usuario).
5. Vuelve a abrir Xuper y borra el script.

El log de instalación queda en `%LOCALAPPDATA%\Xuper\updates\install.log`.

En modo desarrollo (`gradlew run`) no existe `jpackage.app-path`: la app muestra
un aviso y no intenta instalar. Para probar el actualizador hay que usar el MSI
instalado. Si tenías una instalación antigua para todos los usuarios, desinstálala
antes de instalar el MSI por usuario para no acabar con dos copias.

El MSI no está firmado con Authenticode. Windows puede mostrar SmartScreen al
ejecutarlo manualmente; la app verifica el SHA-256 antes de lanzarlo. Si se
configura un certificado de code signing, se puede añadir al workflow más
adelante sin cambiar el cliente.

---

## Android y Android TV

- Permiso `REQUEST_INSTALL_PACKAGES` en el manifiesto.
- Descarga en la caché privada: no hace falta permiso de almacenamiento.
- `FileProvider` (`${applicationId}.fileprovider`) comparte el APK con el
  instalador del sistema.
- Android pide una vez permiso para instalar apps desconocidas; la app abre los
  ajustes correspondientes y permite reintentar.
- El APK se verifica contra el paquete y la firma instalados antes de abrirlo.

---

## Pruebas locales

```bash
# Lógica de versiones, canales, checksums y markdown
./gradlew :core:jvmTest --tests "com.xuperclean.core.update.*"

# APK de depuración
./gradlew :app-mobile:assembleDebug :app-tv:assembleDebug

# MSI (requiere JDK completo con jpackage y WiX 3; Compose lo descarga)
./gradlew :desktop:packageMsi
```

Para validar los scripts de release sin publicar:

```bash
# Fuerza todos los canales; no exige cambios desde el último tag
python scripts/affected_channels.py --channel all --version 0.2.1
python scripts/release_notes.py --version 0.2.1 --channel mobile
```

---

## Problemas frecuentes

**La app dice que está al día pero hay una versión nueva.**
Comprueba que el release no sea borrador ni prerelease, que la etiqueta tenga el
prefijo del canal (`mobile-v...`) y que incluya un asset con la extensión
correcta. También puede ser el límite de la API de GitHub (60 consultas por hora
y IP sin autenticar).

**Android rechaza la instalación por firma.**
El APK se firmó con otra clave. Usa siempre el mismo keystore; si se perdió, hay
que desinstalar la app e instalar la versión oficial (se pierden los datos
locales). Si vienes de un APK de depuración, desinstálalo también antes de poner
el release firmado: Android no deja actualizar entre firmas distintas.

**Android no abre el instalador.**
Concede «instalar apps desconocidas» a Xuper y vuelve a pulsar Instalar. En
televisores, comprueba que el instalador de paquetes del sistema esté disponible.

**Escritorio dice que la actualización solo está en la versión instalada.**
Estás ejecutando desde el IDE o con `gradlew run`. Compila e instala el MSI.

**El MSI no se instala o se queda abierto.**
Revisa `%LOCALAPPDATA%\Xuper\updates\install.log`; suele ser un archivo bloqueado
por una instancia anterior, antivirus o una instalación en curso.

**El workflow falla porque el tag no está en el último commit de `main`.**
El workflow exige que el commit etiquetado sea el HEAD actual de `main`. Si
`main` avanzó después de crear el tag, no muevas el tag anterior: sube la versión
y crea un tag nuevo sobre el HEAD actual.

**El workflow falla porque el tag no coincide con las rutas.**
Un tag `mobile-v...` solo es válido si el cambio afecta a `app-mobile/` o a
`core/`. Usa el tag global `vX.Y.Z` o corrige el tag; no lo fuerces con
`git push --force`.

**El workflow falla en «Calcular versión y canales».**
La etiqueta no coincide con `version.properties`, no hay cambios de código o el
tag no tiene un formato válido. Si el run falló antes de `publish` y todavía no
existe ninguna release, corrige en un commit nuevo, sube la versión y crea una
etiqueta nueva. Con la protección de tags activada no se puede borrar ni recrear
la etiqueta vieja. Si la release ya existe, sube la versión y publica una etiqueta
nueva. No muevas una etiqueta publicada ni uses `git push --force` sobre etiquetas.

---

## Protecciones de GitHub

El workflow ya valida la versión, el canal, el commit y la inmutabilidad del
release. Para cerrar el resto hace falta configurar reglas en GitHub, porque un
agente con permiso de escritura podría intentar mover una etiqueta o reescribir
`main`.

### Etiquetas

En **Settings → Rules → Rulesets**, crea un ruleset para tags:

- Target: los patrones `v*`, `mobile-v*`, `tv-v*` y `desktop-v*`.
- Activa el bloqueo de eliminación y de actualización/force push de etiquetas.
- No bloquees la creación: el workflow crea las etiquetas de canal.
- No añadas agentes ni cuentas de automatización a la lista de bypass.

Así una etiqueta no se puede borrar, mover ni reutilizar. Si un
release falla antes de publicar, comprueba que no exista release, sube la versión
y crea una etiqueta nueva; no intentes borrar ni recrear la etiqueta protegida.

### Rama main

Crea un ruleset para `main` que bloquee force pushes y la eliminación de la
rama. Para la garantía máxima, activa también «Require a pull request before
merging» y revisa los cambios de `.github/workflows/` y `scripts/` antes de
mergear. Sin esa revisión, un agente podría modificar el propio workflow.

### Entorno de publicación

El job `publish` usa el entorno `release`. Si quieres que ninguna release se
publique sin aprobación humana, en **Settings → Environments → release** añade
revisores obligatorios y permite los tags `v*`, `mobile-v*`, `tv-v*` y
`desktop-v*` en «Deployment branches and tags». Si no lo configuras, el entorno
no bloquea nada y las releases siguen siendo automáticas.

Si GitHub ofrece **release immutability** en la configuración del repositorio,
actívala: impide editar o borrar una release publicada incluso fuera del workflow.

Estas reglas no se pueden aplicar desde el repositorio: son ajustes de GitHub y
hay que configurarlas una vez. Para que sirvan, un agente debe usar una cuenta con
permiso **Write**, nunca **Admin**; un administrador puede cambiar las reglas.
La guía para agentes está en `AGENTS.md`.

---

## Seguridad

- Solo se aceptan descargas cuyo host inicial sea `https://github.com/`.
- Se verifica SHA-256 y, en Android, la firma del APK.
- El instalador se lanza después de verificar; no hay ejecución silenciosa.
- No se guardan credenciales ni telemetría de actualizaciones.
- Las claves privadas viven solo en los secretos de GitHub, nunca en el
  repositorio.