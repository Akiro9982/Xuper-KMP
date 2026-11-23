# Xuper KMP

**Una versión limpia, ligera y multiplataforma de Xuper.**

Xuper KMP es un cliente nuevo, escrito desde cero en **Kotlin Multiplatform (KMP)**, que consume
el mismo catálogo de Xuper (TV en vivo, películas y series) con tu propia cuenta. No es una copia
ni una APK modificada: es una aplicación independiente que reproduce el contenido al que tu cuenta
tiene acceso, sin el peso, el relleno ni las conductas invasivas de la app original.

La idea es sencilla: si el contenido ya existe, la app que lo reproduce no debería espiarte,
instalar cosas por su cuenta ni llenar el dispositivo de SDKs que no aportan nada.

---

## ¿Qué es?

- Un reproductor de **TV en vivo, películas y series** conectado al portal de Xuper.
- Una app **multiplataforma**: Android TV, Android móvil y Windows, con un núcleo común.
- Un proyecto **abierto y auditable**: se puede revisar el código, las dependencias y lo que se
  envía por la red.
- Un cliente pensado para **dispositivos de gama baja**, con una interfaz rápida y un consumo de
  recursos contenido.

Se usa con la **suscripción o cuenta que ya tienes**. La app no vende cuentas ni redistribuye el
catálogo: solo reproduce el contenido al que tu cuenta tiene acceso.

---

## Por qué existe: lo que se encontró en la app original

El análisis del repack de Xuper 4.99.3 que circula como APK (y de las variantes que se reparten
por Telegram y foros) dejó datos verificados, no suposiciones. La app original es una base de
IPTV de pago a la que se le fueron sumando capas de terceros: analítica, publicidad, push,
telemetría, un actualizador propio y protección anti-análisis.

### Identidad y distribución

| Dato verificado | Valor |
|---|---|
| Tamaño del APK analizado | **63,5 MB** |
| Código | **3 DEX**, **16.402 clases**, ~114.000 métodos |
| Permisos | **17** |
| Firma | **clave de test de AOSP** (`android@android.com`), pública |
| `packageName` | `com.google.android.youtube.tvpremium12` (suplanta a YouTube TV Premium) |
| Paquete real | `com.interactive.brasiliptv` → producto Xuper / MAGIS de TVMagis |
| Modder del repack | “ANDROID ECUATORIANO”, con un **packer Dex2C** y un modal con su marca |

La clave de test de AOSP es pública: cualquiera puede firmar con ella. Eso significa que el APK
analizado **no es un build de producción verificable**, sino un reempaquetado. Cualquier tercero
puede modificar el instalador, volver a firmarlo y distribuirlo con el mismo nombre e icono.

### Rastreadores y SDKs que carga

| SDK / paquete | Qué aporta |
|---|---|
| **UMeng** (`com.umeng`, `com.efs`) | Analítica, crash reporting, push, remote config, APM y publicidad. 941 clases + `libumeng-spy.so` (730 KB) |
| **Google Analytics** (`gms.measurement`, `gms.internal.measurement`) | Medición de uso. 693 clases entre ambos |
| **Firebase Analytics / Crashlytics** | Analítica y reporte de fallos. Crashlytics suma 40 clases y 4 librerías nativas (~1,1 MB) |
| **Uyumao** (`com.uyumao`) | Anti-fraude y *device fingerprint*: IMEI, SSID, BSSID, operador y estado de root |
| **Alibaba / Taobao / ACCS** (`com.taobao`, `anet`, `spdy`, `agoo`) | Push y red de Alibaba: 477 clases + `libtnet` |
| **UC CrashSDK** | Reporte de fallos: 30 clases + 1,1 MB de librería nativa |
| **BigBee** | Telemetría de calidad de servicio; su base de datos se abre **al arrancar** y no se puede quitar sin romper el inicio |

En la limpieza de referencia se quitaron **más de 2.800 clases de SDK y 16 librerías nativas
(4,2 MB)**, y aun así la app limpia seguía pesando **50,4 MB** y conservaba 13.546 clases.

### Conductas invasivas documentadas

Estas no son “permisos raros” en abstracto: están localizadas en el código y fueron neutralizadas
una por una en la limpieza:

- **Barrido de la red local**: lee `/proc/net/arp`, obtiene las **MAC de todos los dispositivos de
  tu red**, las cifra con una clave fija y **las sube al servidor**.
- **Identificador de hardware Widevine**: obtiene `deviceUniqueId` del DRM, un identificador de
  hardware **no reseteable**, y lo envía como huella del dispositivo.
- **Persistencia encubierta**: escribe un token cifrado dentro de un WAV en el almacenamiento
  compartido (`Alarms/system_c/google.wav`) y borra el tono original del reloj. **Sobrevive a la
  desinstalación de la app**.
- **Serial del SoC**: ejecuta `cat /sys/class/sunxi_info/sys_info` y `cat /proc/cpu_chipid`.
- **Escritura de propiedades del sistema**: `setprop sys.subtitleService.disable false`.
- **Detección de debugger, emulador y root** para esconder el comportamiento.
- **Borrado automático de logs** cada 30 segundos (anti-forense).
- **Publicidad**: pide el **Google Advertising ID** y mantiene una base de datos de anuncios.
- **Actualización e instalación de APKs**: consulta un servidor de update, descarga un `release.apk`,
  verifica su MD5 y lanza el instalador con `REQUEST_INSTALL_PACKAGES`. El backend puede redirigir
  ese endpoint en runtime. Hay además un “market” integrado que descarga e instala **otras** apps.
- **Packer anti-análisis**: el código de `HomeActivity` queda dentro de `libstub.so`, con ~100
  métodos nativos, para dificultar la auditoría.

### Lo que ni siquiera una APK “limpia” resuelve

La limpieza de la APK reduce el daño, pero no cambia el modelo:

- Sigue siendo un APK **cerrado**, firmado y distribuido fuera de una tienda.
- Conserva **BigBee** (telemetría), **FCM**, DBFlow, RxJava y el **packer Dex2C**.
- Conserva el motor **IJKPlayer + Titan Ranger** (~14,3 MB), que no se puede quitar.
- Sigue usando MAC y `android_id` como *device binding* de la cuenta.
- No hay forma de verificar que la próxima versión que te llegue sea igual a la que analizaste.

Por eso este proyecto no limpia la app: **la reemplaza**.

---

## Qué cambia con Xuper KMP

| Aspecto | App original | APK limpia de referencia | Xuper KMP |
|---|---|---|---|
| Tamaño Android | 63,5 MB | 50,4 MB | **~24 MB** (debug; release con R8 pendiente de medir) |
| Clases | 16.402 | 13.546 | Núcleo propio, sin SDKs de terceros |
| Permisos Android | 17 | 11 | **1: Internet** |
| Analítica / crash / push de terceros | UMeng, Google Analytics, Firebase, Crashlytics, Uyumao | Eliminados | **Ninguno** |
| Publicidad | Sí | Eliminada | **Ninguna** |
| Actualizador e instalador de APKs | Sí | Congelado | **No existe** |
| Barrido de LAN y MAC de vecinos | Sí | Neutralizado | **No existe** |
| `deviceUniqueId` de Widevine | Sí | Neutralizado | **No se solicita** |
| Persistencia fuera de la app | Sí (`system_c`) | Eliminada | **No existe** |
| Detección de root / debugger | Sí | Neutralizada | **No existe** |
| Packer / código nativo oculto | Sí | Conservado | **No: todo el código es Kotlin visible** |
| VPN oculta (variantes modificadas) | Reportada | — | **Conexión directa** |
| Plataformas | Solo Android | Solo Android | **Android TV, Android y Windows** |
| Código | Cerrado | Cerrado | **Abierto y auditable** |

Además, el cliente no se actualiza solo: se compila desde el repositorio y se instala la versión
que tú elijas. No hay servidor que pueda empujarte una APK nueva.

---

## Rendimiento y mejoras reales

Estas mejoras no son marketing: salen de medir el portal y comparar con la app original.

- **Menos peso y menos trabajo al arrancar.** La app original inicializa UMeng, Firebase, xUtils,
  HttpDNS de Qiniu, Titan Ranger, configuración de anuncios, el servicio de dominios y OkGo antes
  de pintar. Xuper KMP arranca con su núcleo y el reproductor del sistema. El APK de Android pasa
  de 63,5 MB a ~24 MB (debug), y de 17 permisos a uno.
- **Buffer profundo y relevo de CDN.** El directo del proveedor se entrecorta en el origen: la
  ventana HLS se estanca 3–6 s y salta 8–12 s, igual en los dos CDN sanos. El cliente lo compensa
  con **20–30 s de buffer** y conmuta de host solo cuando la carga falla de verdad.
- **VOD sin reiniciar al cambiar de fuente.** Se construyen las variantes × CDN, se hace un
  *preflight* con `Range: bytes=0-32767` y sincronía TS, y al conmutar **se conserva el minuto**.
  Una fuente del título *Pledge* devuelve 403 de forma reproducible: la app cambia a la otra y
  sigue donde estaba.
- **DNS resistente.** El DNS del sistema puede devolver `::` o fallar con hosts de CDN y pósters.
  La app valida la respuesta y cae a **DoH por IP literal** (1.1.1.1 / 8.8.8.8), con caché y un
  único cliente compartido entre portal, imágenes y reproductor.
- **TV en vivo ordenada de verdad.** El portal publica **1.035 canales** en **37 categorías**
  reales; la app las carga por categoría y añade filtro por nombre o número.
- **Series completas.** Una serie puede estar partida entre el nombre doblado y el original
  (`Un Show Más T1/T2` + `Historias corrientes T3–T8`). El cliente une ambos por `alias`, agrupa
  temporadas y muestra **8 temporadas** donde la app original mostraba fichas sueltas. Los
  episodios se pintan como “Episodio N” en una rejilla que sigue al foco del mando.
- **Audio real, no “fuente”.** El VOD trae varias pistas AAC en el mismo stream (por ejemplo
  `spa, spa, eng`). El cliente permite elegir idioma y deja la calidad y el CDN en automático.
- **Continuar viendo local.** El avance y el episodio exacto se guardan en el dispositivo, nunca
  en el portal.
- **Gama baja.** RGB_565, sin crossfade, caché de disco de 128 MB, listas perezosas, un solo
  reproductor por pantalla y animaciones de 200 ms o menos.

### Límites que conviene conocer

Un cliente propio no arregla lo que depende del proveedor:

- El **directo se entrecorta en el origen**; el buffer lo disimula, no lo elimina.
- **EPG devuelve 403** incluso desde la app original.
- Los **tokens duran ~4 h y están atados a la IP pública**; al cambiar de red hay que refrescar.
- El portal tiene una **puerta de versión** (`portal200001`): si retiran el build aceptado, el
  cliente debe actualizarse. La app cachea el build válido y lo sondea, pero no puede adivinarlo
  para siempre.
- Algunos canales dan **404 porque el CDN de origen está caído**, no porque tu cuenta no tenga
  acceso.

---

## Funciones

- Inicio con destacados y filas por categoría.
- TV en vivo con las 37 categorías reales y guía filtrable.
- Películas y series con ficha, sinopsis, reparto y episodios.
- Buscador con resultados agrupados por serie y títulos alternos.
- Reproductor con pausa, avance, encuadre de imagen, audio y subtítulos.
- Continuar viendo y episodio recordado por dispositivo.
- Cambio automático de servidor y reintentos ante fallos de red.
- Resolución de nombres tolerante a fallos de DNS.

---

## Plataformas y requisito de mpv en Windows

| Plataforma | Motor de vídeo | Estado |
|---|---|---|
| Android TV | Media3 / ExoPlayer (incluido) | Funcional y probado |
| Android móvil | Media3 / ExoPlayer (incluido) | Funcional y probado |
| Windows | **mpv (se descarga aparte)** | Funcional y probado |

### Por qué mpv no está en el repositorio

El `mpv.exe` que usa la app de Windows pesa **más de 100 MB** y GitHub rechaza archivos de ese
tamaño. Por eso **no se versiona**: se descarga aparte y la app lo detecta sola.

1. Descarga mpv para Windows de 64 bits (probado con **0.42**):
   - <https://mpv.io/installation/>
   - <https://github.com/shinchiro/mpv-winbuild-cmake/releases>
2. Copia `mpv.exe` en una de estas rutas:
   - `%LOCALAPPDATA%\Xuper\mpv\mpv.exe` (recomendada, sin permisos de administrador)
   - `desktop\resources\windows\mpv\mpv.exe` (desarrollo y empaquetado)
   - `mpv\mpv.exe` junto a la aplicación
   - cualquier carpeta del `PATH`
   - o indica la ruta con la variable `XUPER_MPV`
3. Abre la app. Si no lo encuentra, muestra una pantalla con la ruta exacta y un botón para
   descargarlo. También puedes reintentar sin cerrar la app.

Las instrucciones completas están en
[`desktop/resources/windows/mpv/README.md`](desktop/resources/windows/mpv/README.md).

### ¿Hay una alternativa más ligera?

Para Windows, mpv es la opción más ligera que cubre todo lo que necesita este catálogo (HLS en
directo, TS progresivo, HEVC/H.264, subtítulos, varias pistas de audio y control por IPC). Las
alternativas no mejoran el tamaño:

| Alternativa | Tamaño aproximado | Problema |
|---|---|---|
| **mpv** | 50–120 MB según build | Elegida: es la más completa y la más pequeña que sirve |
| libVLC / vlcj | 130 MB+ | Más pesada y más compleja de incrustar y controlar |
| FFmpeg + SDL | Similar a mpv | Habría que reescribir el reproductor y sus controles |
| JavaFX Media | Incluido en el JVM | No soporta HLS, TS ni HEVC; no sirve para este catálogo |
| Media3 / ExoPlayer | Incluido en Android | Excelente en Android, sin soporte oficial de escritorio |
| GStreamer | Instalación grande | Requiere runtime del sistema y plugins |

Conclusión: en Android no hace falta nada externo; en Windows, **mpv descargado aparte** es la
opción más razonable. Si algún día aparece un build de mpv más pequeño que siga cubriendo HLS y
HEVC, se puede usar sin cambiar el código.

---

## Uso

1. Compila o descarga la versión de tu plataforma.
2. En Windows, descarga `mpv.exe` y colócalo como se indica arriba.
3. Abre la app e inicia con tu cuenta de Xuper.
4. Navega por el catálogo y reproduce.

El uso es **personal**: se apoya en tu propia suscripción y no se redistribuyen enlaces, tokens
ni listas de canales.

---

## Estado del proyecto

El núcleo, las tres aplicaciones y la reproducción están terminados y validados contra el portal
real. Queda por delante la optimización para dispositivos muy limitados, la firma de las versiones
de publicación y el empaquetado de Windows sin depender de una copia local de mpv.

---

## Estructura general

- `core/`: núcleo compartido (protocolo, modelos, sesión y construcción de reproducción).
- `app-tv/`: aplicación para Android TV.
- `app-mobile/`: aplicación para Android móvil.
- `desktop/`: aplicación para Windows; requiere `mpv.exe` aparte.
- `docs/`: diagnóstico y evidencia de las decisiones.
- `desktop/resources/windows/mpv/README.md`: cómo descargar y colocar mpv.

Los detalles técnicos, las mediciones y la evidencia están en `docs/` y en el código.

---

## Aviso

Xuper KMP no está afiliado a Xuper ni a Magis TV. El catálogo y el servicio pertenecen a sus
respectivos dueños; esta app es un cliente alternativo para usar tu propia cuenta.

---

## Agradecimientos

A la comunidad que documentó el comportamiento de la app original y a quienes reportaron sus
problemas de seguridad, permisos y distribución: ese trabajo es la razón de que este proyecto
exista.
