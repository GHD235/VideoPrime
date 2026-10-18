# VideoPrime — notas para Claude

Fork de NewPipe (GPLv3) mantenido por GHD235. Este archivo es la memoria del proyecto
entre sesiones: léelo entero al empezar y actualízalo al terminar cada bloque de trabajo.
El usuario habla español.

## Repos
- `GHD235/VideoPrime` — la app (fork de `TeamNewPipe/NewPipe`). Rama de trabajo: `videoprime`.
  `dev` se deja como espejo del upstream (botón "Sync fork") para poder integrar sus cambios.
- `GHD235/NewPipeExtractor` — el extractor (fork de `TeamNewPipe/NewPipeExtractor`).
  Misma convención: `dev` espejo, `videoprime` nuestra rama.
- La app compila el extractor desde el submódulo `NewPipeExtractor/` (nuestro fork, rama
  `videoprime`), no desde JitPack (JitPack fallaba con 429 de Maven Central y cachea fallos).
  Para actualizar el extractor en la app: `git -C NewPipeExtractor pull origin videoprime`
  y commitear el nuevo puntero del submódulo. Clonar con `--recurse-submodules`.
- Upstream `refactor` (rediseño Kotlin/Compose de NewPipe) sirve de referencia; el `dev`
  upstream ya incluye la estructura multiplataforma (`shared/`, `desktopApp/`, `iosApp/`).

## Decisiones tomadas
- Nombre: **VideoPrime**, applicationId `com.ghd235.videoprime` (confirmado 2026-10-18).
  El namespace/paquete del código sigue siendo `org.schabi.newpipe` para no mover miles de
  archivos y poder seguir integrando upstream. Constantes en `buildSrc/.../ProjectConfig.kt`.
- Partimos de la base de NewPipe y la modificamos a nuestro gusto (no app desde cero).
- Interfaz moderna: Kotlin + Jetpack Compose + Material 3 (color dinámico).
- Cuenta propia de la app para sincronizar suscripciones/historial/listas.
  NO login con Google/YouTube (riesgo de baneo de la cuenta y contrario a la privacidad).
  Requerirá backend propio (por decidir: Supabase o VPS) + política de privacidad (RGPD).
- Spotify solo como fuente de metadatos (importar playlists vía Web API con PKCE y emparejar
  con YouTube Music). Nunca extraer audio con DRM.
- Nada de plataformas con DRM (Netflix, Prime, Disney+, Apple Music, Tidal, Deezer).

## Hoja de ruta
1. YouTube / YT Music sólidos + vigilancia diaria de roturas del extractor (Action que abre issue).
2. Renombrado (nombre + applicationId) y CI que genere APK descargable.
3. Rediseño de la interfaz en Compose.
4. Importar desde Spotify.
5. Twitch como nuevo servicio del extractor. (Hecho lo básico; faltan chat, clips, categorías.)
6. Cuenta propia + sincronización.

## Pendiente del usuario
- NewPipeExtractor: cambiar la rama por defecto a `videoprime`, activar Issues y lanzar a mano
  "VideoPrime: vigilancia del extractor". (VideoPrime ya está hecho: rama por defecto
  `videoprime`, Actions activo.) GitHub solo ejecuta los `schedule` desde la rama por defecto.
- Activar Issues en VideoPrime: el botón "Informar en GitHub" de la pantalla de error apunta ahí.
- Crear el secret `VIDEOPRIME_DEBUG_KEYSTORE` en VideoPrime para que las APK nightly tengan
  siempre la misma firma (instrucciones en `docs/videoprime/firma-apk.md`).
- Más adelante, abrir red para Twitch (twitch.tv, gql.twitch.tv, usher.ttvnw.net, ttvnw.net, jtvnw.net) y
  Spotify (api.spotify.com, accounts.spotify.com, open.spotify.com).
- Client ID de Spotify (fase 4).

## Entorno (sesiones en la nube)
- Android SDK: descargar commandlinetools de dl.google.com en /opt/android-sdk y poner
  `sdk.dir` en `local.properties` (ignorado por git). La app usa JDK 21; el extractor, 17.
- Maven Central devuelve 429 a menudo vía el proxy: reintentar Gradle con `--max-workers=2`.
- Hace falta JDK 17 (`apt-get install -y openjdk-17-jdk-headless`); el extractor fija toolchain 17.
- Si jitpack.io / dl.google.com están bloqueados: compilar nanojson
  (`TeamNewPipe/nanojson`, commit fijado en el extractor) con Maven, instalarlo en mavenLocal
  con groupId `com.github.TeamNewPipe` y version = hash del commit, y usar un init script de
  Gradle que añada `mavenLocal()` y quite los repos bloqueados. Ver
  `docs/videoprime/sandbox-build.md`.
- Tests del extractor sin red: `./gradlew :extractor:test -Ddownloader=MOCK` (respuestas
  grabadas en `extractor/src/test/resources/mocks`). Con red real: `-Ddownloader=REAL`.

## Estado
- 2026-10-18 (4): Quitados SoundCloud, PeerTube, Bandcamp y media.ccc.de: en el extractor solo
  se desregistran (`ServiceList.all()` = YouTube + Twitch; su código se queda para no romper
  merges de upstream); en la app se borró todo lo suyo (ajustes, filtros de enlaces, temas,
  textos en todos los idiomas...). La app abre enlaces de twitch.tv.
  Rediseño (boceto A elegido, lienzo: https://claude.ai/artifact/8eGnXJpCjSC9ToHLe8v5P6).
  Paso 1 hecho: `MainFragment` con barra inferior Material 3 y 4 secciones fijas (Inicio = feed
  de suscripciones, Directos = kiosco "live" de Twitch, Suscripciones, Biblioteca =
  `LibraryFragment` con accesos + listas); menú lateral bloqueado y sin hamburguesa; ajustes de
  pestañas quitados; botón "Buscar en YouTube/Twitch" en la búsqueda; colores de la app siempre
  violeta (`videoprime_primary_*`, `ThemeHelper.IGNORE_SERVICE_THEMES`).
  Siguientes pasos del rediseño: inicio con carrusel "En directo ahora" + filtros
  Todo/YouTube/Twitch, reproductor con botones grandes, tarjetas de vídeo grandes, modo oscuro
  por defecto.
- No hay emulador en el entorno (sin /dev/kvm): la UI se prueba en el móvil del usuario.
- 2026-10-18 (3): Twitch (servicio id 5) en el extractor y en la app: directos y VODs por HLS
  (token anónimo GQL + usher.ttvnw.net), canales con pestaña de vídeos (el directo primero),
  búsqueda de canales/vídeos y portada "En directo" (más vistos). Sin chat, clips ni
  sugerencias todavía. Tema morado (#9146FF) y icono `ic_live_tv` en la app.
  Un enlace `twitch.tv/<canal>` abre el directo; la URL de canal es `twitch.tv/<canal>/videos`.
- 2026-10-18 (2): APK nightly funcionando: https://github.com/GHD235/VideoPrime/releases/tag/nightly
  (se instala como "VideoPrime Continuous", `com.ghd235.videoprime.continuous`). Play Protect la
  bloquea por ser de desarrollador desconocido: hay que desactivar el análisis para instalar.
  Identidad visual: logo propio (play redondeado + destello, blanco sobre degradado
  #4F46E5→#A21CAF) en icono, splash, cabecera del menú y "Acerca de". Quitado "Donar" del menú,
  enlaces de FAQ/donación/privacidad de NewPipe en "Acerca de" (se añade "Basado en NewPipe").
  Informes de error: sin email a NewPipe, el botón de GitHub va a los issues de VideoPrime.
  El aviso de nuevas versiones de NewPipe ya está inactivo (solo funciona con su firma).
  Pendiente de herencias: política de privacidad propia, textos que dicen "NewPipe" en strings
  traducidos, banner de Android TV (`newpipe_tv_banner.png`), colores del tema (fase 3).
- 2026-10-18: forks creados. Extractor (`8443c91`) compila. Línea base YouTube en MOCK:
  1411 tests, 9 fallos, 114 omitidos. Fallos: YoutubeSignaturesTest x2 ("Could not parse
  deobfuscation function"), YoutubeChannelTabExtractorTest$AgeRestrictedTabsShorts,
  YoutubeMusicSearchExtractorTest$MusicPlaylists (next page), YoutubeSearchExtractorTest
  (RandomQueryNoMorePages, ShortFormContent), YoutubeStreamExtractorRelatedMixTest,
  YoutubeCommentsExtractorTest$Formatting, YoutubeDashManifestCreatorsTest.
  Sin verificar si también fallan en el CI upstream.
