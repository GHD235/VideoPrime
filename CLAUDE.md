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
1. YouTube / YT Music sólidos + vigilancia diaria (`.github/workflows/videoprime-watch.yml` en
   VideoPrime: `VideoPrimeSmokeTest` del extractor con red real + aviso de novedades upstream).
2. Renombrado (nombre + applicationId) y CI que genere APK descargable.
3. Rediseño de la interfaz en Compose.
4. Importar desde Spotify.
5. Twitch como nuevo servicio del extractor. (Hecho lo básico; faltan chat, clips, categorías.)
6. Cuenta propia + sincronización.

## Pendiente del usuario
- Lanzar a mano una vez "VideoPrime: vigilancia" (Actions → Run workflow) en VideoPrime.
  Ya no hace falta tocar nada en NewPipeExtractor (el workflow vive en VideoPrime).
- Más adelante, abrir red para Spotify (api.spotify.com, accounts.spotify.com, open.spotify.com).
  (Hecho: secret del keystore; YouTube y Twitch ya son accesibles desde el entorno.)
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
  Paso 2 hecho: orden Inicio, Suscripciones, Directos, Biblioteca (el usuario quiere
  Suscripciones antes que Directos). Inicio nuevo en Compose (`videoprime/HomeFragment.kt`,
  `HomeScreen.kt`, `VideoPrimeTheme.kt`): chips Todo/YouTube/Twitch, carrusel "En directo
  ahora" (Twitch preguntado al momento con `TwitchLiveChannels`; YouTube = directos del feed de
  <12 h) y tarjetas grandes con avatar. Directos (`LiveFragment`) con chips Twitch/YouTube.
  Paleta oscura del boceto (#0F0E13) y cabecera oscura (`?attr/toolbar_background`).
  Compose activado en el módulo app (plugin kotlin compose + jetbrains compose).
  Pendiente: reproductor con botones grandes, Biblioteca como el boceto A3 ("Seguir viendo"),
  tipografía Plus Jakarta Sans, Suscripciones con el mismo estilo.
- 2026-10-18 (6): TV de España (TDTChannels) implementada y luego QUITADA por decisión del
  usuario: Atresmedia y Mediaset emiten con DRM (no se reproducen en la app ni se va a saltar
  el DRM) y abrir Mitele/Atresplayer le daba errores. Commits revertidos en ambos repos
  (servicio TV id 6 en el extractor; chip TV en el Inicio). Se puede recuperar del historial.
- 2026-10-18 (7): Reproductor de descargas inspirado en Musicolet (sin copiar su código ni su
  arte: es propietario). Servicio `Local` (id 7) en el extractor solo para reconocer URLs
  `file://`/`content://`; `ExtractorHelper.getStreamInfo` construye el StreamInfo en la app con
  `videoprime/LocalMedia.kt` (registro de descargas + historial para título/canal/miniatura).
  Biblioteca → Descargas abre `DownloadsFragment` (Carpetas / Todo, buscar, reproducir todo,
  aleatorio; vídeo → reproductor principal, audio → segundo plano). Varias colas: Biblioteca →
  Colas (`QueuesFragment`, `QueueStore` en `videoprime_queues.json`): guardar la cola actual,
  cambiar entre colas (guarda la activa con elemento y posición y retoma la otra), renombrar,
  borrar. `PlayerHolder` expone `getCurrentPlayQueue()`/`getCurrentPositionMillis()`.
  Modo coche (Android Auto): `player/mediabrowser/CarMedia.kt` añade Novedades, En directo
  (Twitch de tus canales), Descargas por carpetas y Colas a la raíz del navegador multimedia
  heredado de NewPipe. Todo suena en el reproductor de segundo plano: los vídeos van como AUDIO
  (no se muestra vídeo conduciendo, decisión firme). Android Auto solo muestra apps
  instaladas fuera de Play si se activan "Fuentes desconocidas" en sus ajustes de desarrollador.
  Pendiente: pantalla "modo coche" dentro de la app (botones grandes) si el usuario la quiere.
- 2026-10-18 (8): Textos visibles con "NewPipe" → "VideoPrime" en todos los idiomas (notificación
  de descarga, canal de notificaciones, errores...; script de nombres concretos, se dejan los
  que hablan de NewPipe de verdad: donar, web, privacidad, KAO, Android 5, migraciones).
  Carpeta por defecto de descargas "VideoPrime" y copia de seguridad "VideoPrimeData-*.zip".
  Comprobado: la APK nightly 46b94f5 NO usa el keystore fijo → el secret sigue sin crear
  (por eso hay que desinstalar). Mientras: Ajustes → Copia de seguridad → Exportar/Importar.
  Bocetos de Descargas nuevas (https://claude.ai/artifact/BP89K2zPVYNoezXRNkpKgB): pestañas
  Música / Vídeos / Carpetas, descarga en curso arriba, selección múltiple con Borrar (con
  confirmación y espacio liberado), menú de archivo (ver, solo audio, a una cola, original,
  compartir, borrar) y reproductor de música con chips de colas y botones grandes.
  El usuario ya creó el secret del keystore (2026-10-18 20:30): desde la siguiente APK se
  actualiza encima sin desinstalar.
- 2026-10-18 (9): Implementados los bocetos. Barra inferior movida a `activity_main.xml` (por
  encima del bottom sheet del reproductor): `videoprime/BottomBar.kt`; `VideoDetailFragment`
  usa peek = mini reproductor + barra (`getPeekHeight()`/`updatePeekHeight()`) y la barra se
  desliza fuera al expandir. `MainFragment` la muestra en `initViews` y la oculta en
  `onDestroyView` (solo se ve en la pantalla principal). Descargas: pestañas Música / Vídeos /
  Carpetas, descarga en curso arriba (se enlaza a `DownloadManagerService` y sondea cada 1 s;
  `DownloadManager.getPendingMissions()`/`deleteFinishedMission(uri)` añadidos), selección
  múltiple con pulsación larga (reproducir, a una cola, compartir, borrar con confirmación),
  menú de archivo en hoja inferior. Reproductor de música: `videoprime/MusicPlayerActivity.kt`
  (+ `MusicPlayerScreen.kt`, `SleepTimer.kt`, `QueueSwitcher.kt` compartido con Colas): lo abre
  la notificación del reproductor de segundo plano y el botón de cola del mini reproductor
  cuando suena en segundo plano; chips de colas, temporizador para dormir, "A continuación"
  abre el editor de cola de NewPipe. `Player.setActivityListener` pasó a público.
  Pendiente: caché/modo sin conexión (propuesta al usuario: canciones escuchadas + listas
  marcadas "Disponible sin conexión", descargadas en segundo plano a una carpeta privada con
  límite de espacio). La skill "Rea" que menciona el usuario no aparece en la sesión.
- 2026-10-18 (10): "Rea" = https://github.com/morluto/rea (REA, MCP de ingeniería inversa:
  Ghidra/JADX/JS). No instalado: se podría usar con JADX sobre APKs (Musicolet, TDTSpain) para
  entender funciones, sin copiar código propietario. Búsqueda: barra Compose de plataformas
  (`videoprime/SearchPlatformBar.kt`): YouTube · YouTube Music · Twitch (YT Music = YouTube con
  filtros `music_*`, recuerda la última en la pref `videoprime_search_music`) y fila de filtros
  de cada plataforma; quitado el menú de filtros y el botón de cambiar servicio de
  `SearchFragment`. Suscripciones nuevas en Compose (`ChannelsFragment`/`ChannelsScreen`):
  chips Todo/YouTube/Twitch con número, "En directo ahora" (Twitch), rejilla de avatares por
  plataforma, buscador a partir de 12 canales, pulsación larga = compartir/navegador/dejar de
  seguir; menú: importar (copia anterior o YouTube), exportar, "Grupos de canales" (abre el
  `SubscriptionFragment` de NewPipe). `Tab.SubscriptionsTab.getFragment` devuelve `Fragment`.
  CI: el paso de la clave quita espacios del secret y explica si no es válido.
  Secret del keystore OK desde ae17402 (firma C8:72:CC:74…): ya no hay que desinstalar.
  El usuario solo quiere la variante Continuous por ahora (la "release" la lanzará él más
  adelante para otros usuarios; necesitará su propia clave y aviso de actualizaciones).
- 2026-10-18 (11): Música sin conexión (`videoprime/OfflineMusic.kt` + `OfflineMusicWorker.kt`
  + `OfflineFragment.kt`, Biblioteca → Sin conexión). Índice JSON en
  `files/videoprime_offline.json`, audios en `getExternalFilesDir()/offline_music` (privado).
  Se guarda: lo que suena en segundo plano o es música (categoría Music / canal " - Topic"),
  de 1 s a 20 min, y todas las canciones de listas locales, listas de YouTube y colas marcadas
  "Disponible sin conexión" (menú de la lista / de la cola). Límite por defecto 2 GB
  (500 MB-10 GB), solo Wi-Fi por defecto, se borran primero las no fijadas escuchadas hace más
  tiempo. WorkManager único (KEEP) que baja en trozos de 1 MB con Range (M4A original de más
  calidad). Reproducción: `ExtractorHelper.getStreamInfo` usa la copia sin red o si la petición
  falla; `PlaybackResolver` trata `file:` de YouTube como progresivo normal. Sin probar en
  móvil ni contra YouTube real (sin red a YouTube en el entorno).
- 2026-10-18 (12): Rendimiento (el usuario notaba más retraso al reproducir). Medido con red real
  (ahora el entorno sí llega a youtube.com/twitch): cada vídeo de YouTube hacía 5 peticiones en
  serie (~2,8 s). Extractor `15e94f7d`: VISIONOS, WEB (metadatos) y `next` en paralelo
  (`YoutubeParsingHelper.inParallel/awaitParallel`, hilos daemon) y visitorData en caché 30 min
  (`clearVisitorDataCache()`, llamado en `YoutubeTestsUtils.ensureStateless`). Resultado
  0,7-2,2 s; lo más lento ahora es `next` (0,5-3,9 s, depende de YouTube). Tests MOCK: mismos
  9 fallos de siempre. Twitch: kiosco 0,6 s, directo 0,3-0,9 s (bien). La música sin conexión
  espera 90 s antes de guardar una canción escuchada para no quitar ancho de banda al player.
  Desde la IP del entorno YouTube devuelve "no disponible" para vídeos VEVO (Despacito, Gangnam)
  con cualquier cliente (bloqueo de IP, no de la app) y YouTube Music no ofrece Canciones ni
  Álbumes (solo vídeos/perfiles/episodios): si "Canciones" sale vacío, la búsqueda pasa sola a
  "Vídeos" de YT Music. Upstream (NewPipe y NewPipeExtractor dev) sin cambios nuevos a 18-10.
  Colores: chips de la búsqueda en violeta, panel de sugerencias con el fondo de la app, barra de
  estado vuelve a `colorPrimaryDark` (no al violeta) al salir del reproductor.
  Vigilancia diaria rehecha (13): workflow en VideoPrime (cron 5:17 UTC + manual) que ejecuta
  `extractor/src/test/.../videoprime/VideoPrimeSmokeTest.java` (solo con env VIDEOPRIME_SMOKE;
  YouTube: 6 vídeos (falla si más de la mitad no cargan) + descarga real de 64 KB del audio,
  búsqueda, YT Music vídeos (canciones solo se informan), canal @Gronkh, una lista; Twitch:
  kiosco, directo, canal y VOD) y abre/cierra issues con etiquetas `rotura-extractor` y
  `novedades-newpipe` (compara `videoprime...TeamNewPipe:dev` de ambos repos con la API; el
  proxy del entorno no deja probar esa comparación). Se borró el workflow viejo del extractor.
  (14) Twitch: `TwitchParsingHelper.getImages` reemplazaba "{width}" antes que "%{width}"
  (dejaba "%640") y las VOD en proceso apuntan a `_404/404_processing` (403): ahora sin imagen.
  Inicio muestra `placeholder_thumbnail_video` si la miniatura falla. Cabecera de listas
  modernizada (`playlist_control.xml`: botón violeta "Reproducir todo", "Segundo plano" tonal y
  círculo "Emergente"; títulos 24sp negrita; sin fondo de contraste). Usuario ya activó Issues.
  (15) Primera vigilancia (run 39165132869): Twitch, búsquedas, YT Music (20 canciones desde la
  IP de GitHub: lo de 0 canciones era la IP del entorno), canal y lista OK; 5/6 vídeos con
  "confirma que no eres un bot" (IP de GitHub bloqueada) → issue #1 falso. Ahora esos bloqueos no
  cuentan (se informan; si todo bloqueado, se omite). Issue #1 se cerrará solo en la siguiente
  ejecución correcta. github-script v8.
  Herramienta útil: medir tiempos con un test temporal `-Ddownloader=REAL` que envuelve el
  Downloader y registra cada petición (no se sube).
- 2026-10-18/19 (16): Biblioteca A en Compose (`videoprime/LibraryFragment.kt`/`LibraryScreen.kt`:
  accesos Descargas/Sin conexión/Colas/Historial + rejilla "Tus listas" con mosaico; pulsación
  larga = renombrar, sin conexión, borrar). Ajustes en ⚙ arriba en todas las secciones; "Acerca
  de" pasa a Ajustes. Barra inferior (`BottomBar.kt`) siempre visible en toda la app (se oculta
  con el teclado): ya no "baila" al entrar en opciones de la Biblioteca. Directos con chips
  Compose. Sin conexión: WorkManager APPEND_OR_REPLACE, tope 8 min por tanda, reintento tras 3
  errores de red, aviso de canciones de listas pendientes; no se encola nada si no hay nada que
  guardar y al quitar la última lista se liberan sus canciones.
  Noche de pruebas en emulador (ver abajo). Arreglos: abrir un enlace con "Segundo plano/
  Emergente/Encolar" no hacía nada (`ExtractorHelper.getStreamInfo` NO debe llevar
  `subscribeOn`: el FetcherService del RouterActivity espera ejecución síncrona); "+ Nueva cola"
  y el temporizador no funcionaban si la reproducción empezó fuera de la app
  (`PlayerService.getCurrentPlayer()` estático, `QueueSwitcher.playing()`); descargar desde
  enlace decía "URL no compatible" ante cualquier error; Twitch sin pulgares "Desactivado" y con
  "seguidores"; reproductor de música con barra de estado del color de la pantalla y "En
  directo" en vez de barra de progreso. Extractor: limitador del descargador de tests con
  ConcurrentHashMap (las peticiones de YouTube van en paralelo) y vigilancia con
  `youtubeRecentVideosDownload` (descarga real del audio de 6 vídeos recientes buscados) + resumen
  como anotación (`gh api repos/GHD235/VideoPrime/check-runs/<job>/annotations`, los logs no se
  pueden bajar por el proxy).
  OJO: desde la IP del entorno y la de GitHub, el audio de casi cualquier vídeo reciente da 403
  (también con el extractor upstream sin tocar): bloqueo de IP de centro de datos, no de la app.
  Los vídeos antiguos famosos (dQw4w9WgXcQ) sí bajan: usarlos para probar reproducción.
- 2026-10-19 (17): Android Auto con 4 pestañas: Lista de reproducción, Novedades, Descargas,
  Historial (fuera Directos y Colas, borrados de `CarMedia.kt`). En el reproductor del coche el
  aleatorio va siempre primero (`MediaSessionPlayerUi`: SHUFFLE + los 2 huecos de la
  notificación) y el botón "Poner en la cola" (la cola de la sesión) muestra hasta 100 canciones
  (`PlayQueueNavigator.MAX_QUEUE_SIZE`). Reproductor de música: el icono play/pausa sigue el
  estado que informa el reproductor (`isPlaying` aún no ha cambiado al recibir el aviso).
- 2026-10-19 (18): Inicio más rápido: pinta al momento lo guardado en el móvil y pide a Twitch
  quién está en directo después (cada 60 s como mucho, en memoria); durante la actualización
  recarga cada 2 s para que los vídeos nuevos vayan apareciendo; `FeedLoadManager` con 6
  extracciones en paralelo (antes 3) e inserciones cada 10 canales. Reproducción continua:
  salir de la app viendo un vídeo abre el emergente (valor por defecto + migración única
  `videoprime_popup_on_exit_done`; sin permiso de superposición pasa a solo audio);
  principal ↔ emergente ya no recarga el stream (`Player.handleIntentPost`, ambos son vídeo);
  cerrar la app desde recientes: el emergente y el audio siguen, y el reproductor principal pasa
  a segundo plano en vez de matar la app (`PlayerService.onTaskRemoved`). Anuncios de Twitch:
  sin hacer (la pantalla "Commercial break" es el relleno de Twitch durante la pausa del
  streamer; quitarlo sería pedir otra variante de la lista HLS o filtrar segmentos, juego del
  gato y el ratón; pendiente de que el usuario decida).
- 2026-10-19 (19): Mejoras marcadas por el usuario en el doc "VideoPrime — mejoras y viabilidad"
  (https://claude.ai/code/artifact/ca8ed763-b518-4a19-aea3-c8976ee0a80e, columna Decisión).
  Hechas: carátula cuadrada (`videoprime/CoverArt.kt`, recorta bandas planas en
  `Player.onThumbnailLoaded`), botones por defecto de la notificación con aleatorio, colores de
  la portada en el reproductor de música, volumen igualado (`StreamInfo.getLoudnessDb()` del
  extractor + `Player.applyLoudnessNormalization`, opción "Igualar el volumen"), siguiente
  canción preparada 90 s antes (`MediaSourceManager`), "Sin conexión" dentro de Descargas en el
  coche y búsqueda por voz (`onPrepareFromSearch` → `CarMedia.search`, YT Music canciones o
  vídeos), Twitch relacionados (últimos 20 vídeos del canal), aviso de nueva versión
  (`videoprime/UpdateChecker.kt`: release nightly de GitHub vs `BuildConfig.GIT_COMMIT`, cada
  12 h desde el worker), reproductor de vídeo con botones redondos grandes y barra violeta
  (`player.xml`, `videoprime_player_circle`). Sin marcar (no hacer aún): Favoritas, velocidad/A-B,
  ecualizador, letras, widget, listas automáticas, todo lo de Spotify, etc.
  Segundo plano: `NotificationWorker` actualiza TODAS las suscripciones cada hora con cualquier
  red (sin notificación de "cargando"), avisa de vídeos de <2 días de canales con notificaciones
  (activadas para todos una vez + las suscripciones nuevas) y de directos de Twitch
  (`videoprime/LiveNotifications.kt`). Twitch: `playerType: "popout"` en vez de "site": con "site"
  14/30 directos traían un anuncio de 30 s cosido (segmentos "Amazon|..."); con popout 0/30 y sin
  pausas en 30 min de vigilancia. Herramienta: `scratchpad/twitch/probe.py` (no se sube).
- 2026-10-19 (20): Del vídeo del usuario: volver del emergente ya no fuerza pantalla completa
  (`NavigationHelper.openVideoDetailFragment`) y no se queda en pausa (usa `getPlayWhenReady()`
  del reproductor abierto, no `isPlaying()`); el emergente se lanza en `onUserLeaveHint`
  (`MainPlayerUi.onUserLeavingApp`) en vez de esperar a `onStop` + broadcast. PiP de Android
  (como Twitch) descartado por ahora: MainActivity no declara configChanges y entrar en PiP la
  recrearía. Iconos play/pausa (mini reproductor y reproductor de música) con
  `getPlayWhenReady()` (cambia en el toque). Listas (locales y de YouTube): un solo botón
  "Reproducir todo" y tocar una canción = segundo plano + abre el reproductor de música
  (`videoprime/MusicPlayback.kt`; `PlayButtonHelper` con `musicOnly`; `MusicPlayerActivity`
  espera hasta 5 s a que exista el reproductor). Barra inferior: al volver de una pantalla
  profunda a otra sección ya no parpadea la sección anterior (`MainFragment.sectionToOpen`).
  Carpeta de descargas inválida: se olvida en silencio (antes un toast en cada apertura).
- Emulador (sin KVM, lento ~10-15 min de arranque): AVD "vp" (android-30, x86_64) en
  /opt/android-sdk; lanzar `emulator -avd vp -no-window -no-audio -no-boot-anim -accel off
  -gpu swiftshader_indirect -no-snapshot -memory 3072 -cores 4` SIN `-http-proxy` (si el proxy
  cambia de puerto deja al emulador sin red). Red: `adb shell settings put global http_proxy
  10.0.2.2:<puerto de $HTTPS_PROXY>` + CA del proxy montada en tmpfs sobre
  /system/etc/security/cacerts (`adb root`; NO usar `adb remount`/-writable-system: no arranca).
  Script `ui.py` (uiautomator: tap por texto, capturas). Tras arrancar, system_server puede
  reiniciarse solo y salir "System UI isn't responding" (pulsar Wait). El selector de carpetas
  del sistema (DocumentsUI) no responde en este emulador: las descargas no se pudieron probar.
  En el emulador la red cuenta como datos móviles: no hay reproducción automática.
- La UI final se prueba en el móvil del usuario (Xiaomi).
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
