# Lote 0 — Diagnóstico de los 10 puntos reportados

Fecha: 2026-11-18 · Sesión real de invitado (SN `4c3b…f38a`) · Portal en vivo
Herramienta: `:desktop:diagnose` (salida completa en `%TEMP%\diag4.txt`)

Este documento cierra la fase de diagnóstico. Cada conclusión está medida contra el
portal, no inferida del código.

---

## 1 · Directo: entrecorta y se queda cargando

**Medido en ECDF (canal 7) y WIN Sports COL HD (194).**

| CDN | Playlist | 4 segmentos | Resultado |
|---|---|---|---|
| `cdnType=1` `auuy2136a.xh09je1qy.com` | **HTTP 403** | — | muerto |
| `cdnType=4` `niguof.vynbszicd.com` | 137–182 ms | 4/4 | OK |
| `cdnType=4` `aluve.rdgqkfxio.com` | 119–163 ms | 4/4 | OK |

Continuidad de la ventana HLS (8 lecturas cada 3 s, mismo canal, ambos CDN sanos):

```
niguof:  huecos=1  estancada=3 de 7 intervalos   (hueco de 8532 ms)
aluve:   huecos=1  estancada=4 de 7 intervalos   (hueco de 8907 ms)
```

La playlist **no avanza de forma continua**: se queda igual 3–6 s y luego salta 8–12 s
de contenido. `#EXT-X-TARGETDURATION:5` pero aparecen segmentos de **8.229 s**, y varios
segmentos vienen partidos (`#EXT-SEGMENT:0-247031,247032-502711`). Es comportamiento del
origen, idéntico en los dos CDN: **no es un problema de elección de CDN**.

Segmentos: 127–268 ms, 200 OK, 210–480 KB (~0,4–0,9 Mbps). La red no es el cuello de
botella.

**Conclusión:** el tirón viene de la cadena de empaquetado del proveedor (ventana corta
de 6 segmentos, actualizaciones en ráfaga y huecos). Un reproductor con buffer por
defecto se queda sin datos durante las pausas. Mitigación cliente:

1. Buffer profundo (Media3 `DefaultLoadControl` 20–30 s; mpv `--cache-secs=30`).
2. Arrancar cerca del directo pero con colchón: `targetOffsetMs` ≈ 2× target duration.
3. Reintento y conmutación al segundo CDN ante error de carga.
4. Descartar `cdnType=1` (403) y probar el `playCode` en los CDN sanos.

Datos extra: **1035 canales, 1033 tienen variante `cdn=4`, solo 2 tienen únicamente
`cdn=1` (muerto)**. Esos 2 se pueden rescatar probando su `playCode` en un CDN sano.

---

## 2 · VOD fuera del home no reproduce

**Causa exacta, reproducida:**

```
[CORRECTO] ep n=1 (26D0DB96…)  → 206 video/MP2T 262144B sync=71
[CORRECTO] ep n=2 (C79064E1…)  → 206 video/MP2T 262144B sync=71
[APP]      type=0 columnId=0   → FALLA: /v10/startPlayVOD: 请求参数异常
```

El botón principal de la ficha dice «Reproducir T1 E1» pero llama al **path de película**:

```kotlin
// DetailScreen.kt (las tres apps)
Button(onClick = { onPlay(PlayerPayload.Vod(asset)) })
```

`PlayerPayload.Vod` envía `type = asset.type` (que para una serie es `"0"`) con
`columnId = 0` y `seriesContentId = ""`. El backend rechaza esa combinación. La serie
solo reproduce con:

```
contentId = <id del episodio>   type = "0"   columnId = 87026
seriesContentId = <id de la temporada>   episodeNumberList = [n]
```

**Las películas sí funcionan** (verificado con *Contra el huracán*: `itemData type=1`,
ticket, `206`, TS válido, subtítulos `en,es`). Por eso el fallo se percibe como «todo lo
que no está en el home», cuando en realidad es «toda ficha de serie».

**Arreglo:** en las tres fichas, si es serie, el botón principal reproduce el primer
episodio por el path de episodio; el path de película queda solo para `type=1`.

---

## 3 · Categorías Anime/Series y otras no funcionan

**Árbol VOD real (`getColumnContents 87026`):** solo 3 hijos, todos vacíos en cuenta free.

| Columna | totalSize | contenido |
|---|---|---|
| Free.Movies (87027) | 0 | 0 |
| Free.TV Series (87028) | 0 | 0 |
| Free.Kids (87029) | 0 | 0 |

**Home (`getNextColumns masnew_featured`):** 10 filas × 9 ítems = **90 títulos**.
`pageSize` se ignora (10/50/200 devuelven lo mismo) y `totalSize=10`. Las filas no se
pueden pedir por su `columnCode` (devuelve 0 filas), ni por `columnId` (0 contenidos).

**Tags reales del catálogo:** Drama 37, Action 33, Thriller 29, Horror 27, Adventure 26,
Comedy 20, Animation 12, Family 11, Cartoon 11, **Anime 6**, Sci-Fi 6…

**Causa:** la app filtra por `programType` con valores que no existen:

```kotlin
Kids("Kids", listOf("kids", "infantil", "family"))   // programType solo es movie|teleplay
Anime("Anime", listOf("anime", "animation"))         // ídem → siempre vacío
```

**TV en vivo sí tiene árbol real:** 38 categorías (Deportes, Cine y Series, Venezuela,
Colombia, Ecuador, Chile, México, Perú, Noticias, Full HD, HD+(265), Infantil, …).
`getLiveData {columnId: 76206}` devuelve **105 canales** de Deportes. Hoy la app no usa
ese árbol: lista los 1035 canales en plano.

**Arreglo:**
- VOD: filtrar por `type` (película/serie) y por **tags** (Anime/Animation, Family/
  Cartoon/Kids), no por `programType`. Asumir que el plan free ofrece ~90 títulos y
  decirlo en la UI en vez de mostrar una sección vacía.
- Directo: navegación por las 38 categorías reales del portal.

---

## 4 · Búsqueda: una ficha por temporada

`searchByName("un show mas")` devuelve 20 ítems, entre ellos:

```
Un show más T2                         (28 eps)
Un Show Más T1                         (12 eps)
Un Show Más: Las Cintas Perdidas T1    (14 eps, 18 emitidos)
Un show más: la película               (type=1)
```

La temporada está **en el nombre** (`T1`, `T2`), no en `volumnCount` (que es el número de
episodios). La app muestra cada temporada como una serie distinta.

**Arreglo:** agrupar por título base normalizado (minúsculas, sin acentos, sin sufijo
` T\d+`) y quedarse con el tipo (`0`=serie). *Las Cintas Perdidas* es otra serie
(spin-off), no una temporada.

---

## 5 · Ficha: episodios sin organizar

- `simpleProgramList` sí viene ordenado: 28 episodios, `seriesNumber` 1…28, nombres
  `Un show más T2_1 … T2_28`.
- `sameSeasonSeriesList` trae **10 contentId sin nombre, número ni calidad** → no sirve
  para construir el selector de temporadas.
- Las temporadas hermanas solo aparecen en la **búsqueda** (cada una es un asset).

**Arreglo:** al abrir una serie, buscar su título base, agrupar y mostrar selector de
temporada + rejilla de episodios. Reutiliza el agrupador del punto 4.

---

## 6 · Pósters descentrados

Es recorte de UI: los pósters son 2:3 y se pintan en tarjetas 16:9 con `ContentScale.Crop`,
que corta arriba y abajo y deja la cara fuera de encuadre. No es un problema del dato.
Pendiente de arreglo visual (contenedor con proporción correcta o `Alignment.Top` +
degradado).

---

## 7 · Pistas de audio/subtítulos y dos fuentes de vídeo

- **Subtítulos:** el ticket de película sí trae `subtitleList` (`en`, `es`) con URL `.srt`
  pública; los episodios de la serie probada traen `subtitleList` **vacío**. Hay que
  confirmar en más series antes de prometer subtítulos en episodios.
- **Variantes:** los títulos probados devuelven **1 sola variante** (`720p/h265/ts`,
  `audio=spa,eng`, con licencia). No hay dos calidades para elegir en ese contenido.
  Las «dos opciones de vídeo» de la app original probablemente son las dos **fuentes/CDN**
  o los dos códecs cuando existen.
- **Hoy la app toma `variants.first()`** y no prueba las demás ni cambia de CDN si falla.

**Arreglo:** exponer todas las variantes + todas las fuentes, probar con un `Range` corto
antes de abrir el reproductor y hacer fallback automático. Selector de audio/subtítulos
según pistas reales del contenedor (Media3/mpv).

---

## 8 · Continuar viendo

Implementado como historial **local por dispositivo** (SharedPreferences en Android/TV,
preferencias de Java en Windows). Una serie guarda el episodio concreto y reanuda ese
capítulo.

**Identidad de la ficha:** cada temporada se publica como un asset independiente y con su
propio título (a veces sin sufijo de temporada: la copia original de *Star vs. the Forces
of Evil* se llama así, sin «Season 2»). Por eso la tarjeta no deduce nada del nombre del
asset:

- la temporada y el nombre visible viajan en el payload del reproductor, tomados de la
  ficha (`SeasonEntry.number`, título de la entrada que contiene el capítulo);
- el historial guarda además el **alias** del portal como clave de serie, de modo que el
  doblaje y el original comparten una sola tarjeta;
- al leer, se normaliza lo guardado por versiones anteriores (sufijo «T2»/«Season 2» fuera
  y temporada deducida del nombre del capítulo) y se descartan tarjetas repetidas.

---

## 9 · Loaders

Hoy hay un `LoadingState` genérico (spinner). Pendiente de animación propia.

---

## 10 · Categorías + TMDb

Decidido: **TMDb se pospone al lote 6**. Primero se normalizan categorías con los datos
del portal (tags + árbol de TV en vivo). TMDb entraría después como enriquecimiento
opcional (sinopsis/nota/póster limpio), nunca como fuente de verdad.

---

## Orden de arreglo propuesto (ajustado a la evidencia)

| Lote | Puntos | Por qué en este orden |
|---|---|---|
| 1 | **2** (ficha de serie) → **3** (categorías reales) | Desbloquea el uso diario; #2 es la causa real de «Series no funciona» |
| 1b | **1** (buffer + conmutación de CDN) | El origen tiene huecos; sin buffer no hay solución |
| 2 | **4 + 5** (agrupador de series + selector de temporada) | Es un solo modelo de datos |
| 2b | **6** (pósters) | Barato y visible |
| 3 | **7** (variantes/fuentes + pistas + tamaño de subtítulo) | Depende de la UI del reproductor |
| 4 | **8** (continuar viendo) | Persistencia local por plataforma |
| 5 | **9** (loaders) | Pulido |
| 6 | **10** (TMDb opcional) | Decisión de producto ya tomada |

**Fase 6** (R8, firma, ABI splits, rendimiento) sigue al final, con el conjunto congelado.

---

## Decisiones tomadas (antes pendientes)

1. **Continuar viendo:** local por dispositivo. Nunca se sube al portal (privacidad y
   cero escrituras de cuenta): SharedPreferences en Android/TV, preferencias de Java en
   Windows. Una serie guarda el episodio concreto y reanuda ese capítulo.
2. **Subtítulos:** desactivados por defecto; el usuario elige idioma y tamaño (100 % = 16 sp,
   rango 70–180 %). Los subtítulos nunca se fuerzan solos.
3. **Título sin pistas en el ticket:** se muestra «Este título no incluye subtítulos» en el
   diálogo de reproducción; no se sondean rutas alternativas (coste y ruido sin beneficio).

---

## Estado de implementación

| # | Arreglo | Estado |
|---|---|---|
| 1 | Buffer 15–30 s + candidatos de CDN y conmutación automática | Hecho (Android, TV, escritorio) |
| 2 | Reproducción de series por episodio (`startPlayVOD` correcto) | Hecho |
| 3 | Catálogo VOD por `type`/`tags`; **37 categorías LIVE reales** | Hecho |
| 4 | Búsqueda agrupada: una ficha por serie | Hecho |
| 5 | Selector de temporada en la ficha | Hecho |
| 6 | Pósters 16:9 con `stage` (sin recorte de caras) | Hecho |
| 7 | Fuente/calidad, subtítulos (idioma + tamaño) en reproducción | Hecho |
| 8 | Continuar viendo local, por dispositivo | Hecho |
| 9 | Esqueletos y dots animados | Hecho |
| 10 | TMDb como enriquecimiento opcional | Pospuesto al lote 6 |

**Verificación #3 (`:desktop:diagnose`):** `getColumnContents {columnId: 76175}` →
**38 nodos**; se descarta `ChannelList` (76182) porque duplica la lista plana, quedan
**37 categorías** (Deportes 105 canales, Cine y Series, Vivo gratis, Venezuela,
Colombia, Ecuador, Chile, México, Perú, Noticias, Full HD, HD+(265), Infantil…).
Cada categoría se pide con `getLiveData {columnId: <id>}`.

**Corrección de protocolo:** la raíz del árbol LIVE es **76175**, no 76182 (76182 es
`ChannelList`, el nodo plano). Queda documentado en `XuperConfig.liveTreeColumnId`.

---

## Ajustes surgidos en la verificación (lote 5b)

### Reproductor: cambiar de fuente ya no reinicia la reproducción

`OkHttpDataSource.Factory.setDefaultRequestProperties(map)` **copia** el mapa en el momento
de construir el data source. Al compartir un `HashMap` mutable para no recrear el
`ExoPlayer` al cambiar de fuente, las cabeceras de sesión se perdían y el CDN respondía
**401/403** en directo.

Solución en Android TV y móvil:

```kotlin
val fuenteDeDatos = remember(payload) {
    object : DataSource.Factory {
        override fun createDataSource() =
            OkHttpDataSource.Factory(NetProvider.okHttpClient)
                .setDefaultRequestProperties(cabeceras)   // se lee en cada creación
                .createDataSource()
    }
}
```

`DefaultMediaSourceFactory(fuenteDeDatos)` se crea una sola vez (`remember(payload)`), de
modo que cambiar de fuente solo actualiza URL + cabeceras: el reproductor no se recrea, no
se renegocia el códec y la posición se conserva. En escritorio `tryNext()` fija
`reanudarEn = posicion` antes de relanzar mpv.

### TV: contraste del foco

`androidx.tv.material3` publica **su propio** `LocalContentColor`, que
`androidx.compose.material3.Text/Icon` **no leen**: el texto caía a negro mientras el
contenedor de foco se pintaba claro (o rojo), y desaparecía. Ahora:

- `ui/theme/TvColors.kt` → `tvCardColors()`: foco sobre `primaryContainer` /
  `onPrimaryContainer` (rojo de marca + texto claro), en vez de la inversión clara por
  defecto de tv-material.
- Menú lateral y tarjetas de canal fijan el color del texto/icono según `onFocusChanged`.

### TV: D-pad hasta la guía de canales

- Al cambiar de categoría los chips **no se desmontan** (antes el estado de carga sustituía
  toda la pantalla y el foco caía al menú lateral).
- Desde los chips, DOWN entra en la rejilla (`onPreviewKeyEvent` + `FocusRequester`), en vez
  de saltar al menú lateral, que está más cerca en vertical.

### TV: ajustes de reproducción

- Panel opaco redondeado (`surface` al 98 %): antes el vídeo se veía a través y el texto
  perdía legibilidad.
- Solo la lista se desplaza (`verticalScroll` con `weight(1f)`); título y «Cerrar» quedan
  fijos.
- El `PlayerView` ya no es enfocable (`isFocusable = false`,
  `FOCUS_BLOCK_DESCENDANTS`): se quedaba con el foco del D-pad y los botones Compose no
  recibían teclas. Con el overlay abierto, ENTER llega a «Ajustes» en vez de pausar.
- BACK cierra el overlay antes que el reproductor (`BackHandler`).

### Fallo de origen visible

Si el stream falla con la URL ya fijada, el `PlayerView` quedaba en negro sin explicación.
TV y móvil muestran «No se pudo reproducir», el detalle del error y «Atrás para volver».

### Escritorio: ajustes, reanudación y relevo de CDN

Verificado de punta a punta en Windows (mpv embebido por HWND). Tres fallos reales:

1. **El diálogo de ajustes quedaba tapado por el vídeo.** El vídeo es un `Canvas` AWT
   pesado: cualquier capa Compose dibujada en la ventana principal queda **debajo** del
   vídeo (se veían solo los bordes de la tarjeta sobre el atenuado). `AlertDialog` y el
   `Dialog` de Compose son capas dentro de la ventana, así que no sirven aquí. Los ajustes
   viven ahora en una **ventana propia** (`JDialog` sin decorar, hija de la principal) con
   `ComposePanel` + `XuperTheme`. Los datos llegan por `rememberUpdatedState`, de modo que
   el panel se recompone al cambiar fuente, pista o tamaño. Se descartó la ventana
   translúcida: en Windows es *layered* y **los clics la atraviesan** (no recibía ni
   «Cerrar»); ahora es opaca.
2. **`dur 0` reiniciaba la posición mostrada.** Al relanzar mpv para otra fuente, su primer
   `duration` llega como `0` y se emitía `onProgress(0.0, dur)`: la barra saltaba a 00:00 y,
   peor, el relevo de CDN copiaba ese 0 a `reanudarEn` y la película empezaba de cero.
   `MpvPlayer` conserva `ultimaPos` y solo emite progreso con duración conocida; el relevo
   solo adopta `posicion` si es > 0, y los eventos de la instancia anterior se ignoran
   (`if (!running) break`).
3. **El primer `seek` tras cargar podía perderse.** En Windows el pipe IPC de mpv rechaza
   conexiones mientras no está listo. `comando()` reintenta (4 × 30 ms) y el salto de
   reanudación se reintenta en segundo plano hasta 3 s. Además la posición se pasa a mpv con
   `--start=`, de modo que el arranque ya sale en el punto correcto.

Resultado: cambiar de fuente conserva la posición (170 s → 171 s), el relevo desde un CDN
muerto vuelve a la fuente buena sin reiniciar, el chip refleja la fuente que realmente
suena, los subtítulos en español se ven al 139 % y «Continuar viendo» guarda el avance del
escritorio. El directo también reproduce («La Granja VIP 1 HD», ● EN VIVO).

---

## Lote 6 — correcciones del reproductor (2026-11-19)

### «EN VIVO» aparecía en un VOD (móvil y TV)

La etiqueta se decidía por la duración (`!isLive && durationMs > 0` en móvil; el `else` de
la duración en TV). Al cambiar de fuente, al relevar un CDN o al recrear el motor la
duración pasa por `0`, así que cualquier película se pintaba como directo.

Ahora depende **solo del contenido** (`isLive`, fijado por el payload):

- Directo: `LinearProgressIndicator` + «● EN VIVO».
- VOD: la barra se muestra siempre, con `enabled = durationMs > 0` y `--:--` hasta que se
  conoce la duración (antes la barra desaparecía un instante y volvía a aparecer).

Verificado en móvil tras cambiar de fuente (`1:03:43 / 1:17:40`, sin «EN VIVO») y en
escritorio con mpv.

### TV: foco del overlay y realce del progreso

- «Ajustes» y el encuadre son **cápsulas compactas** alineadas a la derecha, con el foco
  pintado en el rojo de marca (`animateColorAsState`, 42 dp enfocado / 38 dp en reposo) y
  no con el foco de tv-material, que sobre fondo oscuro era invisible.
- La barra de progreso es un `Canvas` con pista más gruesa, halo `primary`, thumb y un texto
  de ayuda contextual («OK elegir · ↓ barra»): al enfocarla se ve que responde.
- El overlay usa una **máquina de estados explícita** (`ZonaOverlay.PROGRESO / IMAGEN /
  AJUSTES`) resuelta en el `onPreviewKeyEvent` de la raíz, no el recorrido de foco de
  Compose: UP/DOWN alternan barra y botones, LEFT/RIGHT navegan entre botones o buscan, OK
  actúa sobre la zona enfocada. El auto-ocultado (6 s) solo corre con la barra enfocada y
  reproduciendo.
- En directo el overlay arranca con «Ajustes» enfocado, porque no hay barra que enfocar.

### Encuadre de imagen (TV y escritorio)

Cinco modos compartidos (`AjusteImagen`): **Ajustar**, **Pantalla completa**, **Estirar**,
**16:9** y **4:3**.

- **TV** (`PlayerView.aplicarImagen`): `resizeMode` FIT / ZOOM / FILL y, para los formatos
  forzados, `AspectRatioFrameLayout.setAspectRatio()` obtenido con
  `findViewById(R.id.exo_content_frame)`. Media3 1.11.1 no expone `PlayerView.setAspectRatio`;
  comprobado con `javap` que solo existe en `AspectRatioFrameLayout`.
- **Escritorio** (mpv): `panscan=1.0` (pantalla completa), `keepaspect=no` (estirar) y
  `video-aspect-override=16:9|4:3`. El modo elegido se pasa a `start()` como argumento
  porque el pipe IPC puede rechazar órdenes justo tras cargar el archivo; después se cambia
  en caliente por IPC. `aplicarImagen()` limpia los tres ajustes antes de aplicar el nuevo
  para que no se acumulen entre modos.

Interfaz: botón cíclico en la barra inferior del escritorio y sección «Imagen» en el diálogo
de ajustes; en TV, sección «Imagen» dentro del overlay de ajustes.

Verificación del escritorio leyendo las propiedades de mpv por IPC (`osd-dimensions`,
`video-aspect-override`, `keepaspect`, `panscan`): Ajustar deja márgenes de 120/121 px,
Pantalla completa 0, Estirar 0 con `keepaspect=no`, 4:3 deja `video-aspect-override=1.3333`
y 16:9 `1.7778`; al volver a Ajustar se restablecen los márgenes. En TV,
`exo_content_frame` pasa a pantalla completa en Estirar y 4:3 deja bandas laterales.

### Series partidas por título alterno (móvil, TV y escritorio)

El portal publica la misma serie bajo dos nombres: `Un Show Más T1/T2` (alias `Regular Show S1/S2`)
y `Historias corrientes T3–T8` (alias `Regular Show Season3…8`). Con el agrupado por nombre, la
búsqueda mostraba dos fichas y la de `Un Show Más` solo abría T1/T2; T3–T8 quedaban inaccesibles.

- El agrupado del core (`SeriesGrouping`) pasa a **union-find de tokens**: cada asset aporta el
  token de `name` y el de `alias`, normalizados y sin sufijo de temporada (`T`, `S`, `Season`,
  `Temporada`, `Temp.`). Dos assets se unen si comparten cualquier token. Las temporadas
  duplicadas (`T2` en ambos títulos) se colapsan por número.
- `seasonsFor` busca los dos términos (título visible y alias) y une las listas antes de agrupar.
- `searchByName` de los tres repositorios busca también el alias y `searchResults` devuelve una
  entrada por serie con el título limpio; la búsqueda no pinta la temporada, que sería incompleta
  cuando solo parte de la serie viene en la respuesta.

Verificación: sondeo real (`un show mas` → T1/T2 alias `Regular Show S1/S2`; `historias
corrientes` → T1–T8 alias `Regular Show Season1…8`; `regular show` → ambos conjuntos);
`seasonsFor` devuelve **8 temporadas** y la ficha las ofrece todas; 12 pruebas unitarias verdes
(`:core:jvmTest`) y comprobación en emulador de móvil y TV (una sola tarjeta, T1–T8 seleccionables,
reproducción correcta al cambiar de temporada).

**Rejilla de episodios y seguimiento del foco (TV y móvil).** Una `LazyRow` de 20–500 episodios
solo dejaba ver una tira y, en TV, el D-pad no devolvía la vista arriba. La temporada se pinta en
filas dentro de la `LazyColumn` (cada fila es un ítem perezoso), con el número de columnas calculado
por el ancho disponible. En TV, cada tarjeta pide `bringIntoView()` de su fila al recibir foco, así
el desplazamiento sigue al D-pad en ambos sentidos y se llega al encabezado y a las temporadas.
Comprobado con una serie de **500 episodios**: 40 pulsaciones abajo llegan al 124 con la página
sincronizada, y 40 arriba vuelven al 4 y al encabezado. En móvil se valida la misma rejilla con
desplazamiento táctil (8 episodios a la vista). La búsqueda de TV pasa a 3 columnas 16:9 (los
títulos dejan de partirse) y los catálogos a 4 columnas, también 16:9.

### Tarjetas de episodio y rejilla de búsqueda

`simpleProgramList[].name` es un nombre crudo de producción (`The Morning Show T4_1`,
`Blippi Va A Trabajar T1_1`) y `quality` es una lista (`1080p,720p,480p`). Las tres fichas pintan
ahora tarjetas tonales compactas con solo **«Episodio» + `seriesNumber`** (índice como respaldo) y
la cabecera de sección pasa a «Episodios». La búsqueda móvil se rehízo en rejilla de 3 columnas con
póster 2:3 y título debajo (antes un `Box` con relleno inferior recortaba la tarjeta), y en TV la
rejilla usa la tarjeta del resultado agrupado.

### Puerta de versión del portal (portal200001) (2026-11-20)

El portal retiraba el cliente con portal200001 (版本已停止使用). Medido: la compuerta la decide
**solo la cabecera apkVer** (build interno, hoy 43404); el apkVersion del body es irrelevante y
la compuerta afecta también a v8/active. Acepta **un valor exacto**: 43403 y 43405 ya fallan.

- XuperPortalClient.postOnce trata el código como versión caducada: sondea active con builds
  **crecientes** (bloques paralelos de 16) y adopta el primero aceptado antes de reintentar.
- Tope por **tiempo**, no por número: versionRecoverySeconds (120 s) y una segunda pasada x5
  (RECOVERY_SEGUNDA_PASADA); RECOVERY_SALTO_MAXIMO = 50 000 builds. Si agota, lanza
  XuperException.VersionRetired con mensaje explícito (hace falta una app nueva).
- El build aceptado se guarda en el dispositivo (portal_profile, JSON) y se aplica al arrancar.
- Además hay perfil remoto opcional (XuperConfig.profileUrl): un JSON con apkVer, apkVersion,
  portalKey, reserve1 u hosts que se lee una vez por ejecución y permite corregir sin recompilar.
  Los campos nulos se heredan del perfil guardado (mergedWith).
- El sondeo solo salva la rotación de **build**. Si el proveedor rota la PORTAL_KEY o reserve1
  (van dentro del APK), hace falta un APK nuevo del que extraerlos y publicarlos en el perfil.

Verificación: barrido real 43390-43449 (16 en paralelo por bloque) que encuentra 43404 en el
primer bloque; 3 pruebas nuevas (VersionRecoveryTest) y 8 de perfil (PortalProfileTest).

### Fuente pasa a Audio (2026-11-20)

MovieVariant.audioInfo lista idiomas ISO (spa,eng, spa,spa,eng, eng,lat) y **todos los títulos
medidos traen una sola variante de vídeo**: el idioma no es otra fuente, son pistas del mismo
_media.ts. ffprobe sobre Duna (spa,spa,eng) muestra 3 pistas AAC sin default, así que el
reproductor puede elegir por idioma.

- AudioTracks (core) normaliza códigos (es-MX -> lat, en -> eng), deduplica, etiqueta y construye
  la cadena de preferencia; por defecto **latino**.
- La sección Fuente desaparece de los ajustes de reproducción en las tres apps y la sustituye
  Audio, visible solo con dos o más idiomas. La calidad y el relevo de CDN siguen automáticos.
- Móvil y TV aplican setPreferredAudioLanguages(lat, spa) con Media3 al preparar y al cambiar de
  pista; escritorio pasa --alang a mpv y lo cambia en caliente con aid=auto.
- La preferencia se persiste en el dispositivo (audio_language); el directo no la usa.
### Audio por pista real y decodificador siempre a la vista (0.2.3)

El 15.9 anterior se quedó corto: con dos pistas `spa` (latino y castellano) elegir por idioma
no decide nada. Media3 las ve iguales y desempata por calidad, así que en algunas series sonaba
castellano aunque la preferencia fuera latino. La app original no elige «la mejor»: parte el
`audioInfo` por comas y reproduce la pista cuya posición es `selectAudio` (0 por defecto).

- El extractor de TS publica **un grupo por PID de audio** (grupos de un solo índice), así que
  hay que recorrer todos los grupos de `player.currentTracks`; mirar solo el primero devolvía
  una pista y la sección Audio desaparecía (fallo de 0.2.3, corregido en 0.2.4).
- El reproductor móvil lee esas pistas reales y las etiqueta por posición con los códigos del
  `audioInfo` (`spa,spa,eng` -> Español, Español 2, Inglés).
- Si el aparato solo deja ver una pista, se cae al selector por idioma del `audioInfo`: la
  sección Audio nunca se oculta.
- La pista se fija con `TrackSelectionOverride` por **índice**, no por idioma; por defecto la
  del código preferido (`lat`, si no la primera `spa`, si no la primera pista).
- La elección manual se guarda por título (`audio_index_<contentId>`) y se reaplica al cambiar
  de CDN: el selector ahora sí ofrece las dos pistas en español, antes se deduplicaban.
- Ajustes de reproducción: la sección «Vídeo» (Automático/Hardware/Software + copiar informe)
  va **primero**, para que exista en cualquier contenido y desde cualquier entrada —antes, con
  subtítulos y audio, quedaba por debajo del pliegue y no se veía—. El aviso «Ajustando la
  imagen…» también abre los ajustes al tocarlo.
