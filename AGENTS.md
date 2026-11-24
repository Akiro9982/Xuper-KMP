# Instrucciones para agentes de IA

Este repositorio publica actualizaciones reales. Un error con una etiqueta puede
dejar a usuarios sin actualizaciones o romper la actualización de Android. Antes
de preparar un release, lee `docs/actualizaciones.md` y esta guía.

## Reglas que no se negocian

- No commitees `xuper-release.jks`, `keystore.properties`, `keystore.b64` ni
  contraseñas. Los secretos viven solo en GitHub.
- No cambies ni regeneres la clave de firma Android. La misma clave debe firmar
  todas las versiones para siempre.
- No crees releases a mano en GitHub. El workflow `.github/workflows/release.yml`
  crea las releases por canal.
- No muevas, borres ni reutilices una etiqueta que ya tenga una release
  publicada. Si la release existe, sube la versión y crea otra etiqueta.
- No uses `git push --force` sobre etiquetas.
- No etiquetes un commit que no sea el último commit de `main` o que no haya
  pasado CI. El workflow rechaza tags sobre commits antiguos o de otras ramas.
- No publiques una release vacía: si solo cambiaste documentación, CI o
  `version.properties`, no hay canal afectado.
- No ejecutes `publish` manualmente ni subas assets a mano. Deja que el workflow
  verifique, firme, calcule el SHA-256 y publique.

## Fuente de verdad

- Versión: `version.properties` (`app.versionName`).
- Canales y rutas: `scripts/release_lib.py`.
- Cálculo de canales: `scripts/affected_channels.py`.
- Notas: `scripts/release_notes.py` y `release_notes()` en `release_lib.py`.
- Workflow: `.github/workflows/release.yml`.

Las notas se generan de forma determinista a partir de los mensajes de commit y
las rutas modificadas. No hay IA ni servicio externo. Un mensaje de commit claro
produce una nota clara.

## Canales

| Cambio en | Canal publicado con una etiqueta `vX.Y.Z` |
|---|---|
| `app-mobile/` | `mobile` |
| `app-tv/` | `tv` |
| `desktop/` | `desktop` |
| `app-mobile/` + `app-tv/` | `mobile` + `tv` |
| `core/` | los tres |
| `gradle/` o configuración raíz | los tres |
| `docs/`, `.github/`, `scripts/`, Markdown de la raíz (`README.md`, `AGENTS.md`, etc.), `version.properties` | ninguno |
| Ruta de código desconocida | los tres, por seguridad |

Las etiquetas de canal seleccionan un canal concreto, pero el workflow comprueba
que ese canal esté realmente afectado por los archivos modificados:

```text
vX.Y.Z          -> automático por rutas
mobile-vX.Y.Z   -> solo Android móvil, si app-mobile/ o core/ cambió
tv-vX.Y.Z       -> solo Android TV, si app-tv/ o core/ cambió
desktop-vX.Y.Z  -> solo escritorio, si desktop/ o core/ cambió
```

Si el tag de canal no coincide con las rutas, el workflow falla antes de compilar.
El workflow manual (`workflow_dispatch`) sí puede forzar un canal; úsalo solo con
revisión y cuando sepas que el cambio no afecta a las demás apps.

## Flujo canónico

1. Actualiza la versión:

```bash
python scripts/bump_version.py --patch
```

2. Revisa el diff y ejecuta las pruebas que correspondan:

```bash
git diff --check
./gradlew :core:jvmTest --tests "com.xuperclean.core.update.*"
```

3. Haz commit con un prefijo que el generador entienda:

```bash
# Añade solo los archivos del cambio; no uses `git add .` con secretos locales
git add version.properties
git add app-mobile app-tv desktop core
git commit -m "[FIX]: Describe el cambio real"
git push origin main
```

4. Espera a que el CI de `main` termine en verde.

5. Crea la etiqueta en el commit de `main` que acabas de empujar:

```bash
# Solo móvil
git tag mobile-vX.Y.Z
git push origin mobile-vX.Y.Z

# Varias plataformas o todas: usa la etiqueta global
git tag vX.Y.Z
git push origin vX.Y.Z
```

6. Sigue el workflow **Release** en GitHub Actions. No vuelvas a ejecutar el run
   antiguo si el workflow cambió: el run antiguo usa el commit y el workflow
   antiguos.

## Cuándo usar etiqueta global y cuándo forzar el canal

- Si el cambio está solo en `app-mobile/`, `app-tv/` o `desktop/`, la etiqueta
  global ya detecta el canal correcto.
- Si tocaste `core/` porque el código es compartido, la etiqueta global publica
  los tres canales. Es lo correcto si el cambio puede afectar a los tres.
- Si tocaste `core/` pero el cambio es exclusivo de un canal y los demás no
  deben recibirlo, usa la etiqueta de canal. Hazlo solo si estás seguro de que
  las otras apps siguen funcionando con el mismo `core/`.
- Si no hay cambios de código, no etiquetes. El workflow falla a propósito para
  evitar releases vacías.

## Etiquetas y recuperación

La etiqueta debe apuntar al **último commit de `main`** y su versión debe
coincidir exactamente con `version.properties`. El workflow compara el commit
del tag con `origin/main` y falla si no son el mismo.

- Si el workflow falla **antes** de `publish` y todavía no existe ninguna release
  para esa etiqueta, corrige en un commit nuevo, sube la versión y crea una
  etiqueta nueva. Con la protección de tags activada no se puede borrar ni
  recrear la etiqueta vieja; no lo intentes.
- Si la release ya existe, no muevas la etiqueta: sube la versión (por ejemplo,
  `0.2.0` -> `0.2.1`) y publica una etiqueta nueva. El workflow ya no edita
  releases publicadas: solo crea un draft, sube los assets y lo publica.
- Para comprobar si existe una release:

```bash
gh release view mobile-vX.Y.Z
gh release view tv-vX.Y.Z
gh release view desktop-vX.Y.Z
```

Si no tienes `gh`, comprueba la página de releases en GitHub antes de decidir.
Con la protección de tags activada, no borres ni muevas la etiqueta: sube la
versión.

## Protecciones que deben configurarse en GitHub

Estas reglas no viven en el repositorio; se configuran en GitHub y son las que
impiden que un agente con permisos de escritura mueva una etiqueta o reescriba
`main`. El agente debe usar una cuenta con permiso **Write**, nunca **Admin**: un
administrador puede cambiar las reglas y saltarse estas barreras.

### Etiquetas

En **Settings → Rules → Rulesets**, crea un ruleset para tags:

- Target: todos los tags, o los patrones `v*`, `mobile-v*`, `tv-v*` y
  `desktop-v*`.
- Activa el bloqueo de eliminación y de actualización/force push de tags.
- No bloquees la creación: el workflow necesita crear `mobile-v...`, etc.
- Deja la lista de bypass sin agentes ni cuentas de automatización.

Con esto, una etiqueta no se puede borrar, mover ni reutilizar. Si un
release falla antes de publicar, sube la versión y crea una etiqueta nueva; no
intentes borrar ni recrear la etiqueta protegida.

### Rama main

Crea un ruleset para `main` que bloquee force pushes y eliminación de la rama.
Si quieres la garantía máxima, activa también «Require a pull request before
merging» y revisa los cambios de `.github/workflows/` y `scripts/` antes de
mergear. Eso impide que un agente modifique el propio sistema de release sin
revisión.

### Entorno de publicación

El job `publish` usa el entorno `release`. Si quieres que ninguna release se
publique sin aprobación humana, en **Settings → Environments → release** añade
revisores obligatorios y permite los tags `v*`, `mobile-v*`, `tv-v*` y
`desktop-v*` en «Deployment branches and tags». Si no lo configuras, el entorno
no bloquea nada y las releases siguen siendo automáticas.

## Secretos de Android

El workflow espera estos secretos de repositorio:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

No los imprimas, no los pegues en un commit y no los cambies sin confirmar que
es la misma clave que firma la app instalada. Si faltan, el workflow falla a
propósito antes de publicar un APK con la clave de depuración.

## Verificación final

- `git status` limpio antes de etiquetar.
- `version.properties` coincide con la etiqueta.
- CI de `main` en verde.
- El run **Release** terminó en verde y creó solo los canales esperados.
- Cada release tiene su APK/MSI y su `.sha256`.

Si algo no coincide, detente y reporta el error; no improvises un release
manual ni muevas una etiqueta publicada.
