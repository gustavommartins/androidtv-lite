# androidtv-lite

[English](README.md) · [Português (Brasil)](README.pt-BR.md) · **Español**

Un launcher de Android TV **minimalista y ligero**, pensado para televisores con poca memoria y procesador débil. Es una app de una sola pantalla, en Java puro, sin Leanback, sin Compose y sin bibliotecas externas.

El diseño se inspira en la pantalla de inicio de Apple TV (tvOS). Este proyecto **no está afiliado a Apple** ni a ninguna de las marcas de las apps que muestra.

![Pantalla de inicio](docs/screenshots/home-es.jpg)

<details>
<summary>Otros idiomas (capturas de pantalla)</summary>

| English | Português (Brasil) |
|---|---|
| ![Home screen in English](docs/screenshots/home-en.jpg) | ![Tela inicial em português](docs/screenshots/home-pt.jpg) |

</details>

> La captura en portugués se tomó en un televisor configurado en portugués. Las de inglés y español se tomaron en el mismo televisor, con el idioma de la app forzado mediante una versión de depuración, y muestran lo que ve un televisor configurado en esos idiomas.

## Índice

- [Qué hace](#qué-hace)
- [Idiomas](#idiomas)
- [Requisitos](#requisitos)
- [Compilar](#compilar)
- [Instalar en el televisor](#instalar-en-el-televisor)
- [Establecerlo como launcher predeterminado](#establecerlo-como-launcher-predeterminado)
- [Volver al launcher original / desinstalar](#volver-al-launcher-original--desinstalar)
- [Personalizar](#personalizar)
- [Cómo elige las apps](#cómo-elige-las-apps)
- [Limitaciones conocidas](#limitaciones-conocidas)
- [Solución de problemas](#solución-de-problemas)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Licencia](#licencia)

## Qué hace

- **Barra de navegación superior, centrada:** `Inicio` · `Google Play` · `Configuración`, en una píldora translúcida. La pestaña enfocada se convierte en una píldora blanca, como en tvOS.
- **Fecha en la esquina superior izquierda y hora en la derecha.**
- **Cuadrícula de apps centrada**, en 4 columnas y varias filas, sin desplazamiento lateral. La app enfocada sube, crece y muestra su nombre debajo.
- **Orden alfabético** por defecto, ignorando acentos y mayúsculas. Cuando personalices la pantalla de inicio, se mantiene tu orden.
- **Solo apps de streaming conocidas** por defecto, y cualquier app que instales tú. Las apps del sistema del fabricante y de Google quedan fuera hasta que las añadas.
- **Personalizar la pantalla de inicio**, estilo iOS. Mantén pulsado **OK** en una app y las apps empiezan a temblar. En este modo:
  - **OK** en una app abre un menú con **Mover · Quitar de Inicio · Desinstalar**. *Desinstalar* solo aparece en apps que no son del sistema, y abre el diálogo de desinstalación del sistema.
  - **Mover**: las flechas mueven la app y las demás se desplazan, como en iOS. OK o Atrás la suelta.
  - **Añadir apps** (abajo, al centro) lista todas las apps de TV que no están en Inicio, incluidas las del sistema. OK en una la pone al final de Inicio. **Listo** sale del modo de edición.
  - **Atrás** sale por capas: suelta la app, cierra el menú, cierra *Añadir apps* y, por último, sale de la edición.
  - El orden y las eliminaciones se guardan en el televisor. Las apps nuevas que instales van al final de Inicio; las quitadas solo vuelven desde *Añadir apps*.
- **Imagen de fondo** reducida a 1280 px y cargada en RGB_565 para gastar poca memoria. Es fácil de cambiar.
- **Sin red, sin anuncios, sin telemetría.** El único permiso es `REQUEST_DELETE_PACKAGES` (normal, concedido automáticamente), que se usa solo para abrir el diálogo de desinstalación del sistema.

## Idiomas

El launcher sigue el **idioma del sistema del televisor**. No hay nada que configurar.

| Idioma del televisor | Barra de navegación | Ejemplo de fecha |
|---|---|---|
| English (predeterminado) | Home · Google Play · Settings | Sunday, October 18 |
| Português | Início · Google Play · Configurações | domingo, 18 de outubro |
| Español | Inicio · Google Play · Configuración | domingo, 18 de octubre |

- Cualquier otro idioma usa **inglés**.
- La fecha sigue el formato propio del idioma, y el reloj sigue el ajuste de **12 h / 24 h** del televisor (el formato de 12 h no muestra AM/PM).
- Los nombres de las apps los aporta cada app, por lo que se muestran en el idioma que ella ofrezca.

**Añadir un idioma:** crea `res/values-xx/strings.xml` (`xx` = código del idioma, por ejemplo `fr`) con las cadenas siguientes y vuelve a compilar. Se aceptan pull requests.

```xml
<resources>
    <string name="app_name">...</string>
    <string name="nav_home">...</string>
    <string name="nav_settings">...</string>
    <string name="edit_add_apps">...</string>
    <string name="edit_done">...</string>
    <string name="action_move">...</string>
    <string name="action_remove">...</string>
    <string name="action_uninstall">...</string>
    <string name="add_title">...</string>
    <string name="add_empty">...</string>
</resources>
```

La lista completa, con los textos en inglés, está en `res/values/strings.xml`.

## Requisitos

**En el televisor**

- Android TV con **Android 8.0 (API 26) o superior**.
- Depuración por ADB activada (pasos más abajo).

Probado en un televisor TCL con **Android 9**, **1,4 GB de RAM** y almacenamiento eMMC lento. No se ha probado en otros equipos. Si funciona (o no) en el tuyo, abre una *issue*.

**En el ordenador (para compilar)**

- **JDK 17** o superior. Con JDK 22+ consulta la [nota sobre D8](#nota-sobre-jdk-nuevos-y-d8).
- **Android SDK** con `build-tools` y `platforms;android-28`.
- **ADB** (`platform-tools`) para instalar.

## Compilar

El proyecto no usa Gradle. El script `build.ps1` (Windows/PowerShell) llama directamente a `aapt2`, `javac`, `d8`, `zipalign` y `apksigner`.

### 1. Obtener el Android SDK mínimo

Si aún no tienes el SDK, descarga las *command-line tools* desde <https://developer.android.com/studio#command-line-tools-only> y ejecuta:

```powershell
sdkmanager --sdk_root=C:\Android\sdk "build-tools;34.0.0" "platforms;android-28"
```

Tendrás que aceptar las licencias del Android SDK.

### 2. Compilar

```powershell
git clone https://github.com/gustavommartins/androidtv-lite.git
cd androidtv-lite
.\build.ps1 -SdkRoot C:\Android\sdk
```

El APK queda en `build\androidtv-lite.apk`. Se firma con una clave de prueba creada localmente en `build\debug.keystore`. Esa carpeta no se versiona, y **la clave se conserva entre compilaciones**, así que puedes actualizar una copia ya instalada con `adb install -r`.

> Si PowerShell bloquea los scripts, ejecuta:
> `powershell -ExecutionPolicy Bypass -File .\build.ps1 -SdkRoot C:\Android\sdk`

### Nota sobre JDK nuevos y D8

El D8 que viene en `build-tools` 34 puede fallar con bytecode generado por JDK muy nuevos (por ejemplo, el JDK 25), con un `NullPointerException` al convertir clases anónimas. Hay dos salidas:

1. Compilar con **JDK 17**; o
2. Pasar un R8/D8 más reciente, del repositorio Maven de Google:

```powershell
Invoke-WebRequest "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.7.18/r8-8.7.18.jar" -OutFile r8.jar
.\build.ps1 -SdkRoot C:\Android\sdk -R8Jar .\r8.jar
```

### En Linux o macOS

El script es para Windows, pero los pasos son los mismos: `aapt2 compile` → `aapt2 link` → `javac --release 8` → `d8` → añadir `classes.dex` al APK → `zipalign` → `apksigner sign`. Lee `build.ps1` como referencia.

## Instalar en el televisor

### 1. Activar el modo desarrollador en el televisor

1. **Ajustes → Preferencias del dispositivo → Información**.
2. Pulsa **7 veces** en **Compilación** (o "Versión del SO") hasta que aparezca *"Ahora eres desarrollador"*.
3. Vuelve y abre **Preferencias del dispositivo → Opciones para desarrolladores**.
4. Activa la **Depuración por USB** y, si existe, la **Depuración por red/Wi-Fi (ADB)**.

En muchos televisores, activar la depuración por USB ya abre el puerto ADB en la red (puerto 5555), incluso sin la opción de red por separado.

### 2. Averiguar la IP del televisor

**Ajustes → Red e Internet →** conexión actual. Anota la dirección IP (algo como `192.168.0.50`).

### 3. Conectar e instalar

```powershell
adb connect 192.168.0.50:5555
adb devices
adb install -r build\androidtv-lite.apk
```

En la primera conexión, el televisor muestra el aviso **"¿Permitir la depuración por USB?"**. Marca **"Permitir siempre desde este ordenador"** y confirma. Si `adb devices` muestra `unauthorized`, acepta el aviso en el televisor y conéctate de nuevo.

### 4. Abrirlo

La app aparece en la lista de apps del televisor como **Launcher Ligero** (o el nombre traducido). Ya puedes abrirla desde ahí sin establecerla como predeterminada. No se ha cambiado nada más en el televisor.

## Establecerlo como launcher predeterminado

```powershell
adb shell cmd package set-home-activity com.androidtvlite.launcher/.MainActivity
```

Esto registra el launcher como pantalla de inicio preferida. El televisor debería abrirse en él al encenderse.

### Si el botón Inicio sigue abriendo el launcher original

Algunos fabricantes llaman directamente al launcher original cuando pulsas **Inicio**, ignorando la preferencia. Fue lo que ocurrió en el televisor TCL donde se desarrolló esto. Para forzar que Inicio caiga en este launcher hay que **desactivar el launcher original** solo para tu usuario:

```powershell
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Antes de hacerlo, ten en cuenta que:

- Es un launcher de Google y otras partes del sistema pueden intentar abrirlo.
- Si este launcher falla con el original desactivado, el televisor muestra una pantalla de recuperación del fabricante. Aun así puedes revertirlo desde el ordenador (comandos más abajo).
- Las actualizaciones del sistema o de Play Store pueden reactivarlo.
- El nombre del paquete del launcher original puede ser distinto en tu televisor. Compruébalo con `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`.

## Volver al launcher original / desinstalar

```powershell
# Reactivar el launcher original (si lo desactivaste)
adb shell pm enable com.google.android.tvlauncher

# Devolver la preferencia al original
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity

# Quitar este launcher
adb shell pm uninstall com.androidtvlite.launcher
```

Si algo sale muy mal, un **restablecimiento de fábrica** del televisor lo restaura todo.

## Personalizar

La interfaz y la lógica están en `src/com/androidtvlite/launcher/MainActivity.java`, y las reglas de orden (lista predeterminada, orden guardado, movimientos, navegación de la cuadrícula) en `HomeOrder.java`. Tras editar, ejecuta `build.ps1` y reinstala con `adb install -r`.

| Qué cambiar | Dónde |
|---|---|
| Apps de streaming "conocidas" | constante `PREFERRED` (lista de nombres de paquete) |
| Número de columnas de la cuadrícula | constante `COLUMNS` |
| Tamaño de las tarjetas | `cardW` y `cardH` en `makeTile` |
| Zoom al enfocar | `1.15f` en `makeTile` (y `1.04f` en `makeTab`) |
| Elementos de la barra de navegación | llamadas a `makeTab(...)` en `onCreate` |
| Textos (barra, nombre de la app) | `res/values*/strings.xml` |
| Oscurecimiento sobre el fondo | el `GradientDrawable` de `scrim` en `onCreate` |
| Imagen de fondo | `res/drawable-nodpi/bg.jpg` |
| Reglas de orden (orden guardado, apps nuevas y ocultas) | `HomeOrder.merge` en `HomeOrder.java` |

### Ejecutar las pruebas de HomeOrder

`HomeOrder` no depende de Android, así que las pruebas se ejecutan solo con el JDK (sin SDK ni Gradle). Desde la raíz del proyecto:

```powershell
javac -d build\test src\com\androidtvlite\launcher\HomeOrder.java test\com\androidtvlite\launcher\HomeOrderTest.java
java -cp build\test com.androidtvlite.launcher.HomeOrderTest
```

Si todo pasa, el comando imprime `OK`. Una comprobación que falla lanza un `AssertionError` con el nombre del caso.

### Cambiar la imagen de fondo

Sustituye `res/drawable-nodpi/bg.jpg` por una imagen tuya y vuelve a compilar. Consejos:

- **1280 px de ancho** es suficiente y usa poca memoria. Evita las imágenes 4K, que gastan RAM sin necesidad.
- Formato JPEG, calidad en torno a 85.
- El fondo se recorta para llenar la pantalla (`centerCrop`), así que mantén el motivo principal en el centro.
- La foto incluida en el repositorio (un pueblo costero al atardecer) es la elegida por el autor del proyecto. **No está cubierta por la licencia MIT**: los derechos pertenecen al fotógrafo, y desconozco la licencia original. Si eres el propietario y quieres que se retire, o si vas a redistribuir el proyecto, sustituye el archivo por una imagen tuya o de licencia libre.
- Si usas una foto de terceros, respeta su licencia.

## Cómo elige las apps

Antes de personalizar la pantalla de inicio, vale el orden predeterminado:

1. Pregunta al sistema por las apps con la categoría `LEANBACK_LAUNCHER` (las hechas para TV).
2. Omite el propio launcher, Google Play y Ajustes (que ya están en la barra).
3. Conserva una app si está en la lista `PREFERRED` **o** si **no es una app del sistema**, es decir, cualquier app que instales. Ordena por nombre, ignorando acentos y mayúsculas.

Tras la primera personalización, vale el orden guardado:

4. Las apps que instales después entran al final de Inicio.
5. Las apps ocultas (quitadas de Inicio) no vuelven solas. Para recuperarlas, usa **Añadir apps**.
6. **Añadir apps** lista todas las apps de TV que están fuera de Inicio, incluidas las del sistema del fabricante y de Google, en orden alfabético. Solo quedan fuera Google Play, Ajustes y el propio launcher.
7. La lista se rehace cada vez que vuelves a la pantalla de inicio, así que una app recién instalada aparece sin reiniciar.

## Limitaciones conocidas

- Las **apps solo para móvil** (sin versión para Android TV) no aparecen. Es una regla de Android; ningún launcher de TV las muestra.
- **Sin búsqueda, sin recomendaciones, sin "seguir viendo".** Es a propósito: el objetivo es ser ligero.
- **Sin widgets y sin canales de la pantalla de inicio** de Android TV.
- **Sin icono de app propio** en la entrada del launcher.
- El comportamiento del botón **Inicio** y del arranque varía entre fabricantes. Consulta la sección sobre el launcher predeterminado.
- Los tamaños están en `dp`, pero el diseño solo se comprobó en un televisor que renderiza a 1280×720. En otras pantallas puede ser necesario ajustar `cardW`/`cardH` y los márgenes.

## Solución de problemas

| Síntoma | Qué hacer |
|---|---|
| `adb connect` falla | Comprueba la IP, que el televisor y el PC estén en la misma red y que la depuración esté activa. Prueba a desactivar y activar la depuración por USB. |
| `adb devices` muestra `unauthorized` | Acepta el aviso "Permitir depuración" en la pantalla del televisor y conéctate de nuevo. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | La copia instalada se firmó con otra clave. Desinstálala antes: `adb shell pm uninstall com.androidtvlite.launcher`. A partir de ahí, conserva `build\debug.keystore`. |
| La app no aparece en la lista de apps | Comprueba con `adb shell pm list packages com.androidtvlite.launcher`. |
| Inicio abre el launcher original | Consulta [Si el botón Inicio sigue abriendo el launcher original](#si-el-botón-inicio-sigue-abriendo-el-launcher-original). |
| No aparece una app de streaming | Necesita la categoría `LEANBACK_LAUNCHER`. Comprueba con `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER`. |
| `build.ps1` falla en D8 con `NullPointerException` | Usa JDK 17 o pasa `-R8Jar` (consulta la [nota sobre JDK nuevos y D8](#nota-sobre-jdk-nuevos-y-d8)). |
| El texto está en el idioma equivocado | El launcher sigue el idioma del sistema del televisor. Cámbialo en los ajustes del televisor; los idiomas sin traducción muestran inglés. |
| Necesito recuperar ya el launcher anterior | `adb shell pm enable com.google.android.tvlauncher` y después el `set-home-activity` del original. |

## Estructura del proyecto

```
androidtv-lite/
├── AndroidManifest.xml                       # actividad HOME + LEANBACK_LAUNCHER; único permiso: REQUEST_DELETE_PACKAGES
├── build.ps1                                 # compila y firma el APK (sin Gradle)
├── docs/screenshots/                         # imágenes del README
├── res/
│   ├── drawable-nodpi/bg.jpg                 # fondo (foto, fuera de la licencia MIT)
│   ├── values/strings.xml                    # inglés (predeterminado)
│   ├── values-pt/strings.xml                 # portugués
│   └── values-es/strings.xml                 # español
├── src/com/androidtvlite/launcher/
│   ├── HomeOrder.java                        # reglas de orden (Java puro, sin APIs de Android)
│   └── MainActivity.java                     # toda la interfaz y la lógica (~1050 líneas)
└── test/com/androidtvlite/launcher/
    └── HomeOrderTest.java                    # pruebas de HomeOrder (Java puro)
```

Toda la interfaz se construye en código (sin XML de diseño), lo que mantiene la app pequeña. El APK final pesa unos 200 KB, casi todo es la foto de fondo (el código en sí ocupa unas pocas decenas de KB).

## Licencia

[MIT](LICENSE) para el **código**. Úsalo, cópialo y modifícalo libremente.

La licencia MIT **no cubre** la imagen de fondo `res/drawable-nodpi/bg.jpg` (consulta la sección sobre cómo cambiarla).

Las marcas y logotipos de las apps mostradas (Netflix, YouTube, Prime Video, Globoplay, Disney+, Apple TV, Crunchyroll y otras) pertenecen a sus respectivos dueños. Aparecen únicamente porque cada app proporciona su propio banner al sistema.
