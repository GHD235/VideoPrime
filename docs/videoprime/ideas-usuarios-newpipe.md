# NewPipe: qué odian, qué piden y qué falta. Plan para VideoPrime

Fecha: 2026-10-21. Método: skill `replica-entrepreneur` (prueba).

## Muestra y límites (leer primero)

- **119 filas, 1 sola fuente**: issues de GitHub de `TeamNewPipe/NewPipe`, que son las ~75 "feature request"
  y las ~47 "bug" con más votos (+1). (Datos de la prueba de la skill replica-entrepreneur, ya desinstalada.)
- **Fuentes que fallaron**: Hacker News (hn.algolia.com, DNS/proxy bloqueado), Reddit (www/old.reddit.com,
  proxy 403 y WebFetch no puede), Lemmy, F-Droid y AlternativeTo (proxy 403). `gh api` está limitado a los
  repos de la sesión y `add_repo` para TeamNewPipe/NewPipe fue denegado. Solo se llegó a la API pública de
  GitHub a través de WebFetch.
- **Texto = solo el título del issue** (no la primera frase del cuerpo). Los títulos los transcribió el modelo
  de WebFetch desde el JSON de la API. Dos títulos que salieron en dos consultas distintas (#12333, #4479)
  coinciden, pero no se pudieron comprobar uno a uno (la API sin autenticar dio 403 por límite). Tratar las citas
  como "muy probablemente literales", no como verificadas.
- Sesgo: los issues de GitHub los escriben usuarios técnicos y el orden por votos favorece los issues antiguos.
  Al haber una sola fuente, `feedback.md` marca **todos** los temas como "thin". `reviews.py` no usa los votos
  (todas las filas pesan igual, sin estrellas), así que aquí se ordena por la suma de +1 calculada aparte.

Suma de votos (+1) por tema (n = issues; "abiertos" = votos de issues aún abiertos):

| tema | n | votos | votos abiertos |
| --- | --- | --- | --- |
| No reproduce / extracción rota (403, bot, parseo) | 23 | 887 | 123 |
| Reproductor: pantalla completa, gestos, emergente, atrás | 20 | 495 | 222 |
| Diseño y navegación (Material 3, pestañas, comentarios) | 13 | 488 | 159 |
| Feed de suscripciones / canal vacío o mal | 11 | 444 | 92 |
| Escucha (temporizador, listas, Android Auto) | 11 | 427 | 188 |
| Filtrar, bloquear, ordenar, buscar | 12 | 416 | 292 |
| Otros frontends (Invidious, Piped, PeerTube, iOS, proxy) | 12 | 351 | 268 |
| Descargas (lote, listas, sin conexión, metadatos) | 8 | 326 | 132 |
| Sincronización, cuentas, copia | 9 | 280 | 256 |
| Enviar a la TV (Chromecast, DLNA) | 4 | 193 | 74 |
| SponsorBlock / dislikes / DeArrow | 3 | 114 | 30 |

## 1. Lo que odian (cosas que la app hace mal)

1. **Los vídeos dejan de reproducirse cada pocos meses** (23 issues, 887 votos, 1 fuente). Casi todos cerrados:
   se arreglan con cada versión del extractor y vuelven. "No Video Will play (any video)"
   https://github.com/TeamNewPipe/NewPipe/issues/7734 (129 votos); "[YouTube] "Sign in to confirm..."/"Watch on the
   latest version..."" https://github.com/TeamNewPipe/NewPipe/issues/11139 (49, abierto).
2. **El reproductor estorba** (20 issues, 495 votos, 10 abiertos). "Unified player should be optional, makes the
   app useless in certain scenarios" https://github.com/TeamNewPipe/NewPipe/issues/4460 (42); "An option to disable
   the video history/queue control with the "back" button" https://github.com/TeamNewPipe/NewPipe/issues/4479 (24).
3. **El feed / la página del canal sale vacía** (11 issues, 444 votos). "[STOP CREATING DUPLICATES] [YouTube] Channel
   page doesn't contain any video/no new video when not using dedicated feed"
   https://github.com/TeamNewPipe/NewPipe/issues/9223 (145, el bug más votado).
4. **No retoma un vídeo pausado hace horas** (1 issue, 33 votos, abierto, thin): "App can't continue YouTube video
   that was paused some hours ago" https://github.com/TeamNewPipe/NewPipe/issues/5730.
5. **Cuelgues / ANR** (7 issues, 174 votos, todos cerrados): "Constantly getting "Newpipe isn't responding" popups
   randomly while watching Youtube videos" https://github.com/TeamNewPipe/NewPipe/issues/9390.

## 2. Lo que falta (funciones pedidas por su nombre)

1. **Bloquear canales/palabras y ocultar contenido** (abierto): "Blacklist videos, channels and keywords"
   https://github.com/TeamNewPipe/NewPipe/issues/1815 (68); "hiding paid content"
   https://github.com/TeamNewPipe/NewPipe/issues/12883 (20); "[YouTube] Make members-only videos in subscription
   list recognisable" https://github.com/TeamNewPipe/NewPipe/issues/12040 (15).
2. **Ordenar y buscar dentro del canal** (abierto): "Sort videos (inside channels and playlists) by views and dates"
   https://github.com/TeamNewPipe/NewPipe/issues/7729 (61); "Ability to search videos within the specific channel"
   https://github.com/TeamNewPipe/NewPipe/issues/5441 (26).
3. **Descarga en lote y con metadatos**: "Quick downloads in bulk - download multiple videos, queue or playlist"
   https://github.com/TeamNewPipe/NewPipe/issues/5482 (60, abierto); "Add metadata while downloading files"
   https://github.com/TeamNewPipe/NewPipe/issues/4341 (28, abierto).
4. **Enviar a la TV**: "Chromecast support" https://github.com/TeamNewPipe/NewPipe/issues/668 (119, cerrado
   "not planned"); "Add button to stream to TV" https://github.com/TeamNewPipe/NewPipe/issues/5060 (35);
   "UPnP / DLNA streaming on network receiver" https://github.com/TeamNewPipe/NewPipe/issues/105 (18).
5. **Datos de la comunidad**: "Add integration with returnyoutubedislike.com"
   https://github.com/TeamNewPipe/NewPipe/issues/7469 (48); "Integrate Sponsor Block mechanism into NewPipe"
   https://github.com/TeamNewPipe/NewPipe/issues/2554 (36); "Requesting Dearrow feature"
   https://github.com/TeamNewPipe/NewPipe/issues/10288 (30, abierto).
6. **Sincronizar entre dispositivos** (abierto): "Sync between devices"
   https://github.com/TeamNewPipe/NewPipe/issues/5325 (46); "Self-hosted sync server?"
   https://github.com/TeamNewPipe/NewPipe/issues/8039 (28).
7. **Gestos**: "Pinch zoom" https://github.com/TeamNewPipe/NewPipe/issues/1406 (26); "Fast forward/backward on long
   press video sides" https://github.com/TeamNewPipe/NewPipe/issues/8461 (18).

## 3. Lo que queda sin resolver (trabajos enteros que NewPipe ignora; sirve para el posicionamiento)

- **Un solo "YouTube" en varios dispositivos sin cuenta de Google** (sync + login a Invidious/Piped + portar a
  otras plataformas: 4 abiertos con 256 votos, más "NewPipe ports on other platforms" con 99,
  https://github.com/TeamNewPipe/NewPipe/issues/1051). NewPipe no tiene backend y no lo va a tener.
- **Ver en la tele del salón** (193 votos; el más votado lo cerraron como "not planned").
- **Un feed limpio y bajo tu control** (bloquear, ordenar, ocultar pago/miembros: 292 votos abiertos, el tema con
  más votos sin resolver).
  Thin en todos: una sola fuente.

## Plan de arreglos para VideoPrime

Ya cubierto en VideoPrime (no se repite, según CLAUDE.md "Estado" y el código): temporizador para dormir
(`SleepTimer.kt`, #2830), Android Auto (#1758), reproducir descargas y caché sin conexión (#478, #10542,
`OfflineMusic`), rediseño Material 3/Compose (#7595, #7047), feed que se actualiza al abrir (#4967, #3329),
vigilancia diaria del extractor (tema 1; el arreglo de fondo es seguir el upstream, ya en marcha), preferir
audio original (#12333, ajuste heredado de upstream en `ListHelper`), sincronización (#5325: fase 6 de la hoja
de ruta; la evidencia apoya la opción de **servidor propio/autoalojable**, #8039).

| # | Qué hacer | Tamaño | Evidencia | ¿VideoPrime ya lo tiene? |
| --- | --- | --- | --- | --- |
| 1 | **Bloquear canales y palabras clave** (en Inicio, Directos, búsqueda y relacionados) y **ocultar vídeos de pago/solo miembros** con un ajuste. Lista local, exportable en la copia de seguridad. | M | #1815 (68), #12883 (20), #12040 (15) | No (grep sin resultados) |
| 2 | **Ordenar el canal por Recientes/Populares/Antiguos** (los chips que ya da YouTube en la pestaña Vídeos, vía extractor) y **buscar dentro del canal**. | M | #7729 (61), #5441 (26) | No |
| 3 | **Retomar un vídeo pausado hace horas**: si la URL del stream caducó (403 al reanudar), volver a extraer y seguir en la misma posición sin error. | S | #5730 (33, abierto), #13139 (51) | Sin comprobar; no aparece en "Estado" |
| 4 | **Descargar una lista o cola entera a la carpeta del usuario** (no solo a la caché privada de Sin conexión) con **metadatos** (título, artista, carátula cuadrada ya calculada en `CoverArt.kt`). | M | #5482 (60), #1058 (91), #4341 (28) | Parcial: la caché sin conexión baja listas enteras, pero a una carpeta privada y sin etiquetas |
| 5 | **SponsorBlock + Return YouTube Dislike + DeArrow, desactivados por defecto**: son APIs públicas de terceros; el aviso debe decir que se envía el id del vídeo (privacidad). | M | #7469 (48), #2554 (36), #10288 (30) | No (solo muestra dislikes si el extractor los da) |
| 6 | **Controles del reproductor**: opción para que "atrás" no recorra la cola, y gestos (pellizcar = ajustar/rellenar, pulsación larga en los lados = avance rápido, deslizar arriba/abajo = pantalla completa). | S | #4479 (24), #4460 (42), #1406 (26), #3181 (14), #8461 (18), #9683 (14) | Sin comprobar (el reproductor se rediseñó en la entrada 19) |
| 7 | **Enviar a la TV por DLNA/UPnP** (abierto, sin SDK de Google). Chromecast necesitaría la librería propietaria de Google Cast: decidirlo aparte. Sin DRM implicado. | L | #668 (119), #5060 (35), #105 (18), #7346 (21) | No |

Orden recomendado (evidencia × coste): 3, 1, 6, 2, 5, 4, 7.

## El ángulo (3 opciones)

- **A (recomendada).** Para quien sigue a muchos canales y está harto de que el feed le meta lo que no quiere,
  VideoPrime le deja bloquear, ordenar y ocultar, y avisa al momento de lo nuevo.
  Evidencia: filtrar/bloquear/ordenar, 12 issues y 416 votos (292 en abiertos), 1 fuente.
- **B.** Para quien escucha más que mira (música, podcasts en el coche), VideoPrime es un reproductor de música con
  colas, temporizador, Android Auto y sin conexión. Evidencia: escucha, 11 issues y 427 votos, 1 fuente (ya cubierto
  en buena parte: es diferenciador, no un hueco).
- **C.** Para quien quiere su YouTube en el móvil y en la tele sin cuenta de Google, VideoPrime sincroniza con su
  propia cuenta y envía a la TV. Evidencia: sync + TV, 13 issues y 473 votos, 1 fuente (necesita la fase 6 y el punto 7).

No usar "NewPipe" en el nombre ni en anuncios. Las citas son material de investigación, no testimonios.
