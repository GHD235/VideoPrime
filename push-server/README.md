# Servidor de avisos instantáneos de VideoPrime

Cloudflare Worker (plan gratuito) que avisa al móvil en segundos de los vídeos nuevos de YouTube y
de los directos de Twitch de los canales con la campana activada.

```
YouTube ──WebSub──┐                        ┌── tema yt_<canal> ──┐
                  ├─▶ Worker (este código) ─▶ Firebase Cloud    ─┼─▶ móviles suscritos
Twitch ─EventSub──┘   + Durable Object      Messaging (FCM)     └── tema tw_<login> ──┘
                        con SQLite
        ▲
        └── la app manda la lista de canales a vigilar (POST /register)
```

- **YouTube**: WebSub (PubSubHubbub) de `pubsubhubbub.appspot.com`. El servidor se suscribe al feed
  de cada canal (concesión de 10 días, se renueva sola el último día) y YouTube le manda cada
  subida firmada con HMAC-SHA1. Solo se avisa de vídeos publicados hace menos de 48 h y una vez
  por vídeo (las ediciones de títulos llegan igual que las subidas).
- **Twitch**: EventSub `stream.online` por webhook, firmado con HMAC-SHA256; se descartan los
  mensajes de más de 10 minutos y los repetidos. El título y el juego se piden a Helix.
- **Al móvil**: FCM HTTP v1, mensaje solo de datos con prioridad alta y un tema por canal. La app
  dibuja la notificación (respeta la campana y no repite lo que ya avisó la comprobación normal).
- **Mantenimiento** (cron cada 10 min y justo después de cada registro): suscripciones nuevas,
  renovaciones y bajas, en tandas de 40 peticiones (límite del plan gratuito: 50 por invocación).
  Un canal que ninguna app ha pedido en 30 días se olvida (la app recuerda su lista cada día).
- **Privacidad**: el servidor no guarda nada personal. Solo sabe qué canales públicos se
  vigilan; las suscripciones a los temas las gestiona Firebase con un token anónimo del móvil.
  Ver `docs/videoprime/privacidad-avisos.md`.
- **Claves**: viven en los secrets de GitHub y de Cloudflare, nunca en el repositorio. Los
  secretos con los que firman YouTube y Twitch se derivan de esas claves (no hay que crear más).

## Rutas

| Ruta | Para qué |
| --- | --- |
| `GET /status` | Estado: canales vigilados y suscritos, último aviso enviado. Sin datos personales. |
| `POST /register` | La app manda `{"youtube": ["UC…"], "twitch": ["login"]}` (validados y con tope). |
| `GET/POST /websub/yt?ch=UC…` | Verificación y avisos de YouTube. |
| `POST /eventsub` | Verificación, avisos y revocaciones de Twitch. |

## Puesta en marcha (una vez)

Todo se hace desde las webs de cada servicio y desde GitHub; no hace falta instalar nada.

1. **Firebase** (console.firebase.google.com): proyecto nuevo sin Google Analytics; añadir app
   Android `com.ghd235.videoprime.continuous` y descargar `google-services.json`; en
   Configuración del proyecto → Cuentas de servicio → "Generar nueva clave privada" (otro `.json`).
2. **Cloudflare** (dash.cloudflare.com): cuenta gratuita; abrir *Workers & Pages* una vez para
   elegir el subdominio `workers.dev`; copiar el *Account ID*; Mi perfil → API Tokens → Create
   Token → plantilla **Edit Cloudflare Workers**.
3. **Twitch** (dev.twitch.tv/console, requiere 2FA): Register Your Application, redirect
   `http://localhost`, categoría *Application Integration*, cliente *Confidential*; copiar el
   Client ID y crear un Client Secret.
4. **GitHub** → Settings → Secrets and variables → Actions → secrets:
   `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `FCM_SERVICE_ACCOUNT` (el `.json` de la clave
   privada, entero), `FIREBASE_CONFIG` (el `google-services.json`, entero), `TWITCH_CLIENT_ID`,
   `TWITCH_CLIENT_SECRET`.
5. Actions → **VideoPrime: servidor de avisos** → Run workflow. Luego **VideoPrime: APK** → Run
   workflow (la dirección del servidor se averigua sola con la API de Cloudflare; la variable de
   repositorio `VIDEOPRIME_PUSH_URL` la sustituye si hiciera falta).
6. En el móvil: Ajustes → Notificaciones → **Avisos instantáneos**.

## Desarrollo

```sh
npm test                                   # pruebas de lib.js (node --test)
npx wrangler@4.149.0 dev                   # local, con claves de prueba en .dev.vars
```

`.dev.vars` (no se sube) lleva `FCM_SERVICE_ACCOUNT='{…}'`, `TWITCH_CLIENT_ID` y
`TWITCH_CLIENT_SECRET`. En local los envíos a FCM fallan (claves falsas) pero se ven en el log.
