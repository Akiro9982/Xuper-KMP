# mpv para la app de Windows

`mpv.exe` **no se versiona** en este repositorio porque ocupa más de 100 MB y GitHub rechaza
archivos de ese tamaño. La app de Android (TV y móvil) no lo necesita: usa Media3, que ya viene
incluido.

## Qué descargar

Descarga una compilación de **mpv para Windows de 64 bits** (0.38 o superior; probado con 0.42):

- Página oficial de instalación: <https://mpv.io/installation/>
- Compilaciones para Windows: <https://github.com/shinchiro/mpv-winbuild-cmake/releases>

Descomprime el archivo y quédate con `mpv.exe`. No hace falta instalar nada.

## Dónde colocarlo

La app lo busca en este orden y usa el primero que encuentre:

1. La ruta indicada en la variable `XUPER_MPV` o en la propiedad `xuper.mpv`.
2. `desktop/resources/windows/mpv/mpv.exe` (desarrollo y empaquetado).
3. `mpv/mpv.exe` o `mpv.exe` junto a la aplicación.
4. `%LOCALAPPDATA%\Xuper\mpv\mpv.exe`.
5. `%LOCALAPPDATA%\Programs\mpv\mpv.exe`.
6. `%ProgramFiles%\mpv\mpv.exe`.
7. Cualquier carpeta del `PATH` que contenga `mpv.exe`.

La opción más cómoda para desarrollo es la **2**:

```powershell
# desde la raíz del repositorio
New-Item -ItemType Directory -Force desktop\resources\windows\mpv | Out-Null
# copia aquí el mpv.exe que descargaste
Copy-Item "C:\ruta\al\mpv.exe" desktop\resources\windows\mpv\mpv.exe
```

Para una instalación ya empaquetada, la **4** no requiere permisos de administrador:

```powershell
New-Item -ItemType Directory -Force "$env:LOCALAPPDATA\Xuper\mpv" | Out-Null
Copy-Item "C:\ruta\al\mpv.exe" "$env:LOCALAPPDATA\Xuper\mpv\mpv.exe"
```

## Comprobar que se encuentra

```powershell
.\gradlew.bat :desktop:runMpvTest
```

Si imprime una ruta válida de `mpv` y reproduce, está listo. Si no, revisa las rutas de arriba.
