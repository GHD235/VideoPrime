# Avisos instantáneos: qué datos se usan

Los avisos instantáneos son opcionales y vienen desactivados (Ajustes → Notificaciones →
Avisos instantáneos). Las comprobaciones normales de la app funcionan igual sin ellos.

## Qué pasa al activarlos

- El móvil obtiene de **Firebase Cloud Messaging** (Google) un identificador anónimo de
  instalación y se suscribe a un "tema" por cada canal que tiene la campana activada
  (`yt_<id del canal>`, `tw_<nombre del canal>`). Google sabe, por tanto, a qué temas está
  suscrito ese identificador; no recibe tu cuenta, tu nombre ni tu historial.
- La app manda al **servidor de VideoPrime** (Cloudflare Workers) la lista de canales públicos
  que hay que vigilar, sin ningún identificador del móvil. El servidor guarda solo esos
  identificadores de canal y la fecha en que alguien los pidió por última vez; Cloudflare
  procesa la dirección IP de la petición como cualquier servidor web y el servidor no la guarda.
- Cuando YouTube o Twitch avisan al servidor de un vídeo nuevo o de un directo, el servidor lo
  publica en el tema de ese canal y Firebase lo entrega a los móviles suscritos.

## Qué no se hace

- No hay cuenta, inicio de sesión, analítica ni publicidad.
- El servidor no guarda quién sigue cada canal ni ninguna dirección IP.
- No se usa ningún dato para otra cosa que entregar los avisos.

## Cuánto se guarda

- Canales: hasta 30 días después de la última vez que una app los pidió.
- Vídeos y mensajes ya avisados (para no repetir): 7 días.

## Desactivar

Al apagar el interruptor la app borra su identificador de Firebase (con lo que deja todos los
temas a la vez) y deja de mandar la lista de canales. El servidor olvida los canales que nadie
pide en 30 días.

Responsable: el mantenedor de VideoPrime (GHD235). Proveedores: Google (Firebase Cloud
Messaging) y Cloudflare (Workers).
