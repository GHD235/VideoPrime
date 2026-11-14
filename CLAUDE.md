# PulsePlay (antes VideoPrime) — notas para Claude

Fork de NewPipe (GPLv3) mantenido por GHD235. Nombre visible **PulsePlay** desde el 2026-10-25
(antes VideoPrime): applicationId, paquetes `videoprime/`, prefijos `videoprime_` de recursos y
prefs, rama `videoprime` y nombres de workflows en disco se quedan (internos; cambiarlos costaría
datos o compatibilidad). APK: `PulsePlay_<versión>_<sha7>.apk`. Este archivo es la memoria del proyecto
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
- Renombrar el repo `GHD235/VideoPrime` → `GHD235/PulsePlay` (Settings → General → Repository name).
  Después, en las sesiones nuevas, el repo se llama PulsePlay (las URLs viejas redirigen).
  (El aviso "This repository moved" al hacer push es solo por mayúsculas en el remoto, no un renombrado.)
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
- **replica-skill** (https://github.com/Jakeschincariol/replica-skill, MIT, commit 77c9436): INSTALADA el
  2026-11-02 a petición del usuario ("para este proyecto y posteriores"), en `.claude/skills/replica-*` (11
  skills: recon, architect, design, backend, build, test, diff, brand, deploy, launch, entrepreneur;
  `REPLICA-LICENSE`). Solo texto + scripts Python locales, sin hooks/MCP/red. Útiles aquí: `replica-test`
  (plan de pruebas/informes de fallos), `replica-entrepreneur` (quejas de usuarios de apps parecidas) y
  `replica-recon`/`replica-diff` (comparar funciones con apps abiertas). Las de build/deploy/launch son para webs
  Next.js. Regla del proyecto: de otras apps solo funciones y flujos, nunca código, arte ni textos. (Antes
  probada: su análisis de reseñas quedó en una fuente porque el proxy bloquea Reddit/HN/tiendas; resultado en
  `docs/videoprime/ideas-usuarios-newpipe.md`.)
- Probadas y descartadas: **claude-octopus** (reparte trabajo a Codex/Gemini/
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
- 2026-10-25 (52): Logo nuevo (elegido por el usuario): el de siempre (play blanco sobre degradado
  #4F46E5→#A21CAF) con el destello cambiado por una onda de 3 barras redondeadas en cian #22D3EE. En
  todo: `ic_launcher_foreground` (+ monocromo), `splash_foreground`, `shared/.../ic_foreground.xml`
  (Acerca de), PNG de mipmap (círculo), banner de Android TV (`newpipe_tv_banner`: logo + "VideoPrime")
  y `fastlane/.../icon.png`; iconos pequeños de notificación (`ic_newpipe_triangle_white`,
  `ic_newpipe_update`) ahora son vectores blancos con el mismo dibujo (antes el triángulo de NewPipe).
  Generador: `scratchpad/logo/gen.py` + `render.js` (Playwright; no se sube).
  Canal: la página se queda como estaba, pero a la derecha de la cabecera violeta van una campana
  redonda (solo si estás suscrito; activar/desactivar, deshabilitada hasta que la BD confirma) y una
  píldora "Suscribirse" blanca / "✓ Suscrito" translúcida (`videoprime_channel_bell`,
  `videoprime_channel_check`, `ic_notifications_off`); fuera la campana del menú y el aviso "Recibir
  notificaciones" tras suscribirse (la campana ya viene activada).
  Contador de vídeos nuevos ≠ NUEVO dentro del canal (foto: 3 frente a 4): el contador no cuenta lo ya
  empezado a ver y NUEVO usaba la fecha aproximada de la página del canal ("hace 1 día"). Ahora NUEVO
  sale de la misma consulta (`FeedDAO.getUnwatchedUrlsSince`, misma ventana `ChannelVisits.RECENT_DAYS`
  = 30), leída en segundo plano al abrir el canal (`isNew` espera 300 ms una vez si aún no acabó).
  Bocetos de las pantallas antiguas, ahora frente a propuesta (2-10, pendiente de que elija):
  https://claude.ai/artifact/MoKqav5hBEesGu4eW5K1R1
- 2026-10-25 (53): Nombre nuevo **PulsePlay** (VideoPrime podía dar problemas). Cambiado todo lo que se
  ve: textos de todos los idiomas (fuera de comentarios XML), `VIDEOPRIME_APP_NAME` = "PulsePlay" (la
  variante Continuous ya se llama solo "PulsePlay"), estilos `*.PulsePlay.*`/`PulsePlayUpdateTheme`,
  banner de la TV, carpeta de descargas por defecto "PulsePlay", copia "PulsePlayData-*.zip", APK
  `PulsePlay_*.apk`, release "PulsePlay nightly", nombres de workflows "PulsePlay: …". Se mantienen
  (decisión del usuario): applicationId `com.ghd235.videoprime[.continuous]` (si cambia, no se
  actualiza encima y Firebase deja de valer), proyecto Firebase y worker `videoprime-push`.
  `UpdateChecker` acepta APK `PulsePlay_`/`VideoPrime_`; el servidor de avisos acepta releases de
  GHD235/PulsePlay y GHD235/VideoPrime (lo mismo `UpdateChecker.DOWNLOAD_PREFIXES`). Renombrar el
  repo desde la sesión: 403 ("Repository settings writes are not permitted through this proxy") →
  lo hace el usuario (Settings → Rename a `PulsePlay`); GitHub redirige el nombre viejo.
  PiP (vídeo del usuario: Xiaomi encoge la página entera, no respeta `sourceRectHint`): al salir con
  la ventana armada, `hidePlayerControlsNow` deja INVISIBLE el contenido de la página
  (`detailContentRootLayout`, `viewPager`, `tabLayout`) y pone el fondo negro; se restaura en
  `onResume` (sin PiP) y al salir del PiP (`showPageAfterPictureInPicture`).
  Pantallas elegidas (3, 4, 6, 7, 8, 9, 10 del boceto): Descargar = hoja desde abajo
  (`download_dialog.xml`: chips `VideoPrimeRadioChip`, campos `VideoPrimeSpinner`, botón grande
  `videoprime_download_button`; hilos ocultos, usan el valor guardado); Crear lista =
  `videoprime/NameSheet.kt` (hoja Compose; `SheetColors`, `SheetFrame`, `SheetButton` reutilizables);
  Historial = `videoprime/HistoryFragment.kt` (Compose: buscador, Reproducir todo / Segundo plano,
  secciones Hoy/Ayer/Esta semana/Este mes/Anteriores, barra de progreso y "Quedan m:ss"; pulsación
  larga = menú con Borrar; `NavigationHelper.openStatisticFragment` lo abre); Velocidad =
  `videoprime/SpeedSheet.kt` (chips 0,5-2×, −/+ de 0,05, saltar silencios; "Ajuste fino y tono" abre
  el diálogo antiguo); lista local = filas 128x72 redondeadas y título en negrita; sugerencias de
  búsqueda con icono redondo y títulos "Buscado antes"/"Sugerencias"; error = icono, mensaje claro,
  botones redondeados y "Ver detalles técnicos" plegado (`activity_error.xml`, `error_panel.xml`).
  Rendimiento: `videoprime/StreamPrefetch.kt` (Inicio lee por adelantado los 3 primeros vídeos sin ver
  de YouTube con Wi-Fi, una vez por hora: abrirlos sale de la caché) y `app/src/main/baseline-prof.txt`
  (perfil con comodines para pantallas, reproductor y listas; profileinstaller ya venía con Compose).
- 2026-10-25 (54): "Emergente" desde los menús (historial, listas, Inicio…) mientras sonaba algo en segundo
  plano solo seguía como audio: `StreamDialogDefaultEntry.START_HERE_ON_POPUP` pasaba el contexto de
  la aplicación y `playOnPopupPlayer` sin Activity cae a segundo plano a propósito. Ahora pasa la
  Activity (o la app si se está cerrando).
- 2026-10-25 (55): Icono pequeño de las notificaciones (barra de estado) con círculo: círculo blanco con
  el play y la onda recortados (`fillType="evenOdd"`, un solo path; generador `scratchpad/logo/small.py`)
  en `ic_newpipe_triangle_white` e `ic_newpipe_update` (avisos, reproductor, descargas, versión nueva).
- 2026-10-25 (56): Auditoría interna (2 PDF iguales; el nuevo añade anexo de rendimiento R1-R8). Valorada
  punto por punto con el código. Aplicado: PP-12 `MainActivity.DEBUG = BuildConfig.DEBUG` (la variante
  continuous es release: antes ~270 bloques de log activos) y fuera los diálogos de NewPipe "Keep Android
  Open"/API 23 que eso habría mostrado (la pantalla de Ajustes de depuración ya no sale); R1 extractor
  `df535611`: métodos públicos de `YoutubeJavaScriptPlayerManager` `synchronized` (varias extracciones a
  la vez); PP-06 `QueueStore.load` guarda aparte un JSON dañado (`.damaged-<ms>`); PP-07
  `QueueStore.newId`; PP-05 `UpdateChecker` marca avisado solo si la notificación salió; PP-10
  `OfflineWanted.attempts` (3 intentos antes de olvidar una canción escuchada); R3/R4/R5/R8
  `StreamPrefetch`: sin ahorro de batería ni con algo sonando, reintento a los 5 min tras fallo,
  mapa acotado (200), `cancel()` al salir del Inicio; PP-01/PP-11/PP-14 workflow APK: prueba de arranque
  en Android 16 y, si el emulador no llega a probar, otra en Android 15 (si tampoco, se publica con
  aviso, como antes) y falla si falta el submódulo. Descartado con motivo: PP-02 (ya existe
  `videoprime-checks.yml`), PP-08/09/13 (ya hechos), PP-03 (URL distintas por servicio), PP-04 (checksum
  del mismo origen que la APK no añade nada; Android exige la misma firma), R2, R6 (caché HTTP con
  tokens que caducan), R7.
  Teclado que se abría solo en Historial al cerrar el menú de un vídeo: Android da el foco al primer
  campo al recuperar la ventana. `videoprime/NoAutoKeyboard.kt` quita el foco cada vez que la ventana
  vuelve a tenerlo; usado en Historial, Suscripciones y Descargas (los demás campos son diálogos de
  escribir o la búsqueda, donde el teclado es lo esperado).
  Comprobaciones (run #45) fallaban: `QueueStoreTest.corruptFileLoadsEmpty` (Log.e no existe en tests
  unitarios) → el log va en `runCatching` y el test comprueba también la copia `.damaged-`.
- 2026-10-25 (57): Informe de la ventana flotante (ondas/vibración al terminar la animación en el Xiaomi;
  hipótesis H1-H5). Aplicado: H1 `setSeamlessResizeEnabled(false)` (Android lo recomienda para vídeo: hace
  un fundido en vez de reescalar la superficie en vivo); H2 el ajuste del vídeo a la ventana se hace una
  vez cuando el tamaño se asienta (`VideoDetailFragment.schedulePipResize`, 150 ms) y no en cada paso del
  redimensionado; H3 la forma de la ventana tiene en cuenta `pixelWidthHeightRatio`. H4 (TextureView) y H5
  (decodificador) quedan sin tocar hasta ver si sigue pasando.
  Vídeo del usuario con esa versión: el rayado seguía y además el vídeo se congelaba al redimensionar
  (`seamless=false` = foto fija + fundido). Vuelve `setSeamlessResizeEnabled(true)` y la causa real:
  `ExpandableSurfaceView` medía con una altura fija (`baseHeight`) que solo se actualizaba DESPUÉS de cada
  cambio de tamaño (el vídeo iba un paso por detrás de la ventana). Ahora en PiP
  (`setFillWindow(true)` desde `onPictureInPictureChanged`) mide con los límites actuales de la ventana
  (`WindowManager.getCurrentWindowMetrics`, Android 11+) en la misma pasada de layout. OJO (revisión): la
  vista la infla el `PlayerService` → su `getContext()` es el servicio y daría la pantalla entera; se
  busca la Activity subiendo por los padres (`findActivity`), y el modo se decide en cada medida con
  `activity.isInPictureInPictureMode()` (ni bandera empujada ni `VideoPip.active`, que puede quedarse en
  true si se cierra la ventana sin aviso; `MainActivity.onResume` lo reinicia).
  De la guía oficial de PiP (lo que hacen las apps de vídeo): `MainActivity.onPictureInPictureUiStateChanged`
  con `isTransitioningToPip()` (Android 15+) oculta controles y página al empezar la animación; ya se
  cumplían `setAutoEnterEnabled`, `sourceRectHint` con la forma del vídeo y `configChanges`.
- 2026-10-25 (58): Suscripciones ordenadas: primero los canales con vídeos nuevos, luego de más a menos
  vistos por el usuario (`FeedDAO.getChannelWatchCounts`: suma de `repeat_count` del historial de vídeos
  subidos por el canal o que están en su feed; parte del historial, que es pequeño) y luego por nombre.
- 2026-10-25 (59): Bocetos de tablet y TV (pendiente de que el usuario elija):
  https://claude.ai/artifact/PudbyQtDEegn8uixJLFbRv — tablet horizontal: Inicio con barra lateral (rail)
  en vez de barra inferior y rejilla de 3 columnas; Vídeo con "A continuación" en un panel a la derecha;
  Música con carátula y controles a la izquierda y la cola a la derecha. TV (Android TV, mando): Inicio
  con rail de iconos, destacado grande con el foco (aro blanco) y filas horizontales; Reproductor a
  pantalla completa con controles grandes (−10/+10, calidad, subtítulos, velocidad) y "A continuación"
  abajo; Buscar con voz (micrófono del mando) + teclado en rejilla y resultados por plataforma.
- 2026-10-25 (60): Tablet y TV (el usuario dio el visto bueno a los bocetos de la entrada 59). YouTube Music
  pasa a ser la última plataforma en la búsqueda (YouTube · Twitch · YouTube Music; `SearchPlatformBar`),
  también en el móvil. Tablet (sw600dp) y TV (`layout-television`): `activity_main` con `NavigationRailView`
  (`Widget.PulsePlay.NavigationRail`, 88 dp) en vez de la barra inferior (mismo id; `BottomBar` y
  `MainFragment` usan `NavigationBarView`; el rail no se esconde con el teclado ni al expandir el
  reproductor). Inicio en rejilla según el ancho (`HomeScreen.columnsFor`: ≥600 dp 2, ≥1100 dp 3).
  Página del vídeo en tablet horizontal (`layout-large-land/fragment_video_detail.xml`): campana +
  "Suscribirse/✓ Suscrito" junto al canal (`videoprime/FollowButtons.kt`). Reproductor de música ≥840 dp:
  la cola a la derecha (`UpNextPanel`, 3 antes y 60 después de la actual, tocar = saltar; solo se rellena
  en pantallas anchas). TV: aro blanco en lo que tiene el foco del mando (`videoprime/FocusRing.kt`:
  tarjetas del Inicio, Suscripciones, Biblioteca y chips; con el dedo nunca hay foco, en el móvil no se
  ve) y el vídeo empieza siempre a pantalla completa con los controles encima del vídeo, como YouTube
  TV (`PlayerHelper.isStartMainPlayerFullscreenEnabled`). El móvil (<600 dp) usa los mismos layouts de
  siempre. Sin probar en tablet ni TV reales.
- 2026-10-25 (61): Calidad: 1440p/2160p no salían (ajuste heredado "Mostrar resoluciones más altas" apagado
  por defecto en NewPipe). Ahora encendido por defecto + migración `pulseplay_high_resolutions_done`. Con
  "la mejor" el reproductor (solo él: `ListHelper.getPlaybackDefaultResolutionIndex`; descargas siguen con la
  máxima) elige como mucho lo que la pantalla muestra (`ListHelper.screenResolution`: lado
  corto en píxeles → 1080p móvil, 2160p tele 4K); las demás se eligen a mano en el menú de calidad.
  Ventana flotante con banda negra arriba y vídeo descuadrado: la superficie se medía con el tamaño de la
  ventana pero se centraba en sus padres, que aún tenían el alto viejo de la página; ahora en PiP
  `ExpandableSurfaceView.onLayout` la traslada para centrarla en la propia ventana. Sin probar en el móvil.
- 2026-10-25 (62): Vídeo del usuario: ampliar/reducir la ventana flotante ya va bien. Quedaba: (a) con el
  botón "Emergente" la animación salía en negro (la página entera encogiendo; abrir el PiP a mano hace que
  Xiaomi no use el `sourceRectHint`); ahora en Android 12+ el botón arma el PiP automático y sale al
  escritorio (intent HOME desde el contexto de la app, para que `MainActivity.startActivity` no lo marque
  como "abrir otra pantalla"): mismo camino y animación que el gesto de inicio
  (`VideoDetailFragment.leaveForPictureInPicture`, `pipByButton` cuenta como "quiere ventana"); si no se
  abre, sigue como audio igual que al salir. (b) al volver a grande el vídeo se rayaba: durante 1 s tras
  salir del PiP y en vertical, `ExpandableSurfaceView` mide ya con 16:9 del ancho (lo que pondrá la página)
  en vez del alto viejo de la ventana (`onLeftPictureInPicture`). Sin probar en el móvil.
- 2026-10-25 (63): Ampliar/reducir y volver del PiP van bien (confirmado). El negro al pulsar "Emergente"
  venía de ajustar el vídeo a la ventana durante la animación de entrada: ahora
  `ExpandableSurfaceView.pipWindowBounds` espera 600 ms tras entrar (el vídeo conserva su tamaño mientras
  Android anima) y luego sigue a la ventana. Tablet sin aviso de versión nueva: con "Avisos instantáneos"
  apagados (lo normal en una instalación nueva) la app ni seguía el tema `vp_updates` ni atendía el push, y
  sin canales con campana el trabajo de fondo que comprueba la versión puede no correr. Ahora el tema
  `vp_updates` se sigue siempre que la build tenga Firebase (sin registrar nada en nuestro servidor;
  `InstantPush.followOnly`), el push "up" se atiende siempre y la app mira la versión también al abrirse
  (`App.onCreate`, cada 3 h como mucho). La tablet tiene que instalar esta APK a mano una vez.
- 2026-10-29 (64): Informe del usuario (5 problemas + imágenes), paso a paso. (1) Avisos: servidor OK (32/32
  YouTube, 7/7 Twitch; YouTube avisa ~1 min tras publicar). Riesgo en Twitch: el servidor contestaba a
  EventSub después de pedir el título a Helix y mandar el FCM; si Twitch ve respuestas lentas o fallidas
  apaga la suscripción y aquí seguía "7/7". Ahora `eventsub` guarda el directo en `tw_pending`, contesta 204
  al momento y la alarma (`sendPendingTwitch`, 3 por alarma, cuenta para el presupuesto de 50 peticiones)
  manda el push; cada 6 h `checkTwitchSubscriptions` lista las suscripciones en Helix y rehace las que no
  están `enabled`. `/status` da `lastTwitchEvent`, `lastTwitchPush` y `twitchCheck`. (2) 403 de YouTube:
  `Player.onPlayerError` saltaba la canción con un 403 sin pedir direcciones nuevas (la info sale de
  `InfoCache`). Ahora `retryWithFreshAddresses`: con 403/410 borra de la caché la actual y las 2 siguientes,
  `setRecovery` + `reloadPlayQueueManager` (mismo segundo) una vez por canción cada 5 min; si vuelve a
  fallar, salta como antes. No se registra la URL (lleva tokens). El vídeo de referencia p06G8u1Qgfo da
  "not available" desde la IP del entorno (bloqueo de centro de datos): no se pudo reproducir aquí.
  La vigilancia del 29-10 falló por "Network is unreachable" del runner (no es rotura). Issue #3 de
  upstream: solo una traducción.
  (3) Atrás: heredado de NewPipe, salir de pantalla completa con Atrás pausaba en el móvil (`player.pause()`)
  y en la página grande Atrás volvía a la canción anterior de la cola o recargaba la página anterior (ocultando
  y parando lo que sonaba). Ahora `VideoDetailFragment.onBackPressed`: pantalla completa → sale sin pausar;
  con un vídeo sonando → devuelve false y `MainActivity` encoge al mini reproductor (como YouTube); sin nada
  sonando, la pila de páginas como antes. Raíz con mini reproductor → la app se cierra y sigue como audio
  (sin cambios, entrada 22).
  Twitch en Helix (comprobado tras desplegar): 7 suscripciones `enabled`, ninguna rehecha; si un aviso de
  Twitch no llega, mirar `lastTwitchEvent`/`lastTwitchPush` en "estado de avisos".
  (4) Latencia de Twitch: lista HLS medida con red real: trozos de 2 s (`TARGETDURATION` 6), 2
  `#EXT-X-TWITCH-PREFETCH` y el último trozo completo listo 0-1,5 s después de ocurrir (un canal con 60 s
  era retraso puesto por el streamer). La app se quedaba 10 s detrás (`LIVE_STREAM_EDGE_GAP_MILLIS`, todos los
  directos). Ahora Twitch: objetivo 5 s, mínimo 3, máximo 12 (`PlayerDataSource.TWITCH_LIVE_*`,
  `PlaybackResolver.buildLiveMediaSource`); el control de velocidad de ExoPlayer sube el objetivo solo si hay
  cortes. YouTube en directo sigue igual. Esperado ~6-7 s detrás del streamer (antes ~11-12); la app de Twitch
  usa los trozos PREFETCH (~2-3 s): posible siguiente paso (convertirlos en trozos normales en el parser,
  como el modo "low latency" de Streamlink), no hecho. Sin medir en el móvil.
  (5) Imágenes (sobre todo canciones), sin bajar resolución. Medido: `vi_webp/<id>/hq720.webp` que no existe
  (canciones antiguas: 2 de 10 probadas) tarda 1,1-1,6 s en dar 404 frente a 0,12-0,2 s una que existe, y la
  lista de "no existe" era solo de memoria (se repetía en cada arranque). Ahora `BestThumbnail.CoilInterceptor`
  guarda las que no existen en `cacheDir/videoprime_missing_thumbnails.txt` (2000, solo con 404 de verdad: un
  fallo de red no cuenta; lectura/escritura en `Dispatchers.IO`). Portadas/avatares de googleusercontent en 3
  tamaños (360/720/1200, nunca menos de lo que se dibuja) en vez de 9: la misma canción en lista, reproductor y
  notificación comparte descarga. `SmoothImage(identity=…)`: la imagen vieja solo se mantiene si es del mismo
  vídeo/canal y también se mantiene ante un fallo de red. Debug: `BestThumbnail.debugTimings` registra tiempo y
  origen (memoria/disco/red) de cada imagen con la etiqueta `PulsePlayImages`. Test `BestThumbnailTest`.
  Revisión (agentes): la marca "no existe" caduca a los 3 días ("url\tmillis"; un vídeo recién subido da 404 en
  hq720 unos minutos y no debe quedarse en baja), archivo con su propio candado y escritura `NonCancellable`;
  servidor: la alarma de 1 s de los avisos pendientes se pone al final de `alarm()` (el mantenimiento la pisaba
  con la de 60 s), la comprobación de Twitch no toca nada si la lista de Helix quedó incompleta y si falla espera
  15 min.
- 2026-10-29 (65): Foto + vídeo del usuario: "PulsePlay no responde" (ANR) tras pantalla completa → minimizar
  dentro de la app → completa → minimizar, con transición rayada. Causa probable: `pipArmLayoutListener` hacía
  `post(armPictureInPicture)` en CADA cambio de layout del vídeo (la hoja y la pantalla completa lo mueven en
  cada fotograma) y cada vez `setPictureInPictureParams` + 2 PendingIntent = llamadas síncronas al sistema, que
  durante una animación/giro está ocupado. Ahora espera a que el vídeo deje de moverse (250 ms,
  `armPictureInPictureLater`), `VideoPip.arm` no repite si nada cambió (`armedWith`: activado, forma, rect,
  play) y `MainActivity.onUserLeaveHint` arma con la posición actual antes de salir. Diagnóstico: 
  `videoprime/LastExitReport.kt` (Android 11+, `getHistoricalProcessExitReasons`): si la última salida fue un
  ANR, al abrir la app sale una notificación de error con la traza (hilo main primero) en los detalles
  técnicos; la primera vez solo apunta la marca. Ventana flotante: al entrar, el marco del vídeo de la página
  conservaba el alto de la página/pantalla completa hasta el ajuste posterior → vídeo más abajo que la ventana
  (tramo negro y luego salto); ahora `onPictureInPictureChanged(..., newConfig)` pone ya el alto de la ventana
  (`screenHeightDp`). "Que empiece más grande": Android no deja fijar el tamaño; en AOSP el mínimo del manifiesto
  (`<layout android:minWidth="320dp" android:minHeight="180dp">` en MainActivity) pasa a ser el tamaño por
  defecto del PiP; en Xiaomi sin confirmar. Revisión: `armedWith` se borra en `enter`, `update` y al cambiar
  `active` (si no, tras el PiP no se volvía a mandar el rect); `LastExitReport` marca la hora la primera vez
  aunque no haya salidas y apunta lo visto después de notificar (traza ≤30k).
- 2026-10-29 (66): Traza del ANR (gracias a `LastExitReport`): el hilo main esperaba en un CountDownLatch dentro
  de `RecyclerView` → `StreamMiniInfoItemHolder.updateFromItem` → `historyRecordManager.loadStreamState(..)
  .blockingGet()` (heredado de NewPipe: lee el progreso visto de la BD en el hilo principal; con la BD ocupada
  por el feed/avisos en segundo plano esperaba >5 s al maquetar "Siguiente" al abrir/cerrar pantalla
  completa). Ahora `loadProgress` lo pide en segundo plano y lo pinta al llegar si la fila sigue siendo de ese
  vídeo (`progressUrl`, `Disposable` cancelado al reutilizar); igual en `updateState`. Otros `blocking*` de la
  app están en hilos de fondo. Avisos comprobados tras los cambios: servidor local (`wrangler dev`, scratchpad
  `wr/flow.mjs`): EventSub de directo contestado en 11 ms, repetido ignorado y la alarma intenta el FCM a
  `tw_<login>`; la ruta de YouTube no cambió (diff) y el aviso real de las 16:01 llegó.
- 2026-10-29 (67): Rayado/negro al entrar y salir de pantalla completa (vídeos del usuario): al girar el móvil
  `MainActivity.onConfigurationChanged` hacía `recreate()` y el vídeo perdía su superficie un momento. En el
  móvil no hace falta (sin layouts propios en horizontal salvo la cola clásica, otra actividad, y la tarjeta
  de lista): ahora solo se recrea en tablet/TV; en el móvil `VideoDetailFragment.onOrientationChanged` hace lo
  que hacía la reconexión tras recrear (horizontal → `checkLandscape`, vertical con pantalla completa →
  `toggleFullscreen`, `hideSystemUiIfNeeded`, `setHeightThumbnail` tras el siguiente layout). Revisión del
  progreso asíncrono: sin fallos (posible parpadeo mínimo de la barra al volver a pintar una fila vista).
  Revisión del giro: `values-land` (≈35 dimens de listas) solo se aplica al inflar → ahora el giro sin
  recrear es solo con un vídeo en pantalla (`VideoDetailFragment.isShowingVideo`: hoja EXPANDED + reproductor
  de vídeo); en cualquier otra pantalla, y en tablet/TV, `recreate()` como antes.
- 2026-10-29 (68): Vídeo del usuario (botón "Emergente"): la ventana encogía la app entera (vídeo arriba y negro
  debajo) y luego salía un cuadro negro antes del vídeo. Referencia abierta: LibreTube (cliente de YouTube con el
  reproductor dentro de la actividad principal) al entrar en PiP pasa el reproductor a un diálogo a pantalla
  completa (`openOrCloseFullscreenDialog`, sin `sourceRectHint`): toda la ventana es el vídeo. AOSP
  (`PipUtils.calcEndTransform`, `PipBoundsAlgorithm.isSourceRectHintValidForEnterPip`, umbral de forma 0,01;
  con pista inválida usa la "content overlay", el cuadro negro) anima la app YA maquetada al tamaño de la
  ventana: ese primer layout debe ser solo el vídeo llenándola. Nuestro retraso de 600 ms (entrada 63) hacía lo
  contrario. Ahora `ExpandableSurfaceView.pipWindowBounds` sigue a la ventana desde el primer layout y
  `onPictureInPictureChanged` expande el AppBar sin animación + alto de la ventana al momento (la causa del negro
  de antes eran los padres con el alto viejo, ya resuelto en la entrada 65). Revisión: también
  `hidePlayerControlsNow()` y `surfaceView.requestLayout()` ahí (camino sin armar). Código de referencia en
  scratchpad/ref (no se sube).
- 2026-10-29 (69): Vídeo del usuario con la 68: ahora negro durante TODA la animación (al redimensionar la
  SurfaceView al tamaño de la ventana se tira su imagen: negro hasta el siguiente fotograma; antes, con el
  retraso de 600 ms, el negro era el cuadro del final). Pidió la animación de antes + el final de ahora. Ahora en
  PiP `ExpandableSurfaceView` NO cambia de tamaño: congela el ancho que tenía en la página (`pipWidth`, alto =
  ancho/forma del vídeo) y se ESCALA (`setScaleX/Y`, pivote centro) y traslada para llenar y centrarse en la
  ventana (`updatePipTransform`, también desde un `OnGlobalLayoutListener` mientras está en PiP, para el
  redimensionado). Escalar no tira la imagen: sin negro al entrar, al estabilizarse ni al cambiar el tamaño de
  la ventana. Al salir se quita el listener y vuelve el escalado del modo ZOOM y traslación 0. Revisión:
  `stopPip()` (desde la medida fuera de PiP, `onLeftPictureInPicture` y el propio listener si ya no hay ventana),
  listener movido en `onAttachedToWindow`/quitado en `onDetachedFromWindow`, ancho de la página guardado en
  cada medida normal (`pageWidth`) y comprobación de alto 0.
- 2026-10-29 (70): Con el escalado (69) seguía el negro durante la animación y antes de recuperarse salía un
  fotograma rosa roto (típico de un decodificador que se apaga y se enciende a mitad de GOP, no de cambiar
  tamaño/escala). En vez de más hipótesis, `videoprime/PipLog.kt`: registro en memoria (300 líneas, con ms e hilo)
  de onUserLeaveHint, estados/cambios de PiP, onPause/onResume/onStop de la página, `useVideoAndSubtitles(x)` y
  quién lo llama, `surfaceCreated/Changed/Destroyed`, `onRenderedFirstFrame`, `arm`/`enter`, inicio/escala/fin
  del escalado. Se comparte desde Ajustes → Vídeo y audio → "Registro de la ventana flotante" (no se envía solo).
  Prueba de arranque del CI: falló una vez sin traza (proceso desaparecido); repetida con el mismo código pasó;
  `smoke_launch.sh` ahora anota el motivo (muerte/ANR/fallo nativo).
- 2026-10-30 (71): Vídeo del usuario (botón "Emergente", fotograma a fotograma): al tocar, la pantalla entera se
  pone negra (transición de "ir al escritorio" de Xiaomi, no la de la ventana), luego la ventana sale negra
  ~0,7 s y el vídeo reaparece más adelante (siguió sonando sin imagen). Causa: el camino de la entrada 62 (salir
  al escritorio con un intent HOME y que Android abra la ventana sola) oculta la app un momento → se destruye la
  superficie del vídeo → negro hasta el siguiente fotograma clave (de ahí también el fotograma rosa de la 70).
  Ahora el botón vuelve a abrir la ventana desde dentro de la app (`VideoPip.enter`, la app no deja de verse y la
  superficie no se toca), como las versiones en las que se veía la imagen; antes se ocultan controles y página
  (`hidePlayerControlsNow`) para que solo encoja el vídeo. Fuera `leaveForPictureInPicture`/`pipByButton`. Salir
  con el gesto de inicio (ventana automática) no cambia. El escalado de la superficie en la ventana (69) sigue.
- 2026-10-30 (72): Vídeo + "Registro de la ventana flotante" del usuario con la 71: la superficie ya NO se destruye
  (sin surfaceDestroyed ni useVideoAndSubtitles(false)), pero seguía ~1 s de negro (pantalla entera y luego la
  ventana). El registro lo explica: 35 ms después de `enter`, `isInPictureInPictureMode()` ya es true pero la
  ventana sigue siendo la pantalla entera (1220x2712); `updatePipTransform` centraba el vídeo en esa pantalla
  (bajaba ~900 px) mientras Android anima desde el `sourceRectHint` (el sitio del vídeo arriba, ya negro). Ahora
  `ExpandableSurfaceView.isSmallWindow`: sin escala ni traslación hasta que el área de la ventana es <60 % de la
  pantalla (`getMaximumWindowMetrics`; ni la forma —la pantalla puede tener la del vídeo— ni el ancho —la ventana
  medía el 97 % del ancho— sirven). Probablemente era también el negro de las entradas 69-70.
  CONFIRMADO por el usuario (APK 97eddbf): "Emergente" ya se ve sin negro. No tocar este camino sin motivo.
- 2026-10-30 (73): Foto del coche: tarjeta de PulsePlay sin nada sonando ("Toca para abrir", sin fondo) e icono
  pixelado. Icono: el coche dibuja el icono adaptativo (vector) a la densidad baja de su pantalla y lo amplía →
  ahora el servicio multimedia (`PlayerService`) lleva `android:icon="@drawable/pulseplay_car_icon"` (PNG 512 px en
  `drawable-nodpi`, renderizado con Playwright desde `scratchpad/logo/icon512.svg`) y el SmallIcon del coche es el
  icono blanco `ic_newpipe_triangle_white` (antes el mipmap a color). Sin probar en el coche. Fondo: posible (la
  tarjeta usa la carátula de los metadatos de la sesión; sin nada sonando se pueden publicar metadatos propios con
  una imagen en `PlayerService`). Bocetos A Onda de marca / B Seguir escuchando (última canción) / C Cristal neón /
  D Minimal: https://claude.ai/artifact/LCf7uNDWJY1r34hHMeWUKo (pendiente de que el usuario elija). Mientras suena,
  el fondo es siempre la carátula de la canción (lo pone Android Auto).
  Elegido A: `videoprime/CarIdleCard.kt` publica metadatos propios en la sesión sin reproductor (título "PulsePlay",
  subtítulo `pulseplay_car_idle_subtitle`, imagen `drawable-nodpi/pulseplay_car_background.png` 512 px: degradado
  del logo + onda cian, de `scratchpad/carbg.svg`) desde `PlayerService.onCreate` y tras destruir el reproductor en
  `cleanup()` (`setPlayer(null)` deja metadatos vacíos). Al reproducir, el conector los sustituye (deduplica contra
  los metadatos actuales de la sesión, comprobado en el bytecode de 2.19.1). Sin probar en el coche.
- 2026-10-31 (74): Comprobaciones. CI: APK y comprobaciones en verde. Avisos (`/status`): 32/32 YouTube, 7/7 Twitch
  `enabled`, último directo de Twitch (ibai) avisado en 0,4 s. Vigilancia del 30-10 OK (vídeos recientes bloqueados
  por la IP de GitHub, como siempre). Velocidad medida con red real (`scratchpad/TimingTmpTest.java`, no se sube):
  la respuesta reproducible (VISIONOS `player`) llega en ~150 ms, pero `StreamInfo` espera a `next` (0,6-2,3 s) y al
  `player` WEB de metadatos (0,1-1,3 s) → 0,7-2,2 s por vídeo; el PRIMER vídeo tras abrir la app tardaba 3,4 s
  (versión del cliente de sw.js_data + visitorData de 2 clientes en serie). Extractor: `YoutubeStreamHelper.warmUp`
  (mismas claves de caché que los `player`); app: `StreamPrefetch.warmUp` desde `MainActivity.onStart` (cada 9 min
  como mucho, con cualquier red) → primer vídeo 2,5 s (el resto es `next`). Propuesto (no hecho): arrancar la
  reproducción sin esperar a `next`/WEB (pasaría a ~0,2-0,5 s). REA sigue descartada (ver Herramientas): para
  inspirarse se leyeron las funciones de apps abiertas (Metrolist, OuterTune, ViTune, Auxio, LibreTube, Harmonoid).
- 2026-10-31 (75): Fase 6 — sincronización móvil/tablet/tele (el usuario eligió: código de vinculación sin
  cuentas, sincronizar suscripciones+campanas, historial+progreso, listas+colas y ajustes principales, al momento).
  Servidor (`push-server`, mismo DO): `/sync/create` (grupo + token del aparato; solo se guarda el sha256 del
  token), `/sync/code` (6 letras sin 0/O/1/I, 10 min, un uso), `/sync/join`, `/sync/push` (≤500 elementos, valor
  ≤300 KB, cada uno con la versión siguiente del grupo; gana el último; `ping:false` en tandas intermedias),
  `/sync/pull?since=` (páginas de 500), `/sync/devices`, `/sync/leave`; tras cada subida, FCM sin contenido al tema
  `sync_<grupo>` ({t:"sync", d:aparato}). Límite por IP de crear/unirse. `/status` da `syncGroups/Devices/Items`.
  Tests `test/sync.test.js` y flujo local con `wrangler dev` (scratchpad `wr/sync.mjs`).
  App (`videoprime/sync/`): `SyncApi` (HTTP), `SyncSections` (sub, hist, plist, rplist, queue, pref: cada una da una
  instantánea clave→JSON y aplica cambios con SQL dentro de `runInTransaction`), `SyncManager` (un hilo; detecta
  cambios comparando la instantánea con hashes guardados en `files/pulseplay_sync/<tipo>.json`; baja primero y
  no pisa lo que este aparato cambió y aún no subió; al unirse gana el grupo y no se aplican borrados viejos;
  se dispara con el InvalidationTracker de Room —sin `streams`, que el feed escribe sin parar—, las preferencias de
  la lista, `QueueStore.onSaved`, al abrir la app y con el push), `SyncActivity` (Ajustes → Sincronización: empezar
  aquí / unirme con código / mostrar código / aparatos / dejar de sincronizar). Las listas locales llevan un id de
  sincronización (`SyncIds`, prefs `pulseplay_sync`). Ajustes sincronizados: `PreferenceSection.SYNCED_KEYS`.
  El historial se guarda como una fila por vídeo (última vez + total de veces). Sin probar con dos aparatos reales
  (el entorno no llega a *.workers.dev).
  Revisión (agente) → arreglado: nada de `ON CONFLICT DO UPDATE` (SQLite de Android <11 no lo tiene: INSERT OR
  IGNORE + UPDATE); una sincronización pedida mientras otra corre (cambio, push, reintento) se hace justo después
  (`running`/`rerunDelay`; antes se perdía, también el reintento de 5 min); un elemento demasiado grande se queda en
  el aparato y se avisa ("A list is too big to sync") en vez de bloquear todo (servidor: valor hasta 1 MB, ~3000
  canciones por lista); dejar el grupo conserva los ids de las listas (`playlist_ids`), si no, al volver se
  duplicaban; "uniéndose" = sin archivos de estado (antes `version == 0`); `QueueStore.update` (leer+guardar en un
  paso); el avatar de un canal no se borra si el otro aparato no lo tiene; la copia de seguridad de Android no copia
  la identidad de sincronización (`pulseplay_backup_rules.xml`, `pulseplay_data_extraction_rules.xml`).
- 2026-10-31 (76): Fotos del usuario. (1) Sincronización: el móvil ya creó su grupo (servidor: 1 grupo, 1 aparato,
  1085 elementos); la pantalla decía "Aún no se ha sincronizado" porque no se refrescaba: ahora relee cada 2 s.
  (2) Títulos en inglés en la pestaña del canal: el extractor de upstream fuerza YouTube a en-GB (#196) y YouTube
  da el título traducido que pone el creador. Extractor (`YoutubeService.SUPPORTED_LANGUAGES` = en-GB + es-ES):
  con el idioma del móvil (es-ES) salen los originales. Arreglos para el español: `Utils.mixedNumberWordToLong`
  entiende "17 K"/"3,9 M"/"17 mil"/"1,2 mil M" con espacio no separable (U+00A0; antes 17 K = 17), redondeando;
  `TimeAgoParser` cambia espacios no separables por normales; "Sin visualizaciones" = 0
  (`YoutubeStreamInfoItemExtractor.isNoViewsText`); estrenos "Se estrena el / Programado para el" y fecha d/M/yy en
  `YoutubeStreamInfoItemLockupExtractor`. Probado con red real (búsqueda, canal y pestañas, vídeo, relacionados,
  tendencias): títulos, fechas, duraciones y cifras bien; los relacionados traen fechas cortas ("hace 2 m") que no
  se convierten en fecha (se muestran como texto). Pendiente por si se nota: fecha de subida de la página del vídeo
  si YouTube no da la ISO (en inglés usa patrones ingleses). `YoutubeChannelLocalizationTest` añadido a fallos
  conocidos (recorre idiomas con forceLocalization sin respuestas grabadas para es-ES).
  (3) Corte de sonido al pasar de vídeo a segundo plano: `Player.handleIntent` quitaba la superficie del vídeo
  (al destruir la UI principal) con la pista de vídeo aún activa; ExoPlayer esperaba al decodificador y el audio se
  paraba un instante. Ahora apaga la pista de vídeo ANTES de cambiar la UI. Sin probar en el móvil.
- 2026-10-31 (77): Tele: misma APK (leanback, banner, sin táctil). Primera instalación con la app Downloader; luego
  se actualiza sola. En Android TV las notificaciones casi no se ven → `videoprime/UpdateNotice.kt`: en la tele
  (`DeviceUtils.isTv`), al volver a la app (`MainActivity.onResume`) mira la versión (como mucho cada 3 h) y, si hay
  una nueva (`UpdateChecker.pendingUpdate`, guardada en prefs `pulseplay_update_pending_*` y borrada cuando la última
  es la instalada), ofrece "Actualizar / Más tarde" (una vez por arranque de la app) → `UpdateActivity`.
  Enlace fijo para escribirlo con el mando: la nightly publica también `PulsePlay.apk`
  (https://github.com/GHD235/VideoPrime/releases/download/nightly/PulsePlay.apk); `UpdateChecker` lo ignora
  (su expresión pide nombre con versión y commit).
  Foto de la tele: la flecha de "tirar para actualizar" del Inicio se quedaba fija arriba (el mando, al mover el
  foco en lo alto de la lista, alimenta el gesto y no lo suelta). En la tele `HomeScreen.Feed` no dibuja el
  indicador ni muestra "actualizando" (se actualiza sola al abrir).
- 2026-10-31 (78): Fotos de la tele. Tele y tablet unificadas (`DeviceUtils.isBigScreen` = tele o tablet): cada vídeo
  nuevo empieza a pantalla completa con los controles encima (`PlayerHelper.isStartMainPlayerFullscreenEnabled`).
  Antes en la tele no pasaba: `MainPlayerUi.directlyOpenFullscreenIfNeeded` pedía tablet + giro bloqueado, y además
  `toggleFullscreen` no hace nada si aún no existe el ExoPlayer (al retomar desde el historial la cola se carga más
  tarde). Ahora queda pendiente (`pendingAutoFullscreen`) y se reintenta en `onPlaying` con `toggleFullscreen` directo (sin girar: una tablet en vertical bloqueada giraba sin pantalla completa); no se aplica si la página ya
  se bajó al mini reproductor (`VideoDetailFragment.isMinimized`). En la tele se ocultan "Añadir a", "Segundo plano"
  y "Emergente" en la página del vídeo. "EN DIRECTO" es ahora una píldora que el mando puede enfocar (aro blanco):
  roja en el borde del directo, con contorno si vas por detrás; pulsarla vuelve al directo
  (`VideoPlayerUi.updateLiveSyncButton`, `pulseplay_live_pill_on/off`). Twitch se paraba a veces: margen del
  directo 8 s (mín. 6, máx. 20; antes 5/3/12) y nunca a más de 1,02× para alcanzarlo (`TWITCH_LIVE_MAX_SPEED`).
  Sin probar en la tele ni en la tablet.
- 2026-10-31 (79): Móvil, tablet y tele con la misma interfaz, decidida por el espacio y no por el aparato (boceto de
  tablet/TV, entrada 59). Barra lateral: `layout-w600dp/activity_main.xml` (antes `layout-sw600dp` y
  `layout-television`): cualquier pantalla de 600 dp o más de ancho, también el móvil girado. Inicio en rejilla por
  ancho (ya lo era). Reproductor de música: girado (ancho > alto y ≥560 dp) carátula a la izquierda y canción y
  controles a la derecha (`PlayerLandscape`; piezas `TopBar`/`AnimatedCover`/`TitleRow`/`Controls`/`QueueRow`
  compartidas); con ≥840 dp además la cola a la derecha. Vídeo a pantalla completa (igual en los tres): botones
  −10/+10 s junto a los grandes (`seekBackwardButton`/`seekForwardButton`, duración de los ajustes) y fila "A
  continuación" encima de la barra (`videoprime/PlayerUpNext.kt`: siguientes de la cola o, si la cola acaba aquí,
  los relacionados; tocar uno lo pone en la cola y lo reproduce); `MainPlayerUi.updateFullscreenExtras` en cada
  cambio de pantalla completa, de vídeo y de cola. Botones redondos con aro blanco al tener el foco del mando.
  Revisión: el móvil girado con un vídeo delante no se recrea (entrada 67) y se quedaba con la barra del otro ancho;
  ahora `MainActivity.pendingRecreate` (si cambia el lado de 600 dp) y `recreateIfPending()` al bajar/cerrar el
  vídeo (callback de la hoja) o en `onResume`. Sin probar en aparatos reales.
- 2026-10-31 (80): "Calidad siempre al máximo" (petición del usuario). Los directos (Twitch, YouTube en directo) y
  los vídeos DASH son adaptativos y ExoPlayer bajaba la calidad si estimaba poca red: ahora
  `setForceHighestSupportedBitrate(true)` y sin límite de tamaño de pantalla (`Player`, al crear el trackSelector).
  "La mejor" ya no se limita a la pantalla (se quitó `ListHelper.screenResolution` de la entrada 61: en una tele 4K
  con menús a 1080p daba 1080p). Migración única `pulseplay_max_quality_done` (`MainActivity.maxQualityOnce`):
  calidad por defecto "la mejor" y sin límite con datos móviles. Contrapartida: con red floja se para en vez de
  bajar. Foto de la tele: con el vídeo a pantalla completa el mando podía llevar el foco a la barra lateral de
  detrás (marco blanco sobre el vídeo); `moveFocusToMainFragment` bloquea también el foco de `BottomBar.view`.
- 2026-10-31 (81): Corte de sonido al pasar de vídeo a segundo plano (seguía tras la entrada 76). Causa:
  `VideoDetailFragment.openBackgroundPlayer` hace `player.setRecovery()` y manda la misma cola; en
  `Player.handleIntent` (misma cola de un vídeo con posición guardada) se hacía `seekTo` a esa posición, que es
  donde ya estaba: el salto vacía el búfer y corta el sonido. Ahora solo salta si hay más de 2 s de diferencia
  (`SAME_PLACE_MILLIS`), si el reproductor estaba parado o si el elemento es otro. Sin probar en el móvil.
- 2026-10-31 (82): Vídeo del usuario: el botón "EN DIRECTO" parpadeaba (rojo ↔ contorno cada segundo).
  `Player.isLiveEdge` comparaba con la posición por defecto de la ventana y el control de velocidad del directo
  oscila alrededor del objetivo; ahora usa `getCurrentLiveOffset()` ≤ objetivo + 5 s (`LIVE_EDGE_MARGIN_MILLIS`).
  Pulsarlo sigue llevando al directo (`seekToDefaultPosition`). Informe de error del usuario: con YouTube en español
  los relacionados traen fechas cortas ("hace 1 h", "hace 39 min", "hace 4 m") y TODA la lista daba
  "Some info not extracted". Extractor `a314d02e`: `es` entiende s/seg., min, h, d, sem., m (= meses; los minutos
  son "min") y a; `YoutubeStreamInfoItemLockupExtractor.getUploadDate` devuelve null ante una forma desconocida
  (se queda la fecha en texto). Test `TimeAgoParserTest.parseSpanishShortForms`. OJO: `PatternsManager` busca
  `es_ES` y cae a null; la app usa `Localization("es")`.
  Revisión (agente) del corte de sonido: además `handleIntentPost` ya no repite `useVideoAndSubtitles(false)` si la
  pista de vídeo ya está apagada, el salto no usado borra la posición guardada (`unsetRecovery`) y el "tunneling"
  (audio atado al decodificador de vídeo: apagar la imagen reconfigura el audio) se apaga por defecto una vez
  (`MainActivity.noTunnelingOnce`, clave `pulseplay_no_tunneling_done`; se puede volver a activar en ExoPlayer).
  Bocetos del reproductor a pantalla completa (pendiente de que el usuario elija):
  https://claude.ai/artifact/65TQ1avgVcoV4opruUuH8t — A Cine limpio / B Cristal neón / C Panel lateral + Directo.
  Antes pendiente: bocetos del reproductor a pantalla completa más moderno (lienzo vacío creado:
  https://claude.ai/artifact/65TQ1avgVcoV4opruUuH8t). Historial: no hace falta borrarlo (tabla pequeña; el ANR de la
  entrada 66 ya no lee la BD en el hilo principal).
- 2026-10-31 (83): El usuario eligió el diseño B ("Cristal neón") del reproductor a pantalla completa
  (https://claude.ai/artifact/65TQ1avgVcoV4opruUuH8t; los tableros del directo se rehicieron en estilo B: "en vivo" y
  "por detrás", antes parecían dos barras a la vez). `player.xml`: `bottomPanel` (vertical) agrupa `bottomControls` y
  `upNextPanel` (ahora debajo de la barra, título "A CONTINUACIÓN" en cian); la píldora del directo va la primera de la
  fila. `MainPlayerUi.applyFullscreenStyle` (desde `updateFullscreenExtras`): en pantalla completa panel de cristal
  (`pulseplay_glass_panel`, márgenes 28 dp), título en píldora (`pulseplay_glass_pill`), play de 96 dp con degradado
  violeta y resplandor (`pulseplay_play_glow`, aro blanco con el foco) y −10/+10 de 64 dp; fuera, el aspecto de
  siempre. Directo: "● EN DIRECTO" roja en vivo / "Ir al directo" con contorno por detrás
  (`VideoPlayerUi.updateLiveSyncButton`, `pulseplay_go_live`, `pulseplay_live_dot`). Sin probar en aparatos.
  Revisión: la vista del reproductor se reutiliza (p. ej. en la emergente) → `MainPlayerUi.destroy` quita el estilo
  B, los −10/+10 y "A continuación"; `PopupPlayerUi` sube `bottomPanel` (no `bottomControls`); con pantalla baja
  (<480 dp, móvil girado) el play es de 76 dp para no tapar "A continuación". La píldora del título ocupa media fila
  (su peso se deja: quitarlo podía dejar sin sitio la pista de audio).
- 2026-10-31 (84): "Aplica el B para todo": también el reproductor pequeño de la página del vídeo (en vertical).
  `MainPlayerUi.applyStyle(STYLE_PLAIN|COMPACT|FULL)` sustituye a `applyFullscreenStyle`: compacto = panel de cristal
  con márgenes 8 dp, play violeta de 64 dp con brillo y −10/+10 de 44 dp (huecos de 12 dp para que quepan los 5
  botones en 360 dp); completo = lo de la entrada 83 + "A continuación"; plano = el aspecto de NewPipe para cuando la
  vista pasa a otra UI (`destroy`). Se aplica también al empezar (`setupAfterIntent`). Sin probar en aparatos.
  Revisión: el plano devuelve los huecos de 20 dp de anterior/siguiente (la emergente los heredaba a 12) y los
  −10/+10 aparecen y se ocultan con el play al cargar (`VideoPlayerUi.animatePlayButtons`, bandera
  `seekButtonsInUse`: la animación de ocultar deja la vista en GONE, no sirve mirar la visibilidad).
- 2026-10-31 (85): Foto de la Biblioteca: Historial pasa a ser el primer acceso (`LibraryScreen`). Aviso de versión
  nueva DENTRO de la app en todos los aparatos (antes solo en la tele; en el móvil no salía porque solo hay
  notificación y la comprobación es cada 3 h): `UpdateNotice.offer` (desde `MainActivity.onResume`) mira GitHub
  como mucho cada 15 min (`UpdateChecker.OPEN_CHECK_INTERVAL_MILLIS`, `checkIfDue(context, interval)`) y muestra una
  tarjeta Compose moderna igual en móvil y tele (icono, "VERSIÓN NUEVA" en cian, título, botón degradado
  "Actualizar" con el foco del mando y "Más tarde" con borde; `focusRing`). Botón "EN DIRECTO": si ya estás en
  directo no salta (un salto vaciaba el búfer = parón) y si estaba en pausa reanuda. Foto del móvil girado: los
  botones tapaban la barra y "A continuación" ocupaba media pantalla. Ahora `applyStyle` tiene 3 tamaños por alto
  de pantalla (bajo <480 dp: play 64, −10/+10 48, márgenes 16; medio: 84/56; grande ≥800: 96/64), "A
  continuación" compacta en pantallas bajas (`PlayerUpNext.setCompact`: 112 dp y una línea) y los botones redondos
  (`centerControls`) se centran en el hueco que deja el panel (relleno inferior = alto del panel, recalculado al
  cambiar el panel). Sin probar en aparatos.
  Revisión: el escuchador del panel se quita en `MainPlayerUi.destroy` (las vistas sobreviven a la UI: se acumulaban
  y peleaban por el relleno); la tarjeta se cierra con la pantalla (`DefaultLifecycleObserver.onDestroy`) y, si no la
  contestó el usuario, se vuelve a ofrecer; no sale en la ventana flotante ni si la actividad se cierra.
- 2026-10-31 (86): Pregunta del usuario: ¿cómo se ve en vertical y cómo mejorarlo? Bocetos en el mismo lienzo
  (https://claude.ai/artifact/65TQ1avgVcoV4opruUuH8t, fila "Móvil en vertical"): Ahora (B compacto: 5 botones sobre un
  vídeo de 220 dp, botones redondos, pestañas Siguiente/Comentarios/Descripción), Propuesta 1 limpio (solo play en el
  vídeo, ±10 con dos toques, barra fina; tarjeta de título y canal; acciones como píldoras deslizables; "A
  continuación" con la siguiente grande y cuenta atrás "Siguiente en 8 s · Cancelar") y Propuesta 2 (vídeos y
  directos verticales a pantalla completa, botones a la derecha, deslizar arriba = siguiente). Pendiente de que elija.
- 2026-11-01 (87): El usuario se queda con el vertical actual (B compacto). En la tele tampoco "Descargar": fuera de la
  página del vídeo y, en el menú de cada vídeo (`InfoItemDialog.Builder.create`, por recurso de texto), fuera Segundo
  plano, Emergente, Descargar y Añadir a la lista. Si no queda ninguna (nada sonando), el menú no se abre.
- 2026-11-01 (88): Foto del móvil girado: los botones redondos quedaban altos (centrados entre el borde y el panel,
  sin contar la fila del título). `MainPlayerUi.updateCenterOffset`: relleno arriba = `topControls.getBottom()` y abajo
  = alto del panel + margen, recalculado al cambiar cualquiera de los dos (mismo escuchador, quitado en `destroy`).
- 2026-11-01 (89): Búsqueda en la tele sin sugerencias. Causas: Twitch no tiene extractor de sugerencias (si la
  plataforma elegida era Twitch no salía nada) y el teclado de la tele tapaba la lista. Ahora
  `ExtractorHelper.suggestionsFor` usa las de YouTube si el servicio no tiene (sigue el ajuste de sugerencias
  remotas), el campo lleva `flagNoExtractUi` y en la tele hay búsqueda al momento (`SearchFragment.liveSearch`):
  0,9 s después de dejar de escribir (≥2 letras, no enlaces) carga los resultados sin cerrar el teclado; la consulta
  solo se guarda en el historial al elegir un resultado (`saveLiveSearch`, sin atar a la pantalla) y un error de esa
  búsqueda no cierra el teclado. Sin probar en la tele.
- 2026-11-01 (90): Foto del coche: la tarjeta de PulsePlay seguía con "Toca para abrir" (texto de Android Auto, no el
  nuestro) y sin fondo; en la vista compacta sí sale el icono. Sin reproductor la sesión tenía estado STATE_NONE y
  Android Auto ignora entonces los metadatos. `CarIdleCard.show` pone además estado PAUSED en 0 con solo las acciones
  que el preparador atiende sin reproductor (reproducir desde la lista del coche o por voz); la sesión sigue inactiva
  (no se queda con los botones multimedia del móvil). Sin probar en el coche.
- 2026-11-01 (91): Con la 90 la tarjeta del coche seguía en "Toca para abrir". Causa más probable: Android Auto dibuja
  esa tarjeta pidiendo al navegador multimedia la raíz "reciente" (`EXTRA_RECENT`, la de reanudar) y
  `MediaBrowserImpl.onGetRoot` (heredado de NewPipe) devolvía null. Ahora devuelve `ID_RECENT_ROOT` y
  `populateRecent` da un elemento reproducible: título "PulsePlay", "Seguir escuchando: <último del historial>"
  (`pulseplay_car_recent`), imagen `pulseplay_car_background` (android.resource) y id `history/<streamId>` (lo
  reproduce el preparador). Efecto secundario: Android 11+ puede ofrecer también reanudar PulsePlay en los controles
  multimedia del móvil. Sin historial no hay elemento. Sin probar en el coche.
- 2026-11-01 (92): Fotos de la tele: (1) recuadro blanco cuadrado alrededor del play: es el `FocusOverlayView` de
  NewPipe (dibuja un rectángulo sobre lo que tiene el foco en la tele). Ahora no lo dibuja sobre las vistas marcadas con
  la etiqueta `R.id.pulseplay_own_focus_ring` (`FocusOverlayView.drawsOwnFocus`): botones redondos del reproductor,
  píldora del directo y "A continuación" (ya tienen su aro). (2) Bocetos de la tele en el lienzo
  https://claude.ai/artifact/65TQ1avgVcoV4opruUuH8t (fila "Tele"): pantalla completa con el título entero (en la foto se
  cortaba la 2.ª línea), al acabar "Siguiente en 5 s" con Ver ahora / Cancelar / Volver a ver (hoy no sigue solo:
  "Poner en cola automáticamente" apagado), vista pequeña A (vídeo y datos a la izquierda, lista a la derecha) y B
  (vídeo arriba, fila de siguientes abajo). Pendiente de que el usuario elija.
- 2026-11-01 (93): El usuario aprobó los bocetos de la tele 1, 2 y la vista pequeña B. Hecho: (1) título entero en
  pantalla completa (`MainPlayerUi.applyStyle`: 2 líneas sin marquesina y fila de altura libre para que quepa el canal);
  (2) al acabar, `videoprime/PlayerEndCountdown.kt` (Compose sobre la raíz de `player.xml`): "Siguiente en 5 s" con el
  primer relacionado (sin Shorts ni lo que ya está en la cola), Ver ahora / Cancelar / Volver a ver y fila "Después";
  bloquea el foco de los controles de debajo mientras se ve; se quita en onPlaying/onMetadataChanged/destroy. Solo con
  "Poner en cola automáticamente" (encendido una vez: `pulseplay_autoplay_next_done`) y sin repetir;
  `Player.maybeAutoQueueNextStream` ya no añade nada con el reproductor de vídeo principal (lo hace la cuenta atrás).
  (3) Búsqueda de la tele: el usuario quiere VÍDEOS al escribir, no palabras → en la tele nunca sale el panel de
  sugerencias (tapaba y se llevaba el foco), abajo desde el campo va a los resultados y al pulsar buscar en el teclado
  el foco pasa a los vídeos (`focusResultsOnTv`). Las palabras de la fila del teclado son del propio teclado (Gboard),
  no de la app. Pendiente: vista pequeña B (rehacer `layout-large-land/fragment_video_detail.xml`: vídeo arriba a la
  izquierda, datos a la derecha, siguientes en fila abajo).
- 2026-11-01 (94): Tele: (1) al pasar el mando por una caja de buscar se abría el teclado solo → `videoprime/SearchField.kt`
  (Suscripciones, Historial, Descargas): en la tele la caja es de solo lectura hasta pulsar OK (entonces abre el teclado)
  y vuelve a serlo al salir; en el móvil igual que antes. (2) Suscripciones lenta al volver de un vídeo:
  `FeedDAO.getChannelWatchCounts` comparaba cada vídeo del historial con cada canal (OR + EXISTS) y se repetía con cada
  escritura del feed → ahora dos uniones con índice (por canal subidor y por feed) con UNION por (canal, vídeo,
  access_date); y `ChannelsFragment.cachedChannels` pinta al momento la última lista mientras relee. Sin probar en la
  tele.
- 2026-11-01 (95): Tele: desde la barra de progreso no se llegaba al botón de quitar la pantalla completa (la barra usa
  izquierda/derecha para avanzar y el botón está al final de esa fila) → `MainPlayerUi.initListeners`: abajo desde la
  barra va a `screenRotationButton` y abajo desde él a "A continuación" (Atrás también quita la pantalla completa).
  Bocetos de la página del canal en la tele (lienzo https://claude.ai/artifact/65TQ1avgVcoV4opruUuH8t, fila "Tele ·
  página del canal"): A rejilla de 4 por fila con cabecera, campana, Suscrito y pestañas en píldoras; B filas "Nuevo para
  ti" y "Más populares". En los dos, lo que suena queda en un mini reproductor abajo a la derecha (hoy al abrir un canal
  la miniatura y el texto del vídeo quedan medio cortados encima). Pendiente de que el usuario elija.
- 2026-11-01 (96): El usuario eligió el canal A. Primer paso: pestañas del canal en píldoras (`ChannelFragment.styleTabsAsPills`,
  `pulseplay_tab_pill`: la elegida con píldora violeta, desde la izquierda, sin barra violeta). La rejilla ya sale sola en
  pantallas anchas (modo de vista "auto"). Falta: cabecera grande con aro cian y el mini reproductor que tapa al abrir un
  canal (pedir foto). Comprobaciones del CI ("app") fallan desde dfb3b21 pero en local pasan `testDebugUnitTest
  lintContinuous`; el informe no se puede bajar (proxy) → relanzado el run 41330894955 para ver si es intermitente.
- 2026-11-01 (97): Coche: tras pulsar aleatorio y volver atrás, la lista salía en el orden viejo y hacía falta pulsar
  atrás dos veces. `MediaSessionPlayerUi.onShuffleModeEnabledChanged` ahora avisa también al navegador del coche
  (`notifyCarQueueChanged`, antes solo la cola de la sesión) y `PlayerService.notifyCarQueueChanged` repite el aviso a
  los 400 ms y a los 1,5 s (Android Auto puede pedir la lista mientras vuelve del reproductor y quedarse con esa
  respuesta). Sin probar en el coche.
- 2026-11-01 (98): La tarjeta con fondo del coche (entradas 73, 90 y 91) no llegó a salir nunca en el coche del usuario:
  QUITADA del todo a petición suya (`CarIdleCard.kt`, `pulseplay_car_background.png`, textos `pulseplay_car_idle_subtitle`/
  `pulseplay_car_recent`, la raíz "reciente" `ID_RECENT_ROOT`/`populateRecent`; `onGetRoot` vuelve a devolver null con
  `EXTRA_RECENT`). Se queda el icono del coche en PNG (`pulseplay_car_icon`, entrada 73).
- 2026-11-01 (99): Vídeo de la tele: desde la barra, a la derecha en el directo se buscaba hacia delante (el vídeo se quedaba
  cargando) en vez de llegar al botón de pantalla completa. Ahora `MainPlayerUi` (OnKeyListener de la barra): abajo, o
  derecha al final de la barra / en el borde del directo, lleva el foco a `screenRotationButton` sin buscar. Vista pequeña B
  hecha (`layout-large-land/fragment_video_detail.xml`, también tablet en horizontal): vídeo 16:9 a la izquierda (peso 11,
  alto = ancho de la columna × 9/16, `thumbnailColumnListener`), a la derecha los datos (`detail_content_root_layout` sacado
  del AppBar a `pulseplay_info_panel`, título hasta 3 líneas) con botón "Pantalla completa" (`pulseplay_fullscreen_button`,
  píldora oscura con aro blanco con foco) y abajo la fila "A continuación" (`videoprime/RelatedRowFragment.kt`, Compose:
  tarjetas 220 dp, "Reproducción automática: activada/desactivada" se toca para cambiarla; pulsación larga = menú).
  Panel y fila se ocultan en pantalla completa y en la ventana flotante (`updateInfoPanel`, `showRelatedRow`,
  `pipGoneRelated`). Falta del canal A: cabecera grande con aro cian. Sin probar en la tele.
- 2026-11-01 (100): "En la tele se peta al salir y entrar en vídeos y va algo lento": sin trazas (en la tele no se ven
  las notificaciones). `LastExitReport` informa ahora de ANR, cierre por fallo (Java o nativo), poca memoria, exceso de
  recursos y fallo al iniciar, con resumen del aparato (modelo, RAM total/libre, límite de la app, low-RAM); en la tele
  abre la pantalla de error al volver a la app (`showPending` desde `MainActivity.onResume`, o al momento si el informe
  llega después; se marca visto solo al mostrarse). Pedir al usuario foto de "Ver detalles técnicos". Canal A en pantallas
  ≥600 dp (`ChannelFragment.styleWideHeader`): sin banner, cabecera con degradado violeta, avatar de 96 dp con aro cian,
  nombre 28 sp y campana/Suscrito de 48 dp. Sin probar en la tele.
- 2026-11-01 (101): Auditoría del 01-11 (PDF, base a512359), valorada con el código. Aplicado: 2.1 `OfflineMusicWorker`
  rechaza un 200 tras trozos ya bajados (se pegaba el archivo entero detrás), comprueba el inicio de `Content-Range` y
  el tamaño real contra el límite antes de guardar; 2.2 `LastExitReport` lee la traza con tope (30k) y la salida también
  acotada; 2.3 `HomeFragment`: una sola lectura del feed activa (`homeLoad`, la nueva cancela la anterior; antes se
  acumulaban en el CompositeDisposable cada 2 s durante la actualización) y la respuesta de Twitch pinta con lo último
  leído (`lastStreams/lastSubs`); 3.1 `CheckAlarm`: si la comprobación de Twitch del pulso anterior sigue en marcha, no
  se lanza otra (`twitchRunning`; se mantiene la alarma, que da red en Doze, en vez de WorkManager); 3.2
  `InstantPush.syncInBackground` agrupa peticiones (una esperando como mucho; `syncQueued/forceQueued`; el progreso
  parcial ya se guardaba y un timeout ya cortaba el bucle); 3.3 `UpdateChecker`: un fallo reintenta a los 5 min (antes
  esperaba el intervalo entero); 3.4 `AutoRadio`: el id se lee antes de marcar la semilla; 4.1 `HomeLiveCache` precarga
  sin repetidos y como mucho 24 imágenes. Sin cambios: 4.2 PiP (el informe no pide reescribirlo; ya hay `PipLog`), 4.3
  latencia (solo afecta a Twitch, ya medida en la entrada 64), `PREF_LIVE_NOW` (limpieza de transición, se deja).
- 2026-11-01 (102): Fotos de la tele. El usuario prefiere la vista pequeña A (no la B): `layout-large-land/fragment_video_detail.xml`
  vuelve a la de antes (vídeo + datos a la izquierda, peso 62; lista a la derecha, peso 38) con el vídeo 16:9 de su
  columna (máx. 70 % del alto), título hasta 3 líneas, botón "Pantalla completa" bajo los datos y `RelatedRowFragment`
  como lista vertical (miniatura 168 dp, título 2 líneas, interruptor "Reproducción automática"). Pantalla completa: el
  botón pasa al PRINCIPIO de la fila de la barra (`player.xml`); abajo desde los botones grandes va a él, derecha a la
  barra; desde la barra, abajo o izquierda al principio vuelven al botón y derecha en el directo no hace nada. Canal A:
  texto de las tarjetas cortado por la izquierda (la rejilla calcula columnas con el ancho de toda la pantalla y la
  miniatura tenía ancho fijo mayor que la columna) → `list_stream_grid_item.xml` con miniatura al ancho de su columna
  (16:9, centerCrop) y título en negrita; cabecera con degradado más oscuro (#312E81→#4C1D95→fondo) y aros redondos con
  el mando en campana y Suscrito (`pulseplay_focus_ring_round/pill`). Mini reproductor moderno
  (`videoprime/MiniPlayerStyle.kt`): barra oscura, miniatura 16:9 redondeada, título en negrita y play/pausa en círculo
  violeta. Sin probar en la tele.
- 2026-11-01 (103): Fotos de la tele. Vista pequeña A: fuera el botón "Pantalla completa" de los datos y el título "A
  continuación" de la lista (se veía mal centrado; queda "Reproducción automática" + interruptor). La lista ya no
  recomienda vídeos vistos (`StreamHistoryDAO.getWatchedUrls`: los que están en el historial salen; si todos están vistos
  se deja la lista entera). Canal en la tele: la cabecera ocupaba un tercio y con el mando la rejilla bajaba por dentro sin
  recogerla → `ChannelFragment` (escuchador global de foco): al entrar en los vídeos se recoge el AppBar y al subir a las
  pestañas/cabecera vuelve; cabecera más baja (avatar 80 dp, nombre 26 sp). Tirones: "la mejor" calidad vuelve a tener tope
  en lo que muestra el panel, ahora con sus píxeles físicos (`ListHelper.panelSize`: `Display.Mode` y el mayor modo
  admitido; un 4K da 3840x2160 aunque los menús vayan a 1080p) y el selector de pistas con `setMaxVideoSize` cuadrado del
  lado largo (no excluye vídeos verticales): una tele 1080p ya no decodifica 4K. Extractor con red real: YouTube bien
  (vídeos 0,7-2 s, búsqueda, YT Music, canal, lista), audio de vídeos recientes 403 por la IP del entorno (lo de siempre);
  Twitch bien (VOD de ibai/illojuan/elxokas con 5-6 calidades); la vigilancia fallaba porque squeezie tiene los VOD solo
  para suscriptores → extractor `6c8eb857` prueba hasta 5 y salta esos.
- 2026-11-01 (104): Tele. (1) El mini reproductor no hacía nada al pulsar OK: el foco del mando caía en la raíz de la
  página (clicable por el apaño #5600, sin acción) o, si sonaba como audio, abría el reproductor de música. Ahora la raíz,
  con la hoja recogida, hace lo mismo que tocar el mini reproductor, y en la tele (tipo AUDIO) vuelve al vídeo
  (`openVideoPlayer`). (2) Sin segundo plano en la tele: al salir de la app se pausa (`MainPlayerUi.onFragmentStopped`),
  y cerrar la app o quitarla de recientes ya no pasa a audio (`VideoDetailFragment.onDestroy`, `PlayerService.onTaskRemoved`).
  (3) Calidad: si Android no da el tamaño físico del panel, no hay tope (`ListHelper.panelSize` → null; antes caía a los
  píxeles de los menús).
- 2026-11-01 (105): Primer informe de `LastExitReport` (móvil Xiaomi 2311DRK48G, Android 16, 7,3 GB): "App closed: low
  memory" = Android cerró la app para liberar memoria (lmkd), no un fallo. Ahora un cierre por memoria solo se avisa si la
  app estaba en uso, visible o sonando (`importance` ≤ PERCEPTIBLE; si estaba guardada en segundo plano es lo normal) y el
  informe incluye el estado y la memoria que usaba (PSS/RSS) para saber si la app gastaba demasiado.
- 2026-11-01/02 (106): Noche: revisión completa (3 agentes: Kotlin `videoprime/`, Java del reproductor/página, BD +
  trabajos + servidor) y fluidez en la tele. Arreglado: `MainActivity.onResume` llamaba a `isInPictureInPictureMode()`
  (API 24) sin comprobar versión → se cerraba al abrir en Android 6; `PipLog.share` sin try (tele sin nada con qué
  compartir); `LibraryFragment` leía el índice sin conexión en el hilo principal (bloqueo si el trabajo de descarga lo
  tenía) y usaba `requireContext()` en un hilo de fondo; `DownloadsFragment` desvinculaba el servicio aunque no se hubiera
  vinculado; `StreamPrefetch` acumulaba trabajos terminados; `HomeFragment.startService` sin try (Android 12+ desde fondo);
  `MusicPlayerActivity/QueueActivity` reintentaban enganchar el reproductor tras cerrarse; `Player.freshAddressRetries`
  sin tope; `MainPlayerUi.updateCenterOffset` cast sin comprobar; `VideoDetailFragment` (de NewPipe) usaba `binding` tras
  destruir la vista en un `postDelayed`; `ChannelFragment` dejaba el escuchador de foco en el observador equivocado (ahora el
  de la ventana); push de YouTube espera los detalles 6 s (antes 12, dentro del margen de FCM). Pendiente (no urgente):
  `QueueStore` y `OfflineMusic` aún se leen en el hilo principal en algunas pantallas.
  Fluidez en la tele: `videoprime/FrameRateMatcher.kt` (como el "auto frame rate" de Kodi/SmartTube): la tele cambia a la
  frecuencia del vídeo (50 Hz para 25/50 fps, el contenido europeo; 60 Hz a 25 fps da saltos) con un modo de la misma
  resolución, solo en la tele; vuelve a la normal al cerrar el reproductor o salir de la app. Ajuste "Ajustar la tele al
  vídeo" (Vídeo y audio, activado). La cuenta atrás del final lee ya el siguiente vídeo (`PlayerEndCountdown`: arranca de
  la caché).
- 2026-11-02 (107): Tele: el "tunneling" (el hardware de la tele sincroniza imagen y sonido; lo que hace fluido el vídeo
  en los chips de tele) estaba apagado en todos los aparatos desde la entrada 82 (cortaba el sonido al pasar a segundo plano
  en el móvil). En la tele no hay segundo plano: `MainActivity.noTunnelingOnce` lo enciende una vez en la tele
  (`pulseplay_tv_tunneling_done`) salvo en aparatos de la lista negra de NewPipe (`DeviceUtils.shouldSupportMediaTunneling`).
  Se puede apagar en Ajustes → ExoPlayer.
- 2026-11-02 (108): Tercera revisión (BD, trabajos, servidor; ningún cierre). Arreglado: contadores de vídeos nuevos de
  Suscripciones congelados con la pantalla abierta (`FeedDAO.getRecentUnwatched` usaba un `now` fijo; ahora lo calcula
  SQLite en cada ejecución); Biblioteca releía todas las canciones de cada lista en cada escritura del feed
  (`PlaylistStreamDAO.getCoverUrls` con LIMIT 4 + `throttleLatest` 1 s); `onTimeout` en `DownloadManagerService` y
  `FeedLoadService` (Android 15: 6 h/día para dataSync, si no se paran el sistema cierra la app); servidor: un aviso de
  Twitch que fallaba al enviarse se perdía (ahora se reintenta cada 30 s hasta 15 min) y la suscripción de EventSub de un
  canal olvidado se borraba de la BD antes que de Twitch (ahora solo tras 2xx/404); sincronización: un aparato sin datos
  propios se quedaba "uniéndose" para siempre (ahora se escriben los archivos de estado tras el primer pull) y los ids de
  las listas se escriben solo después de que la transacción se guarde.
- 2026-11-02 (109): Fluidez de las pantallas Compose (auditoría de rendimiento por agente; lo de más impacto):
  `FocusRing` como `Modifier.Node` (FocusEvent + Draw; antes `composed`: una composición y un estado por tarjeta en todas
  las listas, y las listas no podían saltarse tarjetas sin cambios; ahora un cambio de foco solo redibuja);
  `HomeItem`/`HomeState`/`ChannelsState` `@Immutable`, `contentType` en las listas del Inicio y filas de la rejilla
  calculadas una vez por lista (`remember`); barra del reproductor de música: la posición se lee solo al dibujar y el
  tiempo con `derivedStateOf` por segundos (antes recomponía la barra y los textos 60 veces por segundo); Suscripciones:
  listas YouTube/Twitch/directo calculadas una vez por estado (`lazy`; antes ~10 filtrados por redibujado cada 400 ms);
  ecualizador de la cola dibujado en Canvas y parado en pausa; Historial ordena y agrupa una vez (no en cada letra) y
  Descargas filtra solo si cambia lo listado (no con el sondeo de 1 s). Pendiente de la auditoría: `SmoothImage` (dos
  imágenes por tarjeta mientras carga), elementos de `RelatedRowFragment` como modelo propio, arrastre de la cola.
- 2026-11-02 (110): `QueueStore.load` y `OfflineMusic.load` guardan en memoria lo último leído/escrito y solo vuelven
  a leer el JSON si el archivo cambió (fecha + tamaño): varias pantallas leían las colas (hasta 1000 canciones) en el hilo
  principal y el índice sin conexión se lee antes de cada canción.
- 2026-11-02 (111): Fotos de canal lentas en Suscripciones (se pedían a la red al aparecer, con fundido). Ahora
  `videoprime/AvatarPrefetch.kt`: el Inicio, al leer las suscripciones, precarga en segundo plano las fotos (80 como mucho,
  tamaño 360 = el mismo escalón y clave de memoria de `BestThumbnail` que una tile de 64-68 dp) → Suscripciones las saca
  de memoria; un fallo se reintenta en la siguiente lectura. Las fotos de canal entran sin fundido
  (`SmoothImage(crossfade = false)`).
- 2026-11-02 (112): Tele: seguía sonando al salir del vídeo/directo (con la entrada 104 solo se pausaba con
  `MainPlayerUi`, no si sonaba como audio, y algunas teles tienen ventana flotante: el vídeo seguía en una esquina).
  Ahora en la tele: Atrás desde la página del vídeo (tras salir de pantalla completa) cierra el reproductor
  (`VideoDetailFragment.closePlayer`: hoja oculta + `stopService` al momento); salir de la app (`onUserLeaveHint`) o
  dejar de verse (`onStop`, salvo giro o pantallas propias) lo cierra (`MainActivity.stopPlayerOnTv`); y nunca ventana
  flotante (`VideoPip.isSupported` = false en la tele). El móvil sigue igual.
- 2026-11-05 (113): (1) Play/pausa animado: `videoprime/PlayPauseDrawable.kt` (el triángulo se parte en las dos barras y
  vuelve, 260 ms, dibujado a mano: sin librerías) en el reproductor (`VideoPlayerUi.updatePlayPauseButton`, móvil y tele),
  mini reproductor (`VideoDetailFragment.setOverlayPlayPauseImage`) y `PlayPauseIcon` Compose (reproductor de música y
  cola). (2) "A continuación" sin vídeos vistos: `videoprime/WatchedUrls.kt` (una consulta por vídeo de ≤40 direcciones con
  `StreamHistoryDAO.getWatchedUrls(serviceId, urls)`, que ahora usa el índice (service_id, url), en segundo plano y guardada
  en memoria: 30 vídeos, se relee a los 10 min; lo que se ve se añade con `markWatched`). Lo usan la fila de pantalla
  completa (`PlayerUpNext`, la lee `MainPlayerUi.onMetadataChanged`), la cuenta atrás del final, la lista de la tele
  (`RelatedRowFragment`) y la pestaña "Siguiente" del móvil (`RelatedItemsFragment.loadResult`, en el hilo de fondo). Si
  todo está visto se deja la lista entera. (3) Pantalla completa en tele/tablet: el SurfaceView sigue a su nuevo tamaño unos
  fotogramas tarde (marco negro que crece y luego la imagen; al reducir, imagen grande un momento). Igual que TextureView
  arreglaría esto pero rompe el tunneling de la tele, se tapa: `fullscreenCurtain` negro en `player.xml`, se pone al
  cambiar (`MainPlayerUi.coverWhileResizing`) y se funde (220 ms) cuando el vídeo deja de cambiar de tamaño (140 ms sin
  cambios, entre 280 y 900 ms). Al salir de pantalla completa la altura usa el 62 % del ancho mientras la columna no tiene
  su ancho real (`setHeightThumbnail`). (4) Inicio al volver de un vídeo: la página del vídeo es una hoja encima, el Inicio
  no recibía `onResume`. `videoprime/PlayerSheetEvents.kt`: al bajar/cerrar la página (solo si estuvo abierta) el Inicio
  relee la BD y, si la última comprobación tiene más de 10 min, comprueba las suscripciones en silencio (RSS);
  `HomeFragment.catchUp` también en `onResume`. Sin probar en aparatos.
- 2026-11-05 (114): Revisión del extractor. Upstream dev traía 32 commits: parámetro n de YouTube construido con la URL
  correcta (`YoutubeThrottlingParameterUtils`, e8f89145), chat de directos de YouTube (`YoutubeLiveChatInfoItemExtractor`,
  `StreamExtractor.hasLiveChat`; la app aún no lo usa), tamaño de los avatares leído de `=s` y gson/okhttp/gradle. Integrados
  en `f59965f7`; el único conflicto (`YoutubeStreamExtractor.onFetchPage`) se resolvió manteniendo `next` en paralelo y
  añadiendo `findLiveChatContinuation`. MOCK: 2658 tests, los 12 fallos conocidos y ninguno nuevo. Smoke real: 6 vídeos OK
  (1,3-1,7 s), búsqueda, YT Music, canal, lista y Twitch OK; audio de vídeos recientes 403 por la IP del entorno (como
  siempre). Mejora grande pendiente de decidir: arrancar la reproducción sin esperar a `next`/WEB (entrada 74).
- 2026-11-05 (115): Recomendados que "no tienen en cuenta lo visto": el filtro de la entrada 113 dejaba la lista entera si
  todos estaban vistos (frecuente con música). Ahora `WatchedUrls.leaveOut/leaveOutWatched` ORDENAN: primero lo no visto
  (en el orden de YouTube) y después lo visto, nunca se oculta; en la tele (`RelatedRowFragment`) lo visto lleva "Visto"
  (`pulseplay_watched`), en el móvil la barra de progreso de siempre. Sonido raro en un vídeo nuevo: comprobado con red
  real que los vídeos recientes traen solo pistas originales (139/140/249/250/251, sin doblaje automático ni DRC) y que
  "Igualar el volumen" solo baja el volumen; causa sin encontrar, pendiente de que el usuario diga qué oyó. Arrancar sin
  esperar a `next`/WEB: descartado por ahora (toca el centro de `YoutubeStreamExtractor`, donde upstream cambia a menudo y
  hoy ya hubo conflicto, y la app da por hecho un StreamInfo completo en muchos sitios).
- 2026-11-05 (116): Sonido "distorsionado o metálico" en un vídeo nuevo en segundo plano y en el coche. Descartado: pistas
  (originales, sin doblaje ni DRC), "Igualar el volumen" (solo baja) y calidad (sin límite con datos: AAC 128/Opus 160).
  Causa probable: directo o directo recién acabado de YouTube; ExoPlayer cambia la velocidad 0,97-1,03× para seguir el
  directo y estirar música suena metálico. Ahora YouTube en directo va siempre a 1,0× (`PlaybackResolver.buildLiveMediaSource`,
  min = max = 1); tras un corte ya no se pone al día solo (botón "Ir al directo"). Twitch sigue con 1,02× máx. Sin confirmar:
  si vuelve a pasar, pedir el enlace del vídeo y mirar Ajustes → velocidad/tono.
  Después el usuario aclaró: era un vídeo normal (no directo) en segundo plano. Medido con red real: los vídeos recién
  subidos (≈1-2 h) traen el audio solo como OTF (DASH por trozos, 139 HE-AAC 48 kbps y 140 AAC 128 kbps; la app elige
  140 y lo reproduce con `YoutubeOtfDashManifestCreator`); al terminar el procesado pasan a progresivo normal. Sospecha
  principal sin confirmar (los trozos OTF dan 403 desde la IP del entorno): la reproducción OTF. Para confirmarlo: si un
  vídeo nuevo suena mal y unas horas después suena bien, es eso.
- 2026-11-05 (117): Informe del usuario: `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` con `UnknownHostException` de
  `rr2---sn-…googlevideo.com` (el móvil se quedó sin red/DNS un momento; no es un fallo de la app). Antes: recarga al
  momento (aún sin red, podía saltar canciones) + notificación de error. Ahora `Player.waitForNetwork` (errores de
  conexión o UnknownHost/Socket en la causa): sin informe, guarda la posición y espera; sin red mira cada 2 s mientras
  haga falta; con red reintenta 4 veces (1,5/3/6/10 s) y si sigue fallando vuelve al tratamiento de siempre (un stream
  roto no bloquea la cola). Se cancela al sonar (`onPlaying`), al empezar otra cola (`initPlayback`) y en `destroy`; el
  reintento solo recarga si sigue la misma cola (`networkRetryQueue`). Sin probar en el móvil.
- 2026-11-05 (118): Miniaturas de canciones y fotos de canal lentas. (1) Con calidad de imagen "alta" la BD guarda
  avatares/portadas de 900-1200 px y `BestThumbnail.of` solo pedía versiones MÁS grandes: Suscripciones bajaba ~80
  fotos de 900 px para dibujarlas a 64 dp. Ahora, si se sabe el tamaño dibujado, también las pide más pequeñas (a su
  escalón 360/720/1200; tamaño desconocido = la grande, como antes). (2) Canciones: `hq720.webp` no existe en muchas y
  YouTube tarda 1-1,6 s en dar 404. En listas (dibujado 1-900 px) el interceptor espera como mucho 450 ms
  (`SLOW_WAIT_MILLIS`); si no llega, muestra la miniatura original y pide la grande aparte (`fetchLater`, una vez por
  dirección, con tamaño) para la caché o para apuntarla como inexistente. Reproductor/notificación (sin tamaño o
  >900 px) siguen esperando a la grande. Test `BestThumbnailTest` actualizado. Sin probar en el móvil (el proxy del
  entorno no llega a googleusercontent/ggpht).
- 2026-11-05 (119): Pantalla completa ↔ pequeño (tele y móvil girado): seguía "primero el marco negro y luego la imagen"
  (la cortina de la entrada 113 solo lo tapaba). Causa: cambiar el tamaño de un SurfaceView hace que Android rehaga su
  imagen unos fotogramas después de que la página se mueva; escalarlo va en el mismo fotograma (como en la ventana
  flotante, entrada 69/72). Ahora `ExpandableSurfaceView` (Android 7+, dentro de una pantalla de la app) se mide siempre
  al tamaño de referencia = forma del vídeo encajada en la pantalla actual (`referenceSize`, solo cambia al girar) y el
  sitio que ocupa (página, pantalla completa, vista pequeña de la tele, ZOOM/FILL) se aplica como `scaleX/scaleY` (centrado
  en su RelativeLayout aunque sea más grande que él). PiP: `pipWidth` = ese ancho y la fase "ventana aún grande" usa la
  escala de la página. Quitada la cortina (`fullscreenCurtain`, `coverWhileResizing`) y `surfaceForeground` ya no se
  alinea a la superficie. Sin probar en aparatos: vigilar que el vídeo no se salga de su sitio (Xiaomi, Android 7-9) y que
  la ventana flotante siga sin negro.
- 2026-11-05 (120): Fotos del usuario, punto por punto. (1) Parones en la ventana flotante (directo de Twitch): a 1080p60
  en una ventana diminuta y con Android dando menos red/CPU a la app en la ventana. `Player.setPictureInPictureQuality`
  (desde `VideoDetailFragment.onPictureInPictureChanged`) bajaba los directos a 720p en la ventana: QUITADO a petición
  del usuario (no quiere bajar la resolución en la ventana flotante; buscar otra causa de los parones).
  (2) Tele: con la entrada 119 al minimizar/ampliar el vídeo se quedaba en negro minutos (con tunneling la imagen va al
  hardware de la tele, que no escala la capa): `ExpandableSurfaceView.referenceSize` = null en la tele (como antes);
  el escalado se queda en móvil/tablet. (3) Marco blanco alrededor de toda la pantalla en la tele: lo dibujaba el
  `FocusOverlayView` de NewPipe sobre la vista Compose entera (para Android el foco es la pantalla); ahora no dibuja si
  el padre del foco es un `AbstractComposeView` (Compose pone sus aros) y en las listas clásicas el aro es redondeado
  (12 dp, 3 dp de grosor), aparece con un fundido y se desliza (140 ms) al cambiar de elemento; en scroll sigue al
  elemento sin retraso. (4) Fotos de canal lentas en la tele al bajar: la memoria de imágenes de la tele es pequeña y las
  portadas del Inicio echaban las fotos; `AvatarPrefetch` usa un conjunto "en curso" (no "una vez por ejecución") y
  `ChannelsFragment` las precarga cada vez que se abre Suscripciones. Sin probar en aparatos.
- 2026-11-06 (121): Cortes de audio un instante en segundo plano al abrir otra app y en el coche (luego sigue). En
  segundo plano la app no toca el reproductor al irse (BackgroundPlayerUi; sin recargas ni cambios de pista); el foco de
  audio lo lleva ExoPlayer como las apps grandes (bajar volumen/pausa breve si otra app pide sonido: esperado). Causa
  probable: búfer de salida de audio corto (ExoPlayer 0,25-0,75 s) que se vacía cuando el móvil va cargado un momento.
  `player/helper/BufferedRenderersFactory` (DefaultAudioSink con `DefaultAudioTrackBufferSizeProvider` 1-2 s, factor 8;
  `CustomRenderersFactory` la hereda): pausa, saltos y volumen siguen al momento; un cambio de velocidad o "saltar
  silencios" tarda hasta ~1-2 s en oírse. Sin probar en el móvil.
- 2026-11-07 (122): "No me ha llegado la notificación de Twitch de Rubius". Comprobado: Rubius empezó 18:22:46 UTC;
  EventSub llegó al servidor 18:23:04 y el push a `tw_rubius` salió 18:23:05 (`/status`: `lastTwitchEvent`/
  `lastTwitchPush` rubius). Su directo anterior acabó ~03:00 (no era el "mismo directo" de <30 min). El fallo está en el
  móvil y no se podía saber cuál: la app no apuntaba el resultado de un push de Twitch. Ahora `LiveNotifications.onPush`
  devuelve el resultado (mostrado / ya avisado / sin campana / bloqueado / error) e `InstantPush` lo guarda como el de
  YouTube ("Estado de los avisos": "(Rubius: …)"); `RESULT_OFF` si los avisos de vídeos/directos están apagados en la app.
  `LiveNotifications.canShow/popupsOff`: el estado avisa si el canal "Directos" (`videoprime_live`) está bloqueado o no
  salta en pantalla (antes solo se miraba el de vídeos nuevos). Sospechas: campana de Rubius apagada, canal "Directos"
  bloqueado/silenciado o Xiaomi parando la app (tampoco salió por la comprobación de cada 5/10 min). Sin probar en el móvil.
- 2026-11-08 (123): Revisión de lo instantáneo de los avisos. Medido: Twitch kolderiu empezó 21:57:52 UTC, EventSub llegó
  21:58:11,9 (≈20 s, es lo que tarda Twitch y no se puede bajar) y el FCM salió 0,3 s después; YouTube ≈1 min (hub de
  YouTube). Arreglos: (1) servidor: los avisos de sincronización (`sync_<grupo>`, uno por cada cambio) iban con prioridad
  ALTA sin mostrar nada, y FCM rebaja a prioridad normal (con retraso en reposo) las apps que hacen eso → ahora NORMAL
  (`fcmMessage(..., priority)`); probable causa de avisos tardíos o perdidos. `last_push_info` apunta también cuándo se
  envió ("sent …") para medir YouTube. (2) App: el push de Twitch avisaba DESPUÉS de preguntar a Twitch y precargar
  hasta 24 imágenes (`check()`), y además `check()` y `onPush` compartían candado (un push durante una comprobación
  esperaba a que acabara): ahora avisa al momento con lo del push, el candado (`seenLock`) solo cubre la memoria de
  avisados y `InstantPush` lanza la comprobación después. (3) La foto del canal de un aviso espera 3 s como mucho
  (`CoilHelper.loadBitmapWithin`; antes sin límite, con red lenta retrasaba el aviso; sin foto sale solo el icono pequeño).
- 2026-11-09 (124): "Va bastante lento en la tele" (tele con Android integrado; lentos los menús, abrir vídeo y abrir la
  app). Sin poder perfilar en la tele: se quita trabajo que competía con la pantalla en su procesador flojo. En la tele
  (`DeviceUtils.isTv`): Coil descodifica 2 imágenes a la vez como mucho (`decoderCoroutineContext`); al abrir, la
  sincronización de avisos, la comprobación de versión, `LastExitReport` y `SyncManager.syncNow` esperan 10 s
  (`App.TV_STARTUP_DELAY_SECONDS`), la primera actualización del Inicio 6 s (`tvRefreshPending`; si se sale antes, se
  hace al volver) y la lectura anticipada de 3 vídeos 8 s (`StreamPrefetch.TV_DELAY_MILLIS`); durante la actualización
  el Inicio relee cada 6 s (antes 2); el feed extrae 3 canales a la vez (antes 6) en hilos de prioridad BACKGROUND (en
  el móvil LESS_FAVORABLE); sin pulso `CheckAlarm` ni `LiveCheckWorker` (las notificaciones casi no se ven en la tele;
  el Inicio pregunta a Twitch al abrir); sin pestaña de comentarios en la página del vídeo (se bajaban y maquetaban con
  cada vídeo detrás de la pantalla completa). En todos: el Inicio pedía las 80 fotos de canal en cada relectura (cada
  2 s mientras actualizaba; en la tele, con poca memoria de imágenes, las volvía a descodificar): ahora como mucho cada
  10 min (`AvatarPrefetch.warmFromHome`; Suscripciones las sigue pidiendo al abrirse). Sin probar en la tele.
- 2026-11-09 (125): Análisis completo pedido por el usuario y primer bloque (fluidez, optimización y funciones 2, 5, 6
  y 7; NO quiere SponsorBlock, chat, letras ni Spotify por ahora). Bocetos de diseño pendientes de que elija:
  https://claude.ai/artifact/VyRHXcgQ4ag9VVC1eHYFEf (1 Ajustes móvil+tele, 2 comentarios, 3 descripción, 4 lista de
  YouTube móvil+tele, 5 tipografía Plus Jakarta Sans, 6 arranque y movimiento).
  Fluidez: Inicio construye sus tarjetas fuera del hilo de la pantalla (`HomeFragment.build` → `Built`, en el hilo de
  la lectura; la respuesta de Twitch reconstruye en `computation`) y guarda lo último pintado (`cached`, los directos
  solo 5 min) para salir al instante al volver de un canal o una búsqueda; `SmoothImage` = un solo `AsyncImage` con la
  imagen anterior debajo (una recomposición por tarjeta al cargar, antes tres, y una sola imagen en reposo);
  Descargas sondea cada 4 s si no hay nada bajando; tele: `StreamPrefetch.onFocused` lee el vídeo de YouTube sobre el
  que descansa el foco del mando 0,7 s (uno a la vez, nunca con algo sonando) → OK lo abre de la caché.
  Optimización: `localeFilters` en/es (el resto de traducciones eran 5,5 MB de `resources.arsc`; el código fuente
  no cambia, los merges de upstream siguen igual); pulso `CheckAlarm` cada 20 min (no 10) si los avisos instantáneos
  están activos y llegó un push en las últimas 24 h. Revisado y SIN cambiar (no daba ganancia o era arriesgado):
  migraciones de `MainActivity` (15 lecturas de memoria), memoria de Coil (los valores por defecto son correctos),
  reglas R8 de ExoPlayer, división por ABI (las .so pesan 60 KB), Media3 (migración grande; bloque propio), arrancar
  sin esperar a `next`. En el análisis conté mal los "hilos sueltos": las pantallas ya usan Rx.
  Funciones: "Seguir viendo" en Inicio (`StreamHistoryDAO.getContinueWatching`: vídeos ≥5 min con ≥30 s vistos y más
  de 1 min y un 5 % por ver, 12 últimos; tarjeta con barra y "Quedan m:ss"); Twitch más cerca del directo
  (`videoprime/TwitchLowLatency.kt`: las líneas `#EXT-X-TWITCH-PREFETCH` pasan a ser trozos normales con un
  `HlsPlaylistParserFactory` propio; objetivo 9 s/mín. 7 s respecto al final de la lista, que ahora está ~4 s más
  cerca → ~3 s menos de retraso con el mismo colchón; ajuste "Twitch más cerca del directo", activado; comprobado
  con red real que las listas traen 2 prefetch y que se descargan); Favoritas (`videoprime/Favorites.kt`: corazón en
  el reproductor de música → lista local "Favoritas", creada al primer uso y encontrada por id o por nombre, se
  sincroniza como cualquier lista); widget (`videoprime/PlayerWidget.kt`, `pulseplay_widget.xml`: carátula, título,
  anterior/play/siguiente; lo redibuja `NotificationPlayerUi`; sin nada sonando abre la app); copia semanal
  (`videoprime/AutoBackup.kt`: al abrir la app si pasaron 7 días, mismo zip que Exportar, en la carpeta de descargas
  SAF o `files/backups`, se guardan 4; interruptor en Copia de seguridad). Sin probar en aparatos.
  Perfil de arranque real: `baseline-prof.txt` lleva además 15.000 líneas grabadas en el emulador con la APK release
  (abrir, las 4 secciones, un directo de Twitch sonando): `cmd package compile --reset`, usar la app, `killall -s
  SIGUSR1`, `cmd package snapshot-profile <pkg>` y `profman --dump-classes-and-methods --profile-file=/data/misc/
  profman/<pkg>.prof --apk=<base.apk> --dex-location=base.apk` (se quitan las líneas con `$$`/`-IA`, que cambian en
  cada compilación; las que no encajan se ignoran). `baseline.prof` 10,6 → 15,9 KB. APK 13,25 → 8,5 MB. Probado en el
  emulador con la APK release: abre, secciones bien y el directo de Twitch suena con el parser nuevo. OJO emulador:
  compilar con Gradle a la vez lo deja sin CPU y system_server se reinicia en bucle (esperar a que acabe Gradle).
- 2026-11-09 (126): Diseño de las pantallas antiguas (el usuario aprobó los bocetos de la entrada 125 y pidió rehacer
  los de la tele, que veía compactos: ahora usan todo el ancho; mismo lienzo). Hecho: (5) tipografía Plus Jakarta Sans
  (OFL; `res/font/plus_jakarta_sans*.ttf`, en el tema `Base.V21` y `PulsePlayTypography` de Compose; en Android 6-7.1
  las pantallas clásicas se quedan con la del sistema). (1) Ajustes: `videoprime/SettingsStyle.kt` (decoración que
  dibuja cada grupo de opciones como tarjeta redondeada, desde `BasePreferenceFragment`), pantalla principal agrupada
  (Reproducción / Avisos y aparatos / Tus datos / La aplicación) con `pulseplay_settings_entry.xml` (icono en círculo y
  flecha); en la tele `layout-w840dp-television/settings_layout.xml`: secciones a la izquierda, opciones en el centro y
  a la derecha el nombre y la explicación de la opción con el foco (`videoprime/SettingsWide.kt`; solo tele porque no
  gira: un layout con dos contenedores de fragmentos no sobrevive a un giro). (2) Comentarios: `list_comment_item.xml`
  rehecho (mismos ids; OJO `CommentInfoItemHolder` casteaba a RelativeLayout: lo encontró la revisión). (3) Descripción:
  `videoprime/DescriptionHeader.kt` (ComposeView con visitas / me gusta / fecha y capítulos con su minuto; tocar uno
  salta si ese vídeo suena). (4) Lista de YouTube: portada grande redondeada, botón "Aleatorio"
  (`MusicPlayback.playShuffled`: empieza en una canción al azar y activa el aleatorio del reproductor) y
  `layout-w840dp/playlist_header.xml` (portada a la izquierda y datos al lado en tele/tablet). (6) Arranque en
  Android 12+ (`values-v31` `OpeningTheme` + `drawable-v31/pulseplay_splash_animated.xml`: logo con la onda moviéndose);
  las tarjetas del Inicio se recolocan animadas (`animateItem`, no en la tele); vibración corta en play, corazón y al
  coger una canción de la cola. NO hecho (riesgo): gesto de atrás con vista previa (la app usa `onBackPressed` en
  muchas pantallas y con `enableOnBackInvokedCallback` dejan de llamarse en Android 13+) y la miniatura que crece hasta
  el reproductor (la página del vídeo es una hoja con el reproductor del servicio). Sin probar en aparatos.
  Visto en el emulador (Android 11, móvil, APK release): Ajustes y sus subpantallas en tarjetas, lista de YouTube con
  portada y Aleatorio, comentarios y descripción con las cifras, sin cierres. Sin ver: la tele (tres zonas de
  Ajustes, cabecera horizontal de listas), el arranque animado (pide Android 12+) y los capítulos.
- 2026-11-10 (127): Vigilancia #28 fallida (foto del usuario): falsa alarma. 5 de 6 vídeos recientes bloqueados por la
  IP de GitHub y el audio del único que cargó dio 403 (el mismo bloqueo de centro de datos de siempre); búsqueda, YT
  Music, canal, lista, 2 vídeos fijos y Twitch bien. Extractor `a0d67881`: `youtubeRecentVideosDownload` se omite
  (no falla) si no bajó ningún audio y la mayoría de los vídeos estaban bloqueados por IP.
- 2026-11-10 (128): "Seguir viendo" del Inicio QUITADO a petición del usuario (no le hace falta): fuera la fila,
  `ContinueCard`, `StreamHistoryDAO.getContinueWatching` y su texto. El resto de la entrada 125 se queda.
- 2026-11-11 (129): Informe del móvil "App closed: low memory (elevated_mem_pressure)" sonando en segundo plano con RSS
  128 MB y 2,8 GB libres: lo cerró Xiaomi por presión de memoria del sistema, no la app. `LastExitReport.worthTelling`
  ya no avisa de cierres por memoria si la app usaba menos de 400 MB (`OWN_FAULT_RSS_MB`). Tele: "no me deja cargar
  la última actualización" sin más datos (versionCode fijo 1015 en todas, se instala encima; la nightly se reemplazó
  dos veces seguidas el 10-11, una descarga a medias pudo fallar): pedida foto del mensaje.
- 2026-11-11 (130): Auditoría pedida por el usuario. Estado: vigilancia OK (Twitch entero, YouTube búsqueda/YT Music/
  canal/lista; vídeos recientes bloqueados por la IP de GitHub, se omite), issue #5 cerrado solo; servidor de avisos
  34/34 YouTube y 7/7 Twitch, push de YouTube 43 s tras publicar, Twitch 0,5 s; sincronización con 2 aparatos; CI en
  verde. Upstream: extractor sin arreglos de YouTube (Bandcamp/SoundCloud, jsoup 1.24.1, release 0.27.1); app con
  Room v3 en camino (merge grande futuro) y pantallas de ajustes en Compose. Arreglado: (1) TV "no me deja cargar la
  actualización": la dirección guardada era la APK con commit, que se BORRA al publicarse otra nightly → 404;
  `UpdateActivity` ahora ante un 404 baja `PulsePlay.apk` de la misma release (siempre la última). (2) Batería:
  `LiveCheckWorker` cada 15 min (no 5) si los avisos instantáneos funcionan (`CheckAlarm.pushProven`). (3) Traído de
  upstream 73717d44: en pantalla completa los controles quedan fuera de la zona de gestos del sistema. (4) Workflow APK
  fijado a `ubuntu-24.04` (ubuntu-latest migra a Ubuntu 26 y la prueba de arranque usa emulador con KVM).
- 2026-11-11 (131): Estudio de apps abiertas (LibreTube, PipePipe, Tubular, Metrolist, SmartTube; solo funciones y
  flujos; REA sigue descartada). Lista priorizada en `docs/videoprime/ideas-apps-abiertas.md` (R rendimiento, V vídeo,
  M música, F feed, T tele). Comprobado en el código: búfer hacia atrás 0 y caché del reproductor 64 MB (NewPipe).
  Pendiente de que el usuario elija.
- 2026-11-11 (132): Informe del usuario: "Nada que perder" (QjBdXRfnDU4, canal "Pignoise - Topic") daba UNPLAYABLE "Este vídeo
  no está disponible" con VISIONOS. Comprobado con red real: la pista del álbum falla con TODOS los clientes (VISIONOS,
  ANDROID_VR, IOS, WEB_REMIX, TVHTML5, embebido) pero el videoclip oficial y otras subidas de la misma canción cargan
  (bloqueo de la discográfica a apps que no son de YouTube). `videoprime/AlternateVersion.kt`: si `getStreamInfo` de
  YouTube falla con `ContentNotAvailableException` exacta (no edad/pago/privado) o geográfica y el canal (oEmbed, que
  responde aunque esté bloqueado) acaba en " - Topic", busca "artista título" en vídeos de YouTube, puntúa (título y
  artista presentes, sin karaoke/cover/directo/remix…, ≤2 palabras ajenas como "feat. X", 1-12 min), prueba hasta 3 y
  guarda la que carga en `InfoCache` con la URL original; sin alternativa, no se vuelve a buscar en 10 min. Para esta
  canción elegiría "Pignoise - Nada que perder (video clip)" (Warner Music Spain).
- 2026-11-11 (133): Tele confirmada por el usuario: funciona y va más rápida; quedaban miniaturas que tardaban. (1) Caché de
  disco de Coil explícita (`App.newImageLoader`): 5 % del espacio libre, mín. 64 MB, máx. 400 MB (por defecto 2 %, desde
  10 MB: en una tele con poco espacio las portadas se tiraban y se volvían a bajar), en la misma carpeta que usaba Coil 3
  (`cacheDir/coil3_disk_cache`, no `image_cache`, que era la de Coil 2). (2) `videoprime/CoverPrefetch.kt`: 1,5 s después de
  pintar el Inicio, baja al disco las portadas de las tarjetas 7-30 de 4 en 4 (tamaño mínimo y sin memoria: no echa las
  portadas de la pantalla ni quita los 2 huecos de descodificación de la tele); al bajar, la tarjeta solo descodifica.
- 2026-11-11/12 (134): Noche (el usuario pidió quitar el aviso "Cargando contenidos…" y mejoras a criterio). (1) Inicio:
  `FeedLoadService.EXTRA_QUIET` (lo manda `HomeFragment`): servicio normal sin `startForeground` ni notificación (la app
  está delante); si el usuario sale, Android lo para al rato, `onDestroy` publica `IdleEvent` y la carga sigue otra vez
  (lo ya leído queda guardado). `FeedFragment` (grupos de NewPipe) sigue con notificación. (2) De
  `docs/videoprime/ideas-apps-abiertas.md`: R1 búfer hacia atrás 60 s desde fotograma clave (`LoadController(backBufferMs)`;
  0 en tele o poca RAM); R2 caché del reproductor hasta 256 MB, máx. 10 % del espacio libre (`PlayerDataSource`); R4
  vigilante de parones (`Player.checkStall`: cargando, cada 5 s; si en 30 s no llega ni se reproduce nada, con red, sin
  pausa y no en directo → `setRecovery` + `reloadPlayQueueManager`, una vez cada 2 min); V1 mantener pulsado el vídeo =
  2× hasta soltar (`Player.holdFastSpeed/releaseFastSpeed`, no se guarda en ajustes; `MainPlayerGestureListener.onLongPress`,
  rótulo "2× ▶▶" con etiqueta `VideoPlayerUi.FAST_SPEED_LABEL_TAG`, se suelta también al destruir la UI o cambiar de cola).
  Sin probar en aparatos.
- 2026-11-12 (135): R5 de las ideas, con hallazgo: YouTube da cada resolución en H.264 (`avc1`, MPEG_4), VP9 (WEBM) y AV1
  (`av01`, TAMBIÉN MPEG_4). `ListHelper.getSortedStreamVideosList` elegía por contenedor (mapa por resolución, gana el
  último del formato por defecto MPEG_4) → se reproducía AV1 siempre que existía (y en 1440p/2160p, donde no hay H.264),
  aunque el chip no lo descodifique: descodificación por software (tirones, calor, batería; probable causa de tirones en
  la tele y de los parones en la ventana flotante). `videoprime/CodecSupport.preferHardware`: de los streams de una misma
  resolución quita los que el aparato no descodifica por hardware (`MediaCodecList`, `isHardwareAccelerated` en API 29+,
  tamaño admitido) si queda alguno que sí; códec desconocido (Twitch, progresivos) o aparato sin datos = lista intacta.
  Afecta también a la lista de calidades y a Descargar (ya no ofrecen la variante que iría por software). No se comprueba
  perfil/HDR/fps. Sin probar en aparatos. El usuario volvió a descartar la tele (TDT): solo quedarían RTVE/TRECE/À Punt
  y locales; Atresmedia/Mediaset ni siquiera están ya en TDTChannels.
- 2026-11-12 (136): Foto de la tele: algunas portadas del Inicio se quedaban borrosas hasta que la tarjeta salía de
  pantalla y volvía. Causa: la espera corta de `BestThumbnail` (450 ms, entrada 118): en la tele la grande tarda más, se
  pintaba la pequeña y la grande se bajaba aparte (`fetchLater`) sin avisar a la tarjeta. Ahora `BestThumbnail.upgraded`
  (SharedFlow con la dirección grande al terminar `fetchLater`) y `SmoothImage` vuelve a pedir la imagen una vez
  (`version`, `key(version)`; la pequeña queda debajo mientras): sale de memoria. Solo pantallas Compose (Inicio,
  Suscripciones…); las listas clásicas siguen igual. Sin probar en la tele.
- 2026-11-12 (137): "Al entrar a un vídeo hay un poco de tirón". Trabajo quitado del momento en que arranca el vídeo:
  (1) `VideoDetailFragment.updateTabsLater`: Siguiente/Descripción/Comentarios (antes `CommentsFragment` se creaba en
  `initTabs`; ahora `EmptyFragment` y se cambia en `updateTabs`) se rellenan 450 ms después de llegar el vídeo (1,2 s en
  la tele); se compara por URL (el reproductor puede dar otra copia del mismo `StreamInfo`), se cancela en `onDestroyView`
  y en el cambio de ajustes, no vuelve a mostrar el pager oculto para la ventana flotante y el panel de la tele/tablet se
  ve al momento. (2) `MainPlayerUi.applyStyle` no repite el mismo estilo (`appliedStyle`, ~20 vistas remaquetadas en cada
  cambio de vídeo/cola/pantalla completa). (3) `PlayerUpNext.Adapter.submit` no reconstruye la fila si trae lo mismo.
  Causas probables que NO se han tocado (revisión): en la tele `FrameRateMatcher` cambia el modo de pantalla al primer
  fotograma (puede congelar 0,5-2 s; ajuste "Ajustar la tele al vídeo", pedido al usuario que pruebe apagarlo);
  `Player.initPlayback` destruye y crea el ExoPlayer en cada vídeo (`release()` bloquea el hilo principal hasta 500 ms):
  reutilizarlo sería la mejora grande, con riesgo; en tele/tablet el vídeo se maqueta pequeño y salta a pantalla completa
  en `onPlaying`. Sin probar en aparatos.
- 2026-11-12 (138): "En el pausa y play también se buggea, como que se raya". En `VideoPlayerUi.onPlaying/onPaused` (de
  NewPipe) el botón se encogía (80 ms), cambiaba el icono y volvía a crecer con los demás botones redondos (200 ms), y
  encima iba la animación de `PlayPauseDrawable` (entrada 113). Ahora `switchPlayPause`: si el botón ya está en pantalla
  (y los −10/+10 también) solo cambia el icono en su sitio; si estaba oculto por la carga, entra como antes. Sin
  confirmar si el usuario se refería al botón o a la imagen del vídeo (en la tele con tunneling podría ser la imagen).
- 2026-11-12/13 (139): "Unifica las animaciones para que no se solapen, con cuidado con la ventana flotante". Casi todas
  pasan por `ktx/View.kt` `View.animate(enterOrExit, …)` y fallaba al pisarse dos: (a) "mostrar" con la vista a medio
  ocultar solo ponía alpha 1 y dejaba la escala (.8) o el desplazamiento a medias → ahora vuelve animada (≤150 ms,
  `RETURN_MILLIS`) a alpha 1/escala 1/translationY 0; (b) mostrar una vista oculta con alpha 1 ya no "salta": empieza en 0;
  (c) las salidas con escala parten de donde está la vista (antes saltaban a tamaño completo); (d) los fundidos de un
  `ViewGroup` usan `withLayer()` (los controles del reproductor se dibujaban pieza a pieza en cada fotograma).
  `VideoPlayerUi.updatePlayPauseButton(action, morph)`: sin la animación del icono mientras el botón entra o sale. La
  ventana flotante no usa esta función (`VideoPip`, `ExpandableSurfaceView`, `hidePlayerControlsNow` van por
  `setVisibility`): revisado por agente, sin regresiones. Barra del vídeo a tirones al arrastrarla (móvil y mando):
  `onProgressChanged` recortaba y escalaba la imagen de vista previa en CADA paso y recolocaba su caja con
  `setLayoutParams` (maquetado completo) → `SeekbarPreviewThumbnailHolder.getFrameKeyAt` (solo se rehace si cambia el
  fotograma, `shownPreviewFrame`), la caja se mueve con `translationX` y los textos solo se ponen si cambian. Esta última
  parte sin pasar por el revisor (límite de uso de los agentes hasta las 2:10 UTC). Sin probar en aparatos.
- 2026-11-13 (140): "Al entrar no se actualizan los vídeos hasta que se pone otra pantalla y se vuelve" (tele y móvil).
  Causas (revisión por agente): (A) `HomeFragment.lastSmartRefresh` (estático) se apuntaba ANTES de saber si la
  actualización arrancaba: `startService` va dentro de `runCatching` y Android lo rechaza a veces justo al volver la app
  al frente (o paraba el servicio silencioso a medias) → sin actualización y sin reintento en 10 min. (B) el Inicio solo
  releía la BD con los eventos de esa actualización o al recrearse: lo que entraba por otro camino (push, comprobación en
  segundo plano) no se veía hasta cambiar de pantalla. Ahora: `videoprime/QuietFeedUpdate.kt` (la actualización del Inicio
  la hace la app con `FeedLoadManager.startLoading`, sin servicio; tope 10 min; un fallo al arrancar se avisa como error);
  `lastSmartRefresh` solo si arrancó y a 0 con `ErrorResultEvent`; `CATCH_UP_MILLIS` 5 min; el Inicio observa la tabla
  `feed` (`InvalidationTracker`, `throttleLatest` con el mismo ritmo que las recargas) y relee; eventos con
  `onBackpressureLatest` y `onError`; `load()` reintenta una vez a los 2 s si falla. `FeedLoadService` queda para
  `FeedFragment` (grupos). No tocado: al abrir, los canales comprobados hace <5 min se saltan (ajuste de NewPipe) y el RSS
  de YouTube va con hasta ~15 min de retraso. Sin probar en aparatos.
- 2026-11-13 (141): "Cerré la app de la tele y a los 10 s el móvil no tenía el avance (casi 30 min de diferencia)".
  Causas: (a) el avance solo se guardaba al pausar, saltar o cerrar (NewPipe): 30 min sin pausar = nada guardado ni
  sincronizado; (b) al cerrar, `Player.destroy` guardaba y acto seguido `databaseUpdateDisposable.clear()` podía cancelar
  ese guardado; (c) la subida iba 10 s después dentro de la app, que en la tele ya estaba cerrada/congelada. Ahora:
  guardado cada 30 s mientras suena (`saveStreamProgressStateEverySoOften`, no en directos; la tabla cambia → sincroniza
  sola a los 10 s), guardado final no cancelable (`saveFinalStreamProgressState`) y, al escribirse, `SyncManager.flush`:
  trabajo único de WorkManager (`FlushWorker`, urgente en Android 12+, con red) que sube aunque la app se cierre.
  Primera pausa "rara": `PlayPauseDrawable` avanza por fotogramas (`TimeAnimator`, cada fotograma cuenta 34 ms como
  mucho): si el móvil está ocupado al pausar (notificación, sesión; más la primera vez) la animación se retrasa en vez
  de saltarse la mitad. La APK 1925ae9 (entrada 140) no se publicó: la prueba de arranque vio desaparecer el proceso sin
  traza (como en las entradas 50 y 70, emulador de Android 16); se publica con esta. Sin probar en aparatos.
- 2026-11-13/14 (142): Vídeo de la tele: dejando pulsado derecha en la barra, saltos grandes e irregulares (Android
  repite la tecla ~20 veces/s y cada repetición era un paso de 10 s; además cada paso recortaba otra imagen de vista
  previa). `FocusAwareSeekBar`: pulsación corta = un paso como antes; mantenida, la barra se desliza fotograma a
  fotograma (`TimeAnimator`, 20 s de vídeo por segundo al empezar y acelera 3 s hasta cruzar la barra entera en 15 s,
  mínimo 1 min/s; un fotograma tardío cuenta 50 ms como mucho); se para al soltar, al perder el foco (vista o ventana),
  al llegar a un extremo o si la barra se queda sin escuchador. El salto real sigue siendo al pulsar OK o salir de la
  barra. `VideoPlayerUi`: la imagen de vista previa se rehace como mucho cada 120 ms (`PREVIEW_MIN_MILLIS`, la última
  pedida sale al cumplirse el tiempo). El móvil (táctil) no cambia. Sin probar en la tele.
- 2026-11-14 (143): La animación de pausa/play de la 141 iba "muy lenta": contar cada fotograma como 34 ms como mucho
  en un aparato que dibuja despacio la convertía en cámara lenta. Ahora solo se frenan los 3 primeros fotogramas (el
  momento ocupado tras pulsar) y después sigue el reloj; duración 180 ms (antes 260), también en Compose.
- 2026-11-14 (144): Historial entre aparatos, auditoría (el usuario: "muy importante"). OJO: del 13-11 por la tarde no se
  publicó ninguna APK hasta 355c4a4 (1925ae9 falló la prueba de arranque sin traza y 3a00422/8e49935 se cancelaron al
  entrar el siguiente push: NO empujar a `videoprime` mientras corre el workflow de la APK si se quiere esa versión) →
  por eso tampoco salía el aviso de versión nueva. Causa principal del avance perdido (agente): al abrir el vídeo en el
  móvil, `onViewed` reescribe fecha y veces del historial; el elemento sincronizado es uno por vídeo {w, r, p}, así que
  el pull veía "cambiado aquí", descartaba la posición de la tele y el push subía la vieja del móvil encima (gana el
  último). Ahora `SyncSection.mark/merge`: para `hist` la marca es `p` (guardada en `pulseplay_sync/hist.marks.json` al
  aplicar un pull y tras cada push); en conflicto se queda la posición que llega salvo que este aparato haya movido la
  suya desde la última sincronización (sin marca todavía = gana la que llega), y fecha/veces = el mayor; lo mezclado
  se sube después. `SyncedProgress` + `Player.adoptSyncedProgress`: si la posición llega con el vídeo recién empezado
  aquí (<60 s desde que empezó ese vídeo, diferencia >10 s, no visto hasta el final) el reproductor salta a ella.
  Además: `MainActivity.onStop` → `SyncManager.flush` (sincroniza ya + trabajo de respaldo); el push `sync` se atiende
  hasta el final en el hilo de Firebase (15 s máx.; en segundo plano se quedaba a medias); espera tras un cambio 4 s
  (antes 10); la tele sincroniza 3 s tras abrir (antes 10); el guardado periódico no empieza hasta 30 s después de
  empezar a ver. Todos los aparatos vinculados necesitan esta versión. Sin probar con dos aparatos reales.
- 2026-11-14 (145): Informe del móvil "App closed: excessive resource use (excessive cpu 7830 ms en 300 s, límite 2 %)" en
  segundo plano (importance 400), probablemente por la 141/144: la tele subía el avance cada 30 s, cada subida avisaba al
  móvil por FCM y cada aviso hacía una sincronización completa (pull + push que lee y calcula el hash de todo: ~1100
  vídeos del historial, listas, colas, ajustes); además un proceso cerrado y vuelto a arrancar por un push lo comparaba
  todo otra vez. Sin confirmar en el móvil. Ahora: (1) `SyncManager.quietChange()` (guardado periódico del reproductor):
  esa subida va sin aviso a los demás (`ping=false` si solo hay ≤2 elementos `hist` y han pasado <20 s); pausar
  (`loudChange`), cerrar el reproductor y salir de la app (`flush`) sí avisan; (2) `dirtyKinds`/`dirtyAll`: el push solo
  lee los tipos marcados (tablas de Room, ajustes, colas, mezclas de un pull); "limpio" se guarda en prefs
  (`clean`, puesto a false con commit al primer cambio) para que un arranque en frío no lo compare todo; todo entero una
  vez al día (`last_full`: nombres/miniaturas de `streams` cambian sin señal). Pendiente conocido: aplicar un pull
  marca `hist` y provoca otra sincronización corta 4 s después (un repaso del historial sin subir nada).
- 2026-11-14 (146): Auditoría de fluidez del equipo (PDF del 14-11, base 0c5422b), valorada con el código. Aplicado:
  `PlayPauseShape.pointsInto` (array reutilizado en el Drawable y en Compose, antes uno nuevo por fotograma); cola
  (`QueueScreen.SwipeToRemove`): la fila sigue al dedo con un `mutableFloatStateOf` y solo se anima al soltar con un
  único trabajo `settling` (antes una corrutina por cada movimiento); `itemsIndexed` en vez de `shown.indexOf` por fila;
  suma de duraciones recordada; `TwitchLowLatency`: tope de 1 MB y lista original si la reescritura falla. No aplicado:
  separar metadatos/progreso de "Sonando ahora" (la posición cambia 1 vez/s), reducir `AvatarPrefetch` o búferes sin
  medir (el informe pide medir; menos fotos reabre la lentitud de Suscripciones en la tele), Macrobenchmark/Perfetto
  (hacen falta aparatos). El perfil de arranque es una grabación real del emulador (entrada 125), no Macrobenchmark.
- 2026-11-14 (147): Vídeo del usuario: de Historial a Inicio pasaba un momento por Biblioteca. El `MainFragment` rehecho
  tras volver de la pila mostraba la última página guardada del ViewPager y la sección elegida se ponía con un `post`
  (que en una vista aún sin pegar corre tras el primer fotograma). Ahora se aplica en `onViewStateRestored` (después de
  que el ViewPager recupere su página y antes de dibujar), con el `post` solo de guarda; `sectionToOpen` se borra en
  `onDestroyView`. Vale para cualquier sección pulsada desde cualquier pantalla interior.
  Fotos de Suscripciones (móvil ≠ tele, nombres en blanco, fotos que cambian): (1) la "última visita" de cada canal
  (`ChannelVisits`, prefs `videoprime_channel_visits`) era de cada aparato → `VisitSection` ("visit", la más reciente
  gana; `SyncManager.changed(kind)` la marca; `ChannelsFragment` vuelve a contar 300 ms después de que lleguen). Puede
  quedar diferencia si un aparato aún no tiene los últimos vídeos en su feed. (2) Nombres: algunas cabeceras de YouTube
  llegan sin nombre y `SubscriptionManager.updateFromInfo` lo borraba → nunca se pisa con uno vacío; si falta, el autor
  del primer vídeo. (3) Fotos/nombres iban y venían entre aparatos por la sincronización (cada uno guarda su foto con
  su tamaño) → `SubscriptionSection.apply` conserva nombre y foto locales y solo rellena lo que falta (también en
  conflicto, `merge`); la campana se sigue compartiendo. Sin probar en aparatos.
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
