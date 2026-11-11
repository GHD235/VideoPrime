# Ideas de apps abiertas (2026-11-11)

Estudio de solo funciones y flujos (nunca código, arte ni textos) de LibreTube, PipePipe, Tubular
(GPLv3, clientes de YouTube), Metrolist (GPLv3, música) y SmartTube (MIT, tele). REA no se usa
(ingeniería inversa de apps cerradas, contra la regla del proyecto). Excluido por decisión del
usuario: SponsorBlock, chat, letras, Spotify.

## Rendimiento y fluidez
R1 Búfer hacia atrás de 3 min (`LoadController`, hoy 0): volver atrás sin recargar. Alto, trivial.
R2 Caché del reproductor 64 MB → 256-512 MB (`PlayerHelper.getPreferredCacheSize`). Medio, trivial.
R3 Direcciones de stream resueltas al reproducir (caché con caducidad) y renovación automática. Alto, medio.
R4 Vigilante de parones: >20 s cargando en 60 s → recargar en el mismo segundo. Alto, bajo.
R5 Tele: descartar códecs que el aparato no decodifica por hardware (preferir VP9/AVC frente a AV1). Alto, bajo.
R6 Tele: imágenes al tamaño exacto de la tarjeta y liberar al salir de pantalla. Medio, bajo.
R7 Guardar cola y posición y retomarlas tras cerrar la app o reiniciar. Alto, medio.
R8 Recuperación ante 403 con SABR (protocolo nuevo de YouTube). Alto, alto (extractor).

## Reproductor de vídeo
V1 Mantener pulsado = 2× mientras se mantiene. V2 Pellizcar = llenar/ajustar. V3 Deslizar arriba/abajo =
pantalla completa. V4 Bloqueo de pantalla. V5 Tele: el mando avanza al momento con la interfaz oculta,
paso configurable, botones CH+/- y números (1 = 10 %…). V6 Tele: botón OK configurable. V7 Velocidad
recordada por canal. V8 Retraso de audio (barras de sonido). V9 Estilo de subtítulos.

## Música
M1 Fundido entre canciones (crossfade). M2 Ecualizador. M3 Radio: tocar = suena ya, la radio se carga
detrás. M4 Lista automática "Guardadas sin conexión". M5 Estadísticas y listas inteligentes (más
escuchadas, resumen anual). M6 Botones del coche/notificación: favorita, iniciar radio. M7 Pausa al
silenciar y retomar al conectar Bluetooth. M8 Temporizador con fundido / al acabar la canción.

## Feed, búsqueda y listas
F1 Filtro del Inicio: vídeos/Shorts/directos, ocultar vistos, próximos. F2 Filtros por palabra o canal
(ocultar Shorts, de miembros). F3 Horas de silencio de avisos. F4 Filtros de búsqueda (duración, fecha)
y buscar dentro de un canal. F5 Selección múltiple, ordenar y quitar duplicados en listas. F6 Descargar
una lista entera. F7 DeArrow (títulos/miniaturas sin clickbait).

## Tele / Android
T1 Canales en la pantalla de inicio de Android TV y "Seguir viendo" del sistema. T2 Búsqueda por voz
global ("Ok Google, pon X en PulsePlay"). T3 Atajos del icono (Suscripciones, Biblioteca…).
T4 Salvapantallas/atenuar en pausa.
