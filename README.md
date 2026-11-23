# Xuper KMP

**Una versión limpia, ligera y multiplataforma de Xuper.**

Xuper KMP es una aplicación nueva, escrita desde cero, que consume el mismo catálogo de Xuper
(TV en vivo, películas y series) con tu propia cuenta. No es una copia ni una modificación de
la APK original: es un cliente independiente construido con **Kotlin Multiplatform (KMP)**, con
el mismo contenido, pero sin el peso, el relleno y las conductas invasivas que arrastra la app
original.

El proyecto nace de una idea sencilla: si el contenido ya funciona, la app que lo reproduce no
debería espiarte, llenarte de publicidad ni instalar cosas por su cuenta.

---

## ¿Qué es?

- Un reproductor de **TV en vivo, películas y series** que se conecta al portal de Xuper.
- Una app **multiplataforma**: Android TV, Android móvil y Windows, con una base de código
  compartida.
- Un proyecto **abierto y auditable**: cualquiera puede revisar qué hace y qué no hace.
- Un cliente pensado para **dispositivos de gama baja**, con una interfaz rápida y un consumo
  de recursos contenido.

Se usa con la **suscripción o cuenta que ya tienes** en Xuper. La app no vende cuentas ni
redistribuye el catálogo: solo reproduce el contenido al que tu cuenta tiene acceso.

---

## Por qué existe: el problema de la app original

La app original de Xuper (heredera de Magis TV) funciona, pero acumula años de capas: SDKs de
terceros, publicidad, analítica, actualizaciones forzadas y permisos que nada tienen que ver
con ver la televisión. A eso se suma que se distribuye fuera de las tiendas oficiales, donde
circulan versiones modificadas con el mismo nombre y el mismo icono.

Estos son los puntos que más se han documentado y los que este proyecto corrige de raíz:

### Rastreadores y analítica
La app original incorpora varios kits de medición y reporte (analítica de uso, informes de
fallos, configuración remota y publicidad). Cada uno abre conexiones propias, recopila datos
del dispositivo y engorda el APK. En Xuper KMP **no hay ningún SDK de analítica ni de
publicidad**: la app habla con el portal de Xuper y poco más.

### Permisos invasivos
Análisis de seguridad independientes han clasificado la app original como *programa
potencialmente no deseado* por pedir permisos que una app de vídeo no necesita: acceso amplio
al almacenamiento, listado de aplicaciones instaladas, tareas en ejecución o instalación de
paquetes, entre otros. En Xuper KMP los dos clientes Android piden **un único permiso:
Internet**.

### Instalación de otras apps sin control
El permiso para instalar paquetes permite a la app original descargar y ofrecer la instalación
de actualizaciones o de aplicaciones adicionales desde sus propios servidores. Xuper KMP no
instala nada: lo que descargas es lo que ejecutas.

### Actualizaciones fuera de la tienda
La app original se actualiza a sí misma desde fuera de Google Play, así que la versión que
tienes puede cambiar sin que lo sepas y cada origen puede entregar un archivo distinto. Aquí
las versiones se publican en el repositorio y el código es visible antes de compilarlo.

### VPN y tráfico interceptado
Algunas variantes modificadas que circulan por Telegram y foros incorporan una VPN oculta que
enruta el tráfico del dispositivo y lo hace pasar por servidores ajenos. Xuper KMP usa conexión
directa, sin VPN, sin proxy propio y sin intermediarios.

### Ubicación y datos del dispositivo
Se ha reportado el envío de ubicación y de datos del equipo a servidores externos en versiones
modificadas. En este cliente la única información que viaja es la necesaria para autenticarse
y reproducir.

### Bloqueos y ransomwares de variantes modificadas
Versiones repartidas por canales no oficiales han llegado a bloquear el dispositivo con una
pantalla de "seguridad" y pedir un pago para liberarlo. Al no arrastrar ese ecosistema, este
cliente se libra de esas capas.

### Peso y bloatware
La app original incluye decenas de librerías que casi nadie usa (reparto a pantallas, telemetría,
publicidad, utilidades diversas). Este proyecto parte del mínimo imprescindible y crece solo
cuando una función lo justifica.

> El riesgo de la app original no está en un permiso concreto, sino en su modelo de
> distribución: APKs sin firma verificable, fuera de tienda y con multitud de copias alteradas.
> Un cliente propio y abierto elimina esa incertidumbre.

---

## Qué mejora frente a la app original

| Aspecto | App original | Xuper KMP |
|---|---|---|
| Analítica y rastreadores | Varios SDKs de terceros | Ninguno |
| Publicidad | Presente | Ninguna |
| Permisos en Android | Amplios y ajenos al vídeo | Solo Internet |
| Instalación de otras apps | Sí | No |
| Actualizaciones | Se autoactualiza fuera de tienda | Versiones publicadas y auditables |
| VPN / intermediarios | VPN oculta en algunas variantes | Conexión directa |
| Peso | APK inflado con librerías | Construido sobre lo esencial |
| Plataformas | Solo Android | Android TV, Android y Windows |
| Código | Cerrado | Abierto y revisable |

Además de lo anterior, el cliente mejora la experiencia en cosas concretas:

- **Reproducción estable**: buffer amplio y cambio automático de servidor cuando una fuente
  falla, sin reiniciar el contenido.
- **TV en vivo ordenada** por las categorías reales del portal, con filtro por nombre y número.
- **Series bien resueltas**: episodios, temporadas y una ficha por serie, sin duplicados.
- **Continuar viendo** guardado en tu dispositivo, nunca en el portal.
- **Subtítulos** con idioma y tamaño ajustables.
- **Diseño adaptado** a mando a distancia en TV, a tacto en móvil y a teclado/ratón en Windows.
- **Rendimiento en gama baja**: imágenes ligeras, listas eficientes y un solo reproductor activo.

---

## Plataformas

| Plataforma | Estado | Interfaz |
|---|---|---|
| Android TV | Funcional y probada | Mando a distancia, pantalla grande |
| Android móvil | Funcional y probada | Táctil, Material 3 |
| Windows | Funcional y probada | Teclado y ratón, reproductor integrado |

Las tres comparten el mismo núcleo, así que el catálogo, la búsqueda y la reproducción se
comportan igual en todas.

---

## Funciones

- Inicio con destacados y filas por categoría.
- TV en vivo con las 37 categorías reales del portal y guía filtrable.
- Películas y series con ficha, sinopsis, reparto y episodios.
- Buscador con resultados agrupados por serie.
- Reproductor con pausa, avance, encuadre de imagen, audio y subtítulos.
- Continuar viendo y episodio recordado por dispositivo.
- Cambio automático de servidor y reintentos ante fallos de red.
- Resolución de nombres tolerante a fallos de DNS.

---

## Uso

1. Compila o descarga la versión de tu plataforma.
2. Abre la app e inicia con tu cuenta de Xuper.
3. Navega por el catálogo y reproduce.

El uso es **personal**: se apoya en tu propia suscripción y no se redistribuyen enlaces, tokens
ni listas de canales.

---

## Estado del proyecto

El núcleo, las tres aplicaciones y la reproducción están terminados y validados en entorno real.
Queda por delante la optimización para dispositivos muy limitados y la preparación de las
versiones de publicación.

---

## Estructura general

- `core/`: núcleo compartido (protocolo, modelos, sesión y construcción de reproducción).
- `app-tv/`: aplicación para Android TV.
- `app-mobile/`: aplicación para Android móvil.
- `desktop/`: aplicación para Windows.
- `docs/`: documentación de diagnóstico y decisiones.

Los detalles técnicos, la evidencia y las notas de cada entrega están en `docs/`.

---

## Aviso

Xuper KMP no está afiliado a Xuper ni a Magis TV. El catálogo y el servicio pertenecen a sus
respectivos dueños; esta app es un cliente alternativo para usar tu propia cuenta.

---

## Agradecimientos

A la comunidad que documentó el comportamiento de la app original y a quienes reportaron sus
problemas de seguridad, permisos y distribución: ese trabajo es la razón de que este proyecto
exista.
