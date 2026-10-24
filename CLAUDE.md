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
- Uso personal (2026-10-22): la app es solo para el usuario; no se publicará ni compartirá. Como
  mucho, versiones suyas para la tele (Android TV) y para web/ordenador. No hace falta endurecer
  el servidor de avisos para terceros.

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
- Avisos instantáneos: configurado el 2026-10-22 (6 secrets en GitHub, servidor en
  https://videoprime-push.guilleherdo.workers.dev, proyecto Firebase `videoprime-b7b3b`). Activado
  en el móvil: 18 canales; servidor con 13 YouTube (WebSub verificado) y 5 Twitch (EventSub).
  Falta confirmar que llega el primer aviso. Desde el entorno no se llega a *.workers.dev (proxy
  403): lanzar "VideoPrime: estado de avisos" y leer su anotación
  (`gh api repos/GHD235/VideoPrime/check-runs/<job>/annotations`).

## Herramientas de Claude (usar en todas las sesiones)
El usuario pidió (2026-10-21) que estas herramientas se usen siempre de aquí en adelante:
- **Revisor de Kotlin/Java de ECC** (`.claude/agents/kotlin-reviewer.md`, `java-reviewer.md`;
  https://github.com/affaan-m/ECC, MIT): tras cada bloque de cambios en Kotlin/Java, lanzar un agente
  que siga ese prompt sobre el diff (solo lectura) y arreglar lo que se confirme. En su prueba encontró
  2 fallos reales (campana sin `rememberAllStreams`, carrera al cambiar la campana dos veces).
  Skills de referencia: `.claude/skills/kotlin-patterns`, `kotlin-coroutines-flows`,
  `android-clean-architecture`. NO instalar el plugin entero ni sus hooks/MCP (27 hooks con node en
  cada llamada, ~30k tokens de descripciones, `npx` en tiempo de ejecución).
- **r8-analyzer** (`.claude/skills/r8-analyzer`; skill oficial de https://github.com/android/skills,
  Apache-2.0, solo instrucciones y referencias, sin hooks): revisar las reglas de R8/ProGuard
  (`app/proguard-rules.pro` y las de librerías) cuando se toquen dependencias, el extractor o la
  config de release, o si la APK release falla y la debug no. Detecta reglas `-keep` redundantes o
  demasiado amplias y clases que el extractor usa por reflexión. Añadida el 2026-10-21.
- **Firebase** (`.claude/skills/firestore-rules-creation`, `firebase-security-rules-auditor`,
  `firebase-auth-basics`; de https://github.com/firebase/agent-skills, Apache-2.0, solo texto, sin
  hooks; añadidas el 2026-10-22 a petición del usuario): para reglas de seguridad y Auth de
  Firebase (p. ej. el servidor de avisos con FCM o las cuentas de la fase 6). OJO: están pensadas
  para Firestore; si el proyecto usa **Realtime Database**, usar el auditor como guía (lista de
  comprobaciones: escalada de privilegios, create vs update, datos que el propio usuario envía
  como fuente de autoridad, límites de tamaño, tipos) pero escribir y revisar las reglas en el
  formato de RTDB (`database.rules.json` con `.read`/`.write`/`.validate`, `auth.uid`,
  `newData`/`data`), no en `firestore.rules`. VideoPrime hoy no usa Firebase.
- **Playwright** (https://github.com/microsoft/playwright): ya viene en el entorno (Chromium en
  /opt/pw-browsers, no ejecutar `playwright install`). Sirve para web, no para la app Android: usarlo
  para revisar los bocetos HTML (capturas a ancho de móvil) antes de enseñarlos al usuario.
- Probadas y descartadas: **replica-skill** (pensada para clonar webs Next.js; su análisis de reseñas
  quedó en una sola fuente porque el proxy bloquea Reddit/HN/tiendas; resultado útil guardado en
  `docs/videoprime/ideas-usuarios-newpipe.md`) y **claude-octopus** (reparte trabajo a Codex/Gemini/
  Perplexity: sin sus claves no hace nada; ~30 hooks en cada llamada y escribe en `~/.claude`).
  También descartada **REA** (https://github.com/morluto/rea, ingeniería inversa por MCP: JADX/Ghidra;
  91 MB, `npx rea-agents setup` toca la config de Claude): sirve para desmontar apps cerradas, lo que
  choca con la regla de no copiar código propietario, y aquí no se pueden bajar APK ajenas. Para
  analizar nuestra propia APK (p. ej. fallos de R8) basta JADX suelto.
  Al "clonar" algo de apps de referencia: solo funciones y flujos, nunca código, arte ni textos.

## Entorno (sesiones en la nube)
- Android SDK: descargar commandlinetools de dl.google.com en /opt/android-sdk y poner
  `sdk.dir` en `local.properties` (ignorado por git). La app usa JDK 21; el extractor, 17.
- Maven Central devuelve 429 a menudo vía el proxy: reintentar Gradle con `--max-workers=2`, o
  mejor usar el espejo de Google con un init script local (ver Estado, entrada 27).
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
- 2026-10-19 (21): Aleatorio desde coche/controles multimedia: los botones de la sesión
  (`SessionConnectorActionProvider`) llamaban al reproductor con un broadcast implícito; ahora
  ejecutan la acción directamente (`Player.runAction`). Probado en el emulador desde la
  notificación: activar aleatorio deja la canción actual en 1/30 y cambia "A continuación";
  desactivar vuelve al orden original. OJO: la acción real es
  `com.ghd235.videoprime.continuous.player.MainPlayer..player.MainPlayer.ACTION_SHUFFLE`.
- 2026-10-19 (22): Reproductor de música: barra de progreso propia (Canvas; posición interpolada
  por fotograma y valor arrastrado retenido hasta que el reproductor confirma el salto), carátula
  y título con `AnimatedContent` (desliza según el sentido) y fondo con `animateColorAsState` y
  color de portada más vivo (`CoverArt.mainColor`). La notificación usa ese color (`setColor`);
  la tarjeta de la pantalla de bloqueo de Xiaomi/Android 13+ la dibuja el sistema con la
  carátula (no se controla desde la app). Revisión completa (3 agentes: Kotlin, Java,
  extractor) y arreglos: colas que se sobrescribían, escrituras atómicas (`QueueStore.LOCK`,
  `androidx.core.util.AtomicFile`), ids del coche por URL, 403 por canción en sin conexión,
  cerrar la app viendo un vídeo sigue como audio (`VideoDetailFragment.onDestroy`), abrir
  ajustes/compartir no lanza el emergente (`MainPlayerUi.openingAnotherScreen`, puesto por
  `MainActivity.startActivity*`), `BottomBar.install` reinicia `miniPlayerShown`, la carpeta de
  descargas solo se olvida con `SecurityException`, la comprobación horaria en segundo plano usa
  el RSS de YouTube (`FeedLoadManager.startLoading(lightweight = true)`), servicio Local fuera
  del menú de servicios. Extractor `4e7e3844`: visitorData se borra si falla VISIONOS y dura
  10 min, `getClientVersion` sincronizado, Twitch: 403 de directo ya no es "solo suscriptores",
  GQL con errores parciales devuelve los datos, login nulo sin NPE, búsqueda sin páginas vacías
  infinitas. Sin hacer (decidido): de emergente a principal se queda la resolución del
  emergente hasta el siguiente vídeo (recargar cortaría el sonido); audio de Twitch sigue como M4A.
  Bocetos de lista futurista (3 móvil + 2 coche, pendientes de que el usuario elija):
  https://claude.ai/artifact/VRPvnVAQ6hmnNZpBQifsrA. En Android Auto el estilo lo pone el coche;
  se controla carátula, textos, secciones (`EXTRA_CONTENT_STYLE_GROUP_TITLE`) y lista/rejilla
  (`DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_*`).
- 2026-10-20 (23): El usuario eligió el boceto A del móvil ("Cristal neón") y mezclar Coche A + B.
  Cola nueva en Compose (`videoprime/QueueActivity.kt` + `QueueScreen.kt`), siempre oscura:
  resplandor con el color de la portada, tarjeta de cristal "Sonando ahora" (cambio animado,
  barras de ecualizador, línea de progreso), chips Aleatorio/Repetir/Guardar cola, "A
  continuación" con nº de canciones y tiempo restante, "Ya sonaron (n)" plegable, arrastrar por
  el asa para reordenar (orden local hasta que la cola confirma), deslizar para quitar, pulsación
  larga = poner a continuación / quitar, barra inferior con play degradado. Menú ⋮: añadir a una
  lista y "Vista clásica" (el `PlayQueueActivity` de NewPipe). La abren el reproductor de música,
  el botón de cola y la notificación (`NavigationHelper.getPlayQueueActivityIntent/openPlayQueue`).
  Sigue los cambios de la cola con `playQueue.broadcastReceiver` (el reproductor no los avisa).
  Coche: la pestaña "Lista de reproducción" muestra arriba "Sonando ahora" y "A continuación"
  (6 siguientes con "en m:ss", `CarMedia.nowPlaying`, ids `queue/<i>?url=`; tocar uno salta en la
  misma cola con `CarMedia.playFromQueue`) y debajo "Tus listas" en rejilla de portadas con nº de
  canciones (`CONTENT_STYLE_SINGLE_ITEM` = GRID; sus canciones en lista). Se refresca en cada
  cambio de canción o de cola (`PlayerService.notifyCarQueueChanged` desde
  `MediaSessionPlayerUi`). Probado en el emulador (lista de YouTube en segundo plano): se ve como
  el boceto, el color del resplandor cambia con la portada, arrastrar reordena y deslizar quita
  (además, un gesto rápido quita aunque no llegue al 35 % del ancho). La cabecera solo pone el
  nombre de una cola guardada si la que suena es esa (`QueueSwitcher.isSameQueue`). El coche no
  se pudo probar aquí (hace falta la unidad principal de escritorio de Android Auto).
- 2026-10-20 (24): Actualización de YouTube en segundo plano. Medido con red real (48 canales,
  como la app: canal + pestañas Vídeos/Shorts/Directos…): carga completa 105 peticiones, ≥4,7 MB
  por la red y 31 MB de JSON, 318 ms/canal; RSS 48 peticiones, 0,2 MB y 1,3 MB, 46 ms/canal; el
  RSS tenía el último vídeo del canal en 42/43 (YouTube lo cachea 15 min). Cambios:
  `FeedLoadManager.startLoading(smart)`: canales de YouTube primero por RSS y carga completa solo
  si el RSS trae un vídeo que no está en la BD (o si el canal tiene un directo en el feed, para
  ver cuándo acaba; o si falla el RSS); lo que el RSS muestra y las pestañas no (premieres,
  canales "- Topic") se guarda también. Los Shorts del RSS se ignoran (la pestaña Shorts no da
  fecha y la app nunca los guardaba: si no, parecían nuevos siempre). Lo usan el trabajo en
  segundo plano y el Inicio al abrir; tirar hacia abajo = carga completa. Ritmo por canal en
  segundo plano (`FeedCadence`): trabajo cada 15 min (migración `videoprime_feed_15_min_done`
  cambia 3600→900), canal con vídeo <7 días cada 15 min, <30 días cada hora, resto cada 3 h,
  Twitch cada hora (sus directos los avisa `LiveNotifications` en cada pasada), con datos móviles
  mínimo 30 min. Log `VideoPrimeFeed` con el resumen de cada actualización.
  Extractor `c7be4b23`: el RSS daba los Shorts como youtube.com/shorts/ID (las pestañas usan
  watch?v=ID) → se guardaban dos veces; ahora watch + `isShortFormContent`. La migración borra
  esas copias (`FeedDAO.unlinkShortsUrlDuplicates`).
  Arreglado también: `OfflineMusic.schedule` encadenaba un trabajo por canción mientras esperaba
  Wi-Fi (375 bloqueados en el emulador); ahora KEEP salvo si uno está corriendo (entonces uno
  encadenado como mucho) y REPLACE si ya hay una cadena.
  Pruebas en emulador (50 canales reales; el emulador es ~10-20× más lento que un móvil, comparar
  entre sí): completa 515 s; inteligente al abrir 306 s (38 RSS + 12 completos); segundo plano
  sin novedades 82 s (30 activos: 23 RSS + 7 con directo); se quitaron de la BD los últimos
  vídeos de 3 canales → la pasada siguiente detectó exactamente esos 3 y mostró sus 3
  notificaciones con duración correcta; 0 duplicados, 0 URLs /shorts/; un canal roto cae a carga
  completa y no afecta al resto. Herramientas: `scratchpad/emu/runworker.sh` (fuerza el trabajo:
  pone `last_enqueue_time=0` en workdb, si no WorkManager lo ignora; no usar force-stop, cancela
  los trabajos) y `scratchpad/FeedBenchmarkTmpTest.java` (no se suben). Emulador: lanzar con
  timeout de 2 h máx. (se corta al llegar).
- 2026-10-20 (25): Fotos del usuario: Ajustes → Notificaciones "Canales 9/15" (importar
  suscripciones las guardaba con la campana apagada y reimportar apagaba la de un canal ya
  seguido: `SubscriptionManager.upsertAll` ahora ENABLED y `SubscriptionDAO.upsertAll` conserva la
  campana; migración única `videoprime_imported_bells_done` vuelve a encender todas) y la app no
  estaba sin restricciones de batería. `videoprime/BackgroundPermission.kt`: diálogo una vez al
  abrir (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, permiso en el manifiesto; en Xiaomi botón
  "Inicio automático" → `com.miui.permcenter.autostart.AutoStartManagementActivity`) y en
  Ajustes → Notificaciones: "Funcionar en segundo plano" (estado + botón) y, solo en Xiaomi,
  "Ahorro de batería (Xiaomi)" (`miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY`) e "Inicio
  automático (Xiaomi)". `FeedCadence.METERED_MIN` 30→15 min (el RSS ocupa ~5 KB por canal).
  Notificación instantánea de verdad para YouTube = WebSub/PubSubHubbub de YouTube → servidor
  propio → push (ntfy/UnifiedPush o FCM): pendiente de decidir (necesita backend, fase 6).
  Coche (foto: "Sonando ahora"/"A continuación" funcionan): artistas sin " - Topic"
  (`videoprime/CarText.kt`, también en `Player.getUploaderName` → notificación y sesión),
  tiempos sin cero delante ("en 3:43"), icono de descargado en canciones sin conexión/descargas,
  Novedades por secciones Hoy/Ayer/Esta semana/Anteriores con barra de visto/medio visto
  (`DESCRIPTION_EXTRAS_KEY_COMPLETION_*`).
- 2026-10-20/21 (26): Avisos de directos de Twitch (al usuario le llegó antes el de la app de
  Twitch y el de VideoPrime nunca): antes iban dentro del trabajo de 15 min, después del feed de
  YouTube (si fallaba, no se miraban). Ahora `LiveNotifications.LiveCheckWorker`: trabajo de una
  vez que se vuelve a encolar cada 5 min (30 min si no hay canales de Twitch con campana), una
  petición GQL para todos; canal propio "Directos" (`videoprime_live`, IMPORTANCE_HIGH, emergente);
  recuerda cada directo visto 30 min (`videoprime_twitch_live_seen`, "url|millis") para no repetir
  si el canal falta en una consulta; no encola otro si ya hay uno esperando o si el sistema lo
  paró. Las emisiones pasadas de Twitch ya no avisan como vídeo nuevo (`NotificationWorker`).
  Extractor `1e30592a`: el vídeo con `status: RECORDING` (grabación del directo en curso) no se
  lista (salía como "vídeo de 00:59" junto al directo); `bdbe19d8`: la miniatura del directo lleva
  `?t=<cada 5 min>` (Twitch da un gris "sin vista previa" al empezar y Coil lo cacheaba).
  Probado en el emulador con un canal en directo: aviso en el canal Directos (importancia 4), 2.ª
  pasada "0 new", un único trabajo pendiente. OJO emulador: tras reiniciar la red queda "no
  validada" y WorkManager no lanza trabajos con red → forzar con `cmd jobscheduler run -f`.
  Comprobaciones nocturnas: extractor MOCK 2623 tests, 12 fallos (los 9 de siempre de YouTube + 3
  de Bandcamp, desregistrado); smoke real OK salvo audio de vídeos recientes (403 por IP del
  entorno). Revisión de código (agente) → arreglados: cola Compose con capturas viejas en los
  gestos (deslizar podía quitar otra canción y arrastrar usar un orden viejo): ahora
  `rememberUpdatedState` y se busca la canción por clave al soltar; claves únicas por identidad
  (`IdentityHashMap`, `PlayQueueItem.equals` es por URL); `OfflineMusic.schedule` en un hilo propio
  (bloqueaba la UI con `getWorkInfos().get()`); Novedades del coche: directos en sección "En
  directo"; `playFromQueue` elige la copia más cercana si la canción está repetida; diálogo de
  batería se marca al cerrarse; Colas solo marca "Sonando" si esa cola suena de verdad.
  Upstream NewPipe dev tiene 8 commits (descargas en `shared/`, Gradle 9.8.2, dependencias): no
  son de YouTube; el merge de prueba fue denegado por permisos → decide el usuario (issue #2).
- 2026-10-21 (27): Integrados los cambios de upstream (el usuario lo autorizó si eran mejoras).
  OJO: los clones del entorno eran superficiales (shallow) y `git merge-base` salía mal: hacer
  `git fetch --unshallow origin videoprime` antes de comparar con upstream (remoto `upstream` =
  TeamNewPipe). Extractor: 8 commits de upstream dev (YouTube: n-sig actualizado, versión de
  cliente desde el service worker, intérprete JS externo opcional, throttling con www) fusionados
  en `15d432e2` sin conflictos; MOCK 2623 tests, 11 fallos (2 nuevos —MixPlaylist$Music y
  StreamExtractorDefault$NotAvailable.geoRestricted— fallan igual en upstream puro); smoke real
  igual que antes. App: 10 commits de upstream dev (Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21,
  Compose 1.12.1, Coil 3.6.3, traducciones de Weblate, descargas en `shared/`); 72 conflictos en
  traducciones resueltos quedándonos con nuestra versión solo en los trozos en conflicto (nombres
  VideoPrime y servicios quitados), `libs.versions.toml` con jsoup 1.23.2 y nuestro `json`.
  `values-iw` es un enlace a `values-he`. Compilación local: Maven Central da 429 por el proxy →
  `~/.gradle/init.d/central-mirror.gradle` (solo en el entorno, no en el repo) pone primero el
  espejo de Google `https://maven-central.storage-download.googleapis.com/maven2/` (en
  pluginManagement añade también gradlePluginPortal() y google(); en buildSrc como repo de
  proyecto). Con él todo compila y pasan tests y lint.
- 2026-10-21 (28): La APK 15a1966 (con las versiones de upstream) se cerraba en el Xiaomi
  (Android 16): `ClassCastException: OutlinedTextFieldDefaults$$ExternalSyntheticLambda0 cannot be
  cast to androidx.compose.foundation.style.Style$Companion` al dibujar un campo de texto dentro
  de una rejilla Compose (Suscripciones/Descargas). No se reproduce en el emulador (Android 11).
  Se vuelve a las versiones que funcionaban (AGP 9.3.1, Kotlin 2.4.10, KSP 2.3.11, Compose 1.12.0,
  multiplatform 1.11.1, coil 3.5.0, acra, koin-plugin, navigation3, room, etc.); se quedan el
  extractor nuevo, las traducciones, Gradle 9.8.2 y jsoup 1.23.2. Probable causa: R8 de AGP 9.4.1
  o la mezcla Compose 1.12.1/multiplatform 1.12.2 con material3 1.11.0-alpha07. Para volver a
  subirlas: de una en una y probando en el móvil (o un emulador con Android 16).
- 2026-10-21 (29): Revisado el informe del equipo del usuario. Compose: lo que citaban (1.12.1 /
  multiplatform 1.12.2 / Kotlin 2.4.21) era la APK 15a1966, ya revertida; en
  `continuousRuntimeClasspath` resuelve androidx.compose 1.11.2 + org.jetbrains.compose 1.11.1 +
  material3 1.11.0-alpha07, sin mezclas (`compose = 1.12.0` del catálogo solo lo usan librerías de
  test). CI: nuevo `.github/workflows/videoprime-checks.yml` (push/PR a `videoprime`, con
  submódulo): job app = `testDebugUnitTest lintContinuous`; job extractor = tests MOCK del
  submódulo y `.github/videoprime/check_test_failures.py` solo falla con fallos que no estén en
  `.github/videoprime/extractor-known-failures.txt` (los 11 conocidos; `Clase#prefijo*` vale para
  tests parametrizados; avisa si uno conocido deja de fallar). `ci.yml` de upstream no se toca.
- 2026-10-21 (30): Fotos del usuario. Coche: al activar aleatorio la cola de la sesión no se
  volvía a publicar si la canción actual ya era la 1.ª (el `move` del timeline no cambia nada) →
  `MediaSessionPlayerUi` llama a `sessionConnector.invalidateMediaSessionQueue()` en
  `onShuffleModeEnabledChanged` y `onPlayQueueEdited`. Menú de un vídeo (`InfoItemDialog`
  `addDefaultEndEntries`): solo hasta "Añadir a la lista" (fuera compartir, navegador, Kodi, visto,
  canal). Menú de un canal (`ChannelsFragment.showMenu`): campana (tocar = activar/desactivar,
  `ChannelCard.notificationsOn`) y desuscribirse. Estilo elegido "A · Baldosas" de
  https://claude.ai/artifact/VaXfHoSRDZk8L57W67zHmt para los dos: `videoprime/ActionSheet.kt`
  (BottomSheetDialog + Compose: cabecera con miniatura/avatar, baldosas en 2 columnas, la 1.ª
  violeta, borrar/desuscribirse en rojo). `InfoItemDialog` ya no usa AlertDialog: pasa sus
  entradas a `ActionSheet` con icono y nombre corto ("Segundo plano", "Emergente"...). Mini reproductor en segundo plano: tocarlo abre el reproductor de música
  (`VideoDetailFragment` overlayListener si `PlayerType.AUDIO`). Emergente: por defecto 90 % del
  lado corto de la pantalla (migración `videoprime_popup_big_done` olvida el tamaño guardado),
  botón auriculares `audioOnlyButton` (player.xml, solo en la emergente) en lugar de subtítulos →
  segundo plano. Pasar de vídeo a segundo plano ya no recarga (`Player.handleIntentPost` usa
  `useVideoAndSubtitles(false)`: apaga la pista de vídeo; solo recarga si hace falta); de audio a
  vídeo sí recarga (la fuente puede ser solo audio). Copia exacta de la cola en Android Auto: no
  se puede (las apps de música no dibujan su pantalla en el coche).
- 2026-10-21 (31): Menús cambiados tras verlos en el móvil (en los bocetos el usuario lee el
  rótulo de debajo como el del teléfono de abajo: poner rótulos ENCIMA en próximos bocetos).
  Vídeo = "B · Cristal neón" (botones redondos de 4 en fila, resplandor cian), canal = "C · Lista
  con interruptor" (campana como Switch que no cierra la hoja; desuscribirse en rojo pide tocar
  otra vez). `ActionSheet.Layout.BUTTONS/ROWS`; se borraron las baldosas A. Regla: el violeta
  significa "activado", nunca "acción principal" (ningún botón resaltado en los menús).
  Mini reproductor opaco (`MAX_OVERLAY_ALPHA` 1, antes 0,9: se transparentaba la miniatura).
  Negro al cambiar de vídeo a emergente: ahora al salir de la app con un vídeo se usa el PiP de
  Android como Twitch (`videoprime/VideoPip.kt`, `VideoDetailFragment.enterPictureInPicture/
  onPictureInPictureChanged`, `MainActivity.onUserLeaveHint/onPictureInPictureModeChanged`):
  mismo reproductor y misma superficie, nada se recarga; sin controles propios (botón play/pausa
  del sistema por broadcast ACTION_PLAY_PAUSE); cerrar la ventana pausa; volver la abre grande;
  pantalla apagada = solo audio. Manifiesto: `supportsPictureInPicture` y `configChanges`
  orientation|screenSize|smallestScreenSize|screenLayout (el PiP es "land" y recreaba la
  actividad); al girar el móvil fuera del PiP `MainActivity.onConfigurationChanged` hace
  `recreate()` como antes. Si el PiP está desactivado para la app se usa la emergente antigua.
  Probado en el emulador: entra en PiP con el vídeo sonando y sin recrear la actividad. Los toques
  sobre la ventana PiP no llegan en el emulador (no se pudo probar expandir/cerrar desde ella).
  El botón "Emergente" sigue usando la emergente antigua (el cambio principal↔emergente aún puede
  dar un negro breve).
- 2026-10-21 (32): Una sola ventana flotante: la emergente antigua (`PopupPlayerUi`) queda en
  desuso. `NavigationHelper.playOnPopupPlayer` abre el vídeo en el reproductor principal y pide el
  PiP (`VideoPip.requestFor(url)`, ver entrada 33); `MainPlayerUi.onPlaying` →
  `VideoDetailFragment.enterPictureInPictureIfRequested()` (solo si el vídeo que suena es ese); si
  ya sonaba ese vídeo se entra al momento. `enqueueOnPlayer(POPUP)` va al reproductor abierto o al
  PiP. Salir de la app sin PiP posible = sigue como audio (nunca la emergente vieja). En el PiP: el
  vídeo llena la ventana aunque se redimensione (`pipLayoutListener`), botones del sistema
  "Quitar el vídeo" (`VideoPip.ACTION_AUDIO_ONLY` → segundo plano + `moveTaskToBack`) y
  play/pausa; receptor dado de baja en `onDestroy`. Calidad del reproductor principal por defecto
  "la mejor" (migración `videoprime_best_quality_done`). Interruptor de la campana apagado ahora
  visible (pista gris con borde). `videoprime/SmoothImage.kt`: las carátulas del Inicio y avatares
  mantienen la imagen anterior mientras carga la nueva dirección (las miniaturas de directos de
  Twitch cambian de URL cada 5 min y parpadeaban en negro al actualizar).
- 2026-10-22 (33): Turno de noche de notificaciones (informe para el usuario:
  https://claude.ai/artifact/UcLvBEvTy3GMo7WfSNceeV; investigaciones completas en el scratchpad,
  no se suben). Hecho sin servidor: `videoprime/CheckAlarm.kt` = pulso con alarma exacta
  `setExactAndAllowWhileIdle` cada 10 min (`USE_EXACT_ALARM`; `SCHEDULE_EXACT_ALARM` maxSdk 32):
  el receptor encola `NotificationWorker.runExpedited` (único, KEEP, expedited en API 31+, con
  `skip_twitch`; sus errores devuelven success para no bloquear pulsos) y mira Twitch con
  `goAsync` y tope de 7 s; `RestartReceiver` (no exportado) tras reinicio/actualización.
  `FeedCadence.BELL` = 10 min para canales con campana (15 con datos móviles). Los avisos salen
  según se guarda cada tanda (`FeedLoadManager.startLoading(onNewStreams)`), no al final.
  Canal nuevo `videoprime_new_videos` IMPORTANCE_HIGH (borra `newpipeNewStreams`), grupo común,
  cada vídeo suena si son ≤3. `videoprime/BackgroundStatus.kt`: marcas de última comprobación y
  "Estado de los avisos" en Ajustes → Notificaciones; `BackgroundPermission.askOnce` vuelve a
  preguntar (1/día) si hay red y >2 h sin comprobar. PiP desde "Emergente": `RouterActivity` va
  por la página del vídeo (un servicio no puede abrir pantallas), la petición se acepta
  (`VideoPip.accept`, dura 5 min), fuerza la reproducción automática y reintenta entrar 8 veces.
  Probado en el emulador: "Emergente" desde un enlace youtu.be → ventana PiP con el vídeo
  sonando. Pendiente decisión del usuario: servidor de avisos instantáneos (WebSub + EventSub +
  Cloudflare Workers + FCM por temas; necesita sus cuentas de Cloudflare, Firebase y Twitch dev).
  Visto en el emulador: "SQLiteConnectionPool ... unable to grant a connection ... 360 s" durante
  cargas de feed (posible contención en la BD; vigilar en el móvil).
- 2026-10-22 (34): Coche: al tocar una canción de una lista, poner aleatorio y volver, el coche
  vuelve a la pantalla de la lista (la navegación la controla el coche, la app no puede llevarlo a
  otra). Ahora esa lista, si es la cola que suena, se dibuja en el orden de la cola (aleatorio
  incluido): "Sonando ahora", "A continuación" con "en m:ss" y "Ya sonaron (n)", ids `queue/<i>?url=`
  (`CarMedia.playlistAsQueue/isPlayingPlaylist`; local = mismas canciones, remota = la cola las
  contiene todas; hasta 1000). `MediaBrowserImpl` recuerda las 5 últimas listas abiertas y en
  `onQueueChanged` las redibuja si son la cola o lo eran (vuelven a su orden). Sin probar en coche.
- 2026-10-22 (35): Avisos instantáneos con servidor (el usuario eligió Firebase). `push-server/`:
  Cloudflare Worker + un Durable Object con SQLite (plan gratuito, sin IDs de KV/D1 que crear):
  YouTube por WebSub (callback `/websub/yt?ch=UC…&k=<hmac>`, concesión máx. 11 días, se renueva el
  último día, solo vídeos <48 h y una vez), Twitch por EventSub `stream.online` (firma HMAC, 10 min
  de margen, deduplicado; título y juego de Helix), envío por FCM HTTP v1 (JWT RS256 con WebCrypto)
  como mensaje solo de datos, prioridad alta, tema `yt_<canal>`/`tw_<login>`. Mantenimiento por
  cron cada 10 min y alarma tras cada registro, en tandas de 40 peticiones (límite gratuito 50);
  canales sin pedir en 30 días se olvidan; si cambian las claves se rehace todo; `/register` con
  tope (5000 YT, 2000 Twitch) y 20 registros/h por IP (solo en memoria). Secretos de firma
  derivados de las claves (no hay más secrets). Rutas `/status`, `/register`. Pruebas:
  `npm test` (lib.js) y prueba local con `wrangler dev` (scratchpad, no se sube).
  Workflow `videoprime-push.yml` (despliega con wrangler 4.149.0 y pasa las claves con `secret
  bulk`; en push sin secrets se omite). `videoprime-apk.yml`: `FIREBASE_CONFIG` (google-services
  entero) → BuildConfig `FIREBASE_*` (sin plugin google-services; `app/build.gradle.kts`, un JSON
  roto compila sin avisos) y la URL del servidor sale de la API de Cloudflare (o variable
  `VIDEOPRIME_PUSH_URL`). App: `videoprime/InstantPush.kt` (FirebaseApp a mano, auto-init apagado
  y `FirebaseInitProvider` quitado; interruptor "Avisos instantáneos", desactivado por defecto;
  temas = canales con campana, se sincronizan al abrir, en cada pasada del trabajo y al tocar la
  campana; registro diario; al apagar borra el token). Push de YouTube → notificación con
  `NotificationHelper` y marca `videoprime_pushed_videos` (la comprobación normal no repite);
  Twitch → `LiveNotifications.onPush` (mira Twitch y si aún no sale, avisa con lo del push;
  deduplicado por login). firebase-messaging 25.1.3 (26 pide minSdk 24). Estado de los avisos
  muestra canales y último push. Privacidad: `docs/videoprime/privacidad-avisos.md`. Sin probar
  con Firebase real (faltan las cuentas del usuario).
- 2026-10-22 (36): Servidor de avisos: los Shorts no avisan (`isShort`: HEAD a /shorts/<id> con
  cookie SOCS=CAI → 200 = Short, 303 a /watch = vídeo; desconocido = vídeo) y `/status` da
  `lastPushVideo` (hora de publicación) y `lastShortSkipped`. Medido: Short publicado 16:00:00 UTC,
  aviso enviado 16:01:02 (1 min, casi todo es el hub de YouTube); Twitch al instante. Workflow
  "VideoPrime: estado de avisos" (anotación con /status). App: `InstantPush` vigila la tabla de
  suscripciones (canales con campana, 5 s de espera) y sincroniza temas y servidor al seguir/dejar
  un canal o tocar la campana, sin esperar a la siguiente comprobación.
- 2026-10-22 (37): Actualizar desde la notificación: `videoprime/UpdateActivity.kt` (diálogo; pide
  "instalar apps desconocidas" la 1.ª vez, descarga la APK de la release a `cacheDir/updates` con
  progreso en un `.part` propio y la abre en el instalador de Android vía `UpdateActivity.Provider`,
  autoridad `<appId>.updates`); si falla, abre la URL en el navegador como antes. Permiso
  `REQUEST_INSTALL_PACKAGES`. En Android 6 el instalador no acepta content:// (no se usa).
- 2026-10-22 (38): Inicio: la actualización automática al abrir va callada (sin el círculo de
  `PullToRefreshBox`); el círculo solo sale al tirar hacia abajo (`HomeFragment.pulled`). Servidor:
  39 canales (32 YouTube + 7 Twitch) suscritos. Bocetos de "vídeos nuevos por canal" (A contador en
  el avatar, B anillo + «n nuevos», C fila «Novedades» arriba, y la vista del canal con NUEVO):
  https://claude.ai/artifact/CDEfUz4TTyDVWP1zg4jzjL (pendiente de que el usuario elija).
- 2026-10-22 (39): (a) Un push de YouTube mete ya el vídeo en el feed (`FeedDatabaseManager.addStream`,
  sin tocar `feed_last_updated`) y luego sus detalles con `getStreamInfo` (12 s máx.): sale en Inicio
  al abrir. (b) Aviso de versión nueva al momento: el último paso de `videoprime-apk.yml` hace POST
  a `/release` del servidor (clave = sha256("videoprime-release:"+private_key de FCM_SERVICE_ACCOUNT))
  y el servidor lo manda al tema `vp_updates` (la app se suscribe con los avisos instantáneos;
  `UpdateChecker.onPush`); respaldo: comprobación cada 3 h (antes 12). (c) Diseño A elegido:
  `videoprime/ChannelVisits.kt` (última visita por canal en prefs `videoprime_channel_visits`, al
  principio cuenta lo del último día), `FeedDAO.getRecentUnwatched` (vídeos no directos de 30 días,
  sin ver, no futuros) → contador cian en el avatar de Suscripciones (9+), canales con novedades
  primero; al abrir el canal (`NavigationHelper.openChannelFragment`) se apunta la visita y sus
  vídeos posteriores llevan borde cian redondeado + NUEVO en la miniatura (foreground dibujado en
  `StreamMiniInfoItemHolder`; se quita en `ChannelFragment.onDestroy`). Color `VideoPrimeColors.New`.
- 2026-10-22 (40): Informe de auditoría del equipo (PDF, base 15a1966). Aplicado: VP-02 prueba de
  arranque en emulador en `videoprime-apk.yml` antes de publicar (KVM + reactivecircus/android-
  emulator-runner@v2.33.0, API 35, `.github/videoprime/smoke_launch.sh`: instala, abre MainActivity,
  25 s, falla si hay "Process: <paquete>" en el logcat o el proceso murió; un fallo del emulador
  solo avisa y publica); VP-03/04 migraciones de BD con `MainActivity.migrateDatabaseOnce` (marca
  hecha solo si sale bien, registra el error y reintenta; claves nuevas
  `videoprime_notifications_on_db_done`, `videoprime_shorts_duplicates_done`); VP-07 `distinctBy`
  url en las listas del Inicio; VP-06 estado `failed` en Inicio (mensaje si no hay vídeos, aviso si
  falla al tirar hacia abajo). Descartado: VP-01 (era la APK 15a1966 ya revertida, entrada 28),
  VP-05 (ya existe `videoprime-checks.yml`), VP-08 (el progreso ya se limita a 0..1 en
  HomeFragment), VP-10 (refactor grande del código de upstream sin beneficio visible). Duda para el
  usuario: VP-09 chips (Inicio/Suscripciones blancos, Búsqueda/Directos violeta) → unificar estilo.
- 2026-10-22 (41): Fotos del usuario. Botones de filtro: violeta en todas partes (VP-09) con un
  componente común `videoprime/VideoPrimeChip.kt` (Inicio, Suscripciones, Directos, Búsqueda).
  Imágenes: calidad alta por defecto (`image_quality_default` = high + migración
  `videoprime_high_images_done`); la miniatura de un push es `hq720.jpg`. Listas (canal, búsqueda,
  listas): miniatura 160x90 (antes 124x70; land 192x108), `centerCrop` y esquinas de 12 dp
  (`ChannelVisits.roundCorners` en `StreamMiniInfoItemHolder`); NUEVO como píldora redonda.
  Aviso de TheGrefg que no salió: el servidor lo mandó (19:46:17, 1 min tras publicarse) y el móvil
  lo recibió; la app lo descartaba porque el vídeo ya estaba en la BD (`streamDAO.exists`). Ahora el
  push solo se salta si ya se avisó (`InstantPush.markTold/wasTold`, también desde
  `NotificationWorker.tell`) y "Estado de los avisos" muestra qué pasó con el último push
  (mostrado / ya avisado / sin campana / no se pudo mostrar).
- 2026-10-22 (42): Actualizar desde la notificación se cerraba en el móvil ("Missing
  android.support.FILE_PROVIDER_PATHS meta-data": `FileProvider(int)` no basta para
  `getUriForFile`); ahora el proveedor lleva su `<meta-data>` en el manifiesto. `UpdateActivity`
  rehecha en Compose (tarjeta oscura con el icono de la app, barra violeta, MB y %, pasos Permitir /
  Descargando / Instalar / Error con Reintentar o navegador; tema `VideoPrimeUpdateTheme` con fondo
  semitransparente; si la descarga acaba con la app en segundo plano, espera con "Instalar").
  Avisos de YouTube confirmados por el usuario (Tito Kolde al instante). TV: propuesto (a) volver a
  meter la TV solo con canales sin DRM (RTVE, autonómicas, TRECE, RMTV…; de TDTChannels solo `m3u8`)
  y (b) para Atresmedia/Mediaset, TDT por antena con sintonizador de red (HDHomeRun DVB-T2 o
  Raspberry Pi + TVHeadend) integrado en la app; nunca saltar DRM. Pendiente de que elija.
- 2026-10-22 (43): Versión estable guardada antes de la TV: release `estable-2026-10-22`
  (https://github.com/GHD235/VideoPrime/releases/download/estable-2026-10-22/VideoPrime_0.29.1_784ad81.apk).
  Esta sesión no puede crear releases por la API (403): workflow `videoprime-stable.yml` copia la APK
  de la nightly a una release propia al añadir "<etiqueta> <commit>" en `.github/videoprime/estable.txt`
  (hacerlo ANTES de que la nightly cambie). Tampoco puede subir etiquetas git.
  TV de vuelta (opción A elegida): chip "TV" en Inicio (`TvController`/`TvScreen`, recuperados de la
  entrada 6; extractor: servicio TV id 6, Local id 7, `3824e8c5`). Canales sin DRM (`m3u8`) en nuestro
  reproductor; Atresmedia/Mediaset ("Web oficial") en `videoprime/TvWebActivity.kt`: WebView con su
  reproductor web oficial y Widevine del sistema (solo se concede `RESOURCE_PROTECTED_MEDIA_ID` a
  https de atresplayer/atresmedia/mitele/mediaset/mediasetinfinity), pantalla completa con
  `onShowCustomView`, cookies persistentes (si piden cuenta gratuita, se queda iniciada), sin
  "; wv" en el user agent, enlaces intent:/http ignorados. Usa el DRM normal, no lo salta.
  Sin probar en móvil. Fotos del usuario: contador del avatar en Suscripciones con tamaño fijo
  (26 dp, texto sin escalar con la fuente del sistema: salía alargado); miniaturas de listas
  192x108 (horizontal 224x126); portadas de YouTube siempre en hq720 (`videoprime/BestThumbnail.kt`,
  Inicio y listas, con la dirección guardada de respaldo: la BD guardaba a veces mqdefault/hqdefault
  según de dónde llegaba el vídeo y al volver al Inicio se veían borrosas); botones de la página del
  vídeo redondos como el menú del vídeo (`videoprime_detail_*.xml`).
  Probado por el usuario: dentro del WebView Atresplayer dice "bloqueador de publicidad" y Mitele
  "Se ha producido un error · DAI" (la inserción de anuncios de Google, IMA DAI, no funciona en un
  WebView). Ahora "Web oficial" abre una pestaña de Chrome sobre la app (Custom Tab,
  `androidx.browser`; anuncios, cuenta y DRM como en su web); `TvWebActivity` solo si no hay Chrome.
  Causa real: el DNS privado del móvil era dns.adguard.com (bloquea anuncios en todo el sistema);
  con él desactivado funciona. Luego (petición del usuario, "que salga directamente el directo"):
  vuelve el WebView (`TvWebActivity`) y, en cuanto un <video> visible suena, un JS (cada 1 s,
  `PLAYER_ONLY_JS`) fija el contenedor del reproductor a pantalla completa (horizontal e inmersivo,
  atrás = salir); antes de sonar la página se ve normal (aviso de cookies/cuenta). Botón "Abrir en
  Chrome" abajo mientras no suena (Custom Tab de respaldo). Inicio: la lista de directos de Twitch
  se guarda en `cacheDir/videoprime_home_live.bin` y se pinta al abrir la app.
- 2026-10-23 (44): TV confirmada por el usuario (con DNS sin filtro). Autonómicas: se quedan (quitarlas
  no mejora el rendimiento: la lista es un JSON que solo se baja al tocar "TV" y la rejilla es
  perezosa). Directos en Inicio al abrir: `videoprime/HomeLiveCache.kt` (máx. 15 min); lo escribe
  también `LiveNotifications.check` (cada 5-10 min en segundo plano), que ahora pregunta a Twitch
  por TODOS los canales de Twitch en una petición y solo avisa de los que tienen campana.
  Avisos: estado del servidor OK (32/32 YouTube, 7/7 Twitch). Un push de eSavants era de un vídeo de
  hacía 41 h (YouTube vuelve a avisar al editar título/miniatura): la app ignora ahora un push de
  un vídeo de más de 12 h que ya está en el feed (`InstantPush.OLD_VIDEO_HOURS`); el servidor sigue
  con 48 h (los estrenos llevan la fecha de cuando se programaron).
  Después (foto del usuario: al abrir, sin directos; al rato, sí): caché en JSON
  (`videoprime_home_live.json`, solo lo que pinta el Inicio, sin serialización Java) y válida 6 h
  (mejor algo viejo un segundo que la fila vacía; el Inicio pregunta a Twitch al momento si tiene
  más de 1 min). "Estado de los avisos" muestra cuántos estaban en directo en la última consulta.
  TV: solo grupos nacionales + "C. Valenciana" (`TvController.SHOWN_GROUPS`; el usuario lo pidió).
  Lo que de verdad veía el usuario: la fila de directos llega un momento después que los vídeos y la
  LazyColumn se quedaba anclada en "De tus suscripciones" (el directo quedaba encima, fuera de
  pantalla). `HomeScreen.Feed`: al aparecer directos vuelve arriba (`scrollToItem(0)`) salvo que el
  usuario haya arrastrado la lista (`DragInteraction.Start`).
- 2026-10-23 (45): Carátulas de YouTube Music borrosas (vienen de googleusercontent a 60-120 px).
  `BestThumbnail.CoilInterceptor` (registrado en `App.newImageLoader`) reescribe TODAS las imágenes
  que carga Coil (listas, Inicio, reproductor, notificación, Compose): i.ytimg `vi/<id>/<fija>` →
  `hq720.jpg` (no los `*_live`), googleusercontent/ggpht `=wN-hN…` → ancho 1200 proporcional y
  `=sN…` → s800; claves de caché propias y, si la grande falla, la original (y no se vuelve a pedir:
  conjunto de 500 fallidas). Quitado el respaldo manual de `CoilHelper.loadThumbnail`/`SmoothImage`.
  El proxy del entorno no deja llegar a ggpht/googleusercontent: el reescalado no se probó aquí.
- 2026-10-24 (46): Avisos de vídeo nuevo con el título entero: título = canal, texto = vídeo y
  `BigTextStyle` (el título de una notificación se corta a 2 líneas). "Añadir a la lista" moderno:
  `PlaylistAppendDialog` devuelve un `BottomSheetDialog` y su vista es `videoprime/PlaylistPicker.kt`
  (Compose, como los menús: "Lista de reproducción nueva" + listas con portada redondeada, nombre y
  nº de vídeos; las que ya lo tienen, atenuadas con "Ya está en esta lista" en cian).
- 2026-10-24 (47): TV QUITADA del todo (decisión del usuario, segunda vez: entrada 6). Usa DNS privado
  dns.adguard.com y Atresplayer/Mitele no reproducen con bloqueo de anuncios; saltarse su detección
  no se hace, y el usuario no quiere "medias tintas" (descartada también la opción de dejar solo los
  canales sin DRM). App: fuera `TvController`, `TvScreen`, `TvWebActivity`, chip TV, `HomeFilter.TV`,
  `VideoPrimeColors.Tv`, textos `tv_*`, `androidx.browser`, manifiesto. Extractor: revertidos
  c51dad44/e0de204c/3824e8c5 (`ServiceList` = YouTube, Twitch, Local id 7). Recuperable del historial.
- 2026-10-24 (48): Imágenes más rápidas: `BestThumbnail` pide `vi_webp/<id>/hq720.webp` (misma
  imagen, ~40 % del JPEG: 28 KB frente a 65 KB), portadas de YT Music a ancho 720 y avatares s288
  (antes 1200 / 800); Coil con su propio cliente OkHttp de 16 peticiones por host (OkHttp deja 5).
  Radio automática (al poner una canción suelta, seguir con canciones parecidas): factible con los
  mixes de YouTube Music (`RDAMVM<id>`, ya soportados por el extractor); pendiente de que el usuario
  lo confirme. Confirmado y hecho: `videoprime/AutoRadio.kt` (desde `Player.updateMetadataWith`):
  en segundo plano/coche, una cola de UNA canción (categoría Music o canal " - Topic") se vuelve
  radio con el mix de YT Music (`watch?v=ID&list=RDAMVM<ID>`, si falla `RD<ID>`; 25 canciones,
  probado con red real) y a 3 del final añade el mix de la que suena (sin repetir URL ni pedir
  dos veces la misma semilla). Ajuste "Radio automática" (Vídeo y audio, activado). Imágenes: la
  calidad se comprobó (WebP = mismos 1280x720, PSNR 39-43 dB frente al JPEG) y portadas/avatares de
  googleusercontent se piden al tamaño en que se dibujan (`chain.size`, pasos de 120, 240-1200).
- 2026-10-24 (49): Suscripciones tardaba en salir: `ChannelsFragment` usaba `debounce(400 ms)` y, al
  abrir la app, el feed se escribe sin parar mientras se actualiza (nunca había 400 ms de calma):
  ahora `throttleLatest(400 ms, emitLast)` (pinta al momento y luego como mucho cada 400 ms).
  Pedido HDfull (web de películas/series sin licencia): rechazado, es piratería (regla del proyecto).
- 2026-10-24 (50): Vídeo del usuario: al salir de la app con un vídeo, Android encogía la página
  entera hasta la ventana flotante (un destello) y al volver al revés. Ahora en Android 12+ la
  ventana se abre sola (`setAutoEnterEnabled`) con `sourceRectHint` = el vídeo recortado a su forma
  (`VideoPip.arm/disarm/videoRect`); `VideoDetailFragment.armPictureInPicture` lo actualiza al
  reproducir/pausar, al cambiar la hoja y al moverse el vídeo (listener de layout, quitado en
  `onDestroyView`); `MainActivity.onUserLeaveHint` solo deja actuar al sistema si sigue queriendo
  la ventana (`isPictureInPictureWanted`), si no la desarma y sigue como antes; `onDestroy` desarma.
  Sin probar en móvil (el emulador de aquí no aguanta).
  La APK 91224b6 no se publicó: la prueba de arranque vio un cierre (sin traza: los logs del run no
  se pueden bajar). `smoke_launch.sh` ahora pone la traza en una anotación `::error`. Repetida con el
  mismo código de la app (e30a336): arrancó bien y se publicó; el cierre no se repitió (intermitente,
  causa desconocida). Si vuelve a salir, leer la anotación del run.
- 2026-10-25 (51): Portada del directo lenta: la vista previa de Twitch cambia de dirección cada 5 min
  (`?t=`), casi nunca estaba en caché; `HomeLiveCache.write` ahora la precarga (y el avatar) en la
  caché de disco de Coil. Vídeo que tarda/se queda pillado: `LoadController` con búfer 30-90 s (antes
  50/50), empieza con 1,5 s (antes 2,5) y tras un corte con 4 s (antes 5). Destello del PiP: los
  controles del reproductor se ocultan al instante en `onUserLeaveHint` antes de que Android abra la
  ventana (`VideoDetailFragment.hidePlayerControlsNow`).
  Propuesta para el usuario (pendiente de que elija): https://claude.ai/artifact/7Ce9SJjJ4h7LVmjRXo31Ag
  — logo A (actual pulido) / B (monograma VP) / C (prisma rojo-violeta-cian) / D (play + barras);
  10 pantallas con estilo antiguo por prioridad (canal, parte baja del vídeo, descargar, crear
  lista = alta; ajustes, historial, velocidad, lista local, sugerencias = media); funciones F1-F11
  (directos programados de YouTube, SponsorBlock, letras LRCLIB, "Seguir viendo", chat de Twitch,
  widget, ecualizador, sin conexión automático por canal, copia semanal, Android TV, Spotify).
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
