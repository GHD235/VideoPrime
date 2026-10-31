// VideoPrime push server: pure helpers (no Cloudflare APIs), tested with `node --test`.

export const YT_CHANNEL = /^UC[A-Za-z0-9_-]{22}$/;
export const TW_LOGIN = /^[a-z0-9_]{1,25}$/;

/** Most channels one request may register, and most the server watches in total. */
export const MAX_PER_REQUEST = 2000;
export const MAX_YOUTUBE = 5000;
export const MAX_TWITCH = 2000;

/** A video older than this is an edit of an old one, not news. */
export const NEWS_MAX_AGE_MS = 48 * 3600 * 1000;

const encoder = new TextEncoder();

/** The channels of a registration, cleaned; null if the body is not one. */
export function parseRegistration(body) {
  if (!body || typeof body !== "object") return null;
  const youtube = Array.isArray(body.youtube) ? body.youtube : [];
  const twitch = Array.isArray(body.twitch) ? body.twitch : [];
  if (youtube.length > MAX_PER_REQUEST || twitch.length > MAX_PER_REQUEST) return null;
  return {
    youtube: [...new Set(youtube.filter((c) => typeof c === "string" && YT_CHANNEL.test(c)))],
    twitch: [
      ...new Set(
        twitch
          .filter((l) => typeof l === "string")
          .map((l) => l.toLowerCase())
          .filter((l) => TW_LOGIN.test(l)),
      ),
    ],
  };
}

function decodeXml(text) {
  return text
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-fA-F]+);/g, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&amp;/g, "&")
    .trim();
}

function tag(xml, name) {
  const match = xml.match(new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`));
  return match ? decodeXml(match[1]) : null;
}

/**
 * The videos of a YouTube WebSub notification (an Atom feed). A deleted video comes as an
 * <at:deleted-entry>, which has no <entry>: it gives nothing.
 */
export function parseYouTubeAtom(xml) {
  const videos = [];
  for (const match of xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)) {
    const entry = match[1];
    const videoId = tag(entry, "yt:videoId");
    const channelId = tag(entry, "yt:channelId");
    if (!videoId || !channelId) continue;
    const author = entry.match(/<author>([\s\S]*?)<\/author>/);
    videos.push({
      videoId,
      channelId,
      title: tag(entry, "title") ?? "",
      author: author ? (tag(author[1], "name") ?? "") : "",
      published: tag(entry, "published") ?? "",
      link: entry.match(/<link[^>]*href="([^"]+)"/)?.[1] ?? "",
    });
  }
  return videos;
}

/** Whether a video published at this date (ISO) is news at [now]. */
export function isRecent(published, now) {
  const at = Date.parse(published);
  return Number.isFinite(at) && now - at < NEWS_MAX_AGE_MS && at - now < NEWS_MAX_AGE_MS;
}

export function hex(buffer) {
  return [...new Uint8Array(buffer)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function sha256Hex(text) {
  return hex(await crypto.subtle.digest("SHA-256", encoder.encode(text)));
}

export async function hmacHex(algorithm, secret, message) {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: algorithm },
    false,
    ["sign"],
  );
  return hex(await crypto.subtle.sign("HMAC", key, encoder.encode(message)));
}

/** Compares two strings in a time that does not depend on where they differ. */
export function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/** WebSub: the body is signed with the subscription's secret as "sha1=<hex>". */
export async function verifyWebSub(secret, body, header) {
  if (!header || !header.startsWith("sha1=")) return false;
  return safeEqual(await hmacHex("SHA-1", secret, body), header.slice(5).toLowerCase());
}

/** Twitch EventSub: "sha256=" + HMAC of message id + timestamp + body. */
export async function verifyEventSub(secret, messageId, timestamp, body, header) {
  if (!messageId || !timestamp || !header || !header.startsWith("sha256=")) return false;
  const expected = await hmacHex("SHA-256", secret, messageId + timestamp + body);
  return safeEqual(expected, header.slice(7).toLowerCase());
}

/** Twitch asks to drop messages older than ten minutes (replays). */
export function isFreshTimestamp(timestamp, now) {
  const at = Date.parse(timestamp);
  return Number.isFinite(at) && Math.abs(now - at) < 10 * 60 * 1000;
}

function base64url(bytes) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToBytes(pem) {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
}

/** The signed JWT a Google service account trades for an access token (RS256). */
export async function serviceAccountJwt(account, scope, now) {
  const seconds = Math.floor(now / 1000);
  const header = { alg: "RS256", typ: "JWT", kid: account.private_key_id };
  const claims = {
    iss: account.client_email,
    scope,
    aud: account.token_uri || "https://oauth2.googleapis.com/token",
    iat: seconds,
    exp: seconds + 3600,
  };
  const unsigned =
    base64url(encoder.encode(JSON.stringify(header))) +
    "." +
    base64url(encoder.encode(JSON.stringify(claims)));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(unsigned));
  return unsigned + "." + base64url(new Uint8Array(signature));
}

/** FCM topic names: letters, digits and -_.~% only. */
export const youtubeTopic = (channelId) => `yt_${channelId}`;
export const twitchTopic = (login) => `tw_${login}`;

/** The FCM v1 message: data only (the app draws the notification), high priority. */
export function fcmMessage(topic, data, ttlSeconds) {
  const strings = {};
  for (const [k, v] of Object.entries(data)) strings[k] = String(v ?? "");
  return {
    message: {
      topic,
      data: strings,
      android: { priority: "HIGH", ttl: `${ttlSeconds}s` },
    },
  };
}

export const youtubeFeedUrl = (channelId) =>
  `https://www.youtube.com/xml/feeds/videos.xml?channel_id=${channelId}`;

// region sync (PulsePlay: the user's phone, tablet and TV share subscriptions, history, playlists...)
/** What can be synced; anything else is refused. */
export const SYNC_KINDS = new Set(["sub", "hist", "plist", "rplist", "queue", "pref"]);
export const SYNC_MAX_ITEMS = 500;
export const SYNC_MAX_KEY = 512;
export const SYNC_MAX_VALUE = 300_000;
export const SYNC_MAX_BODY = 2_000_000;
/** Letters and digits that cannot be mistaken for each other when typed with a TV remote. */
const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
export const SYNC_CODE_LENGTH = 6;

/** @return the items of a push, or null if anything in it is not acceptable */
export function parseSyncPush(body) {
  if (!body || !Array.isArray(body.items) || body.items.length > SYNC_MAX_ITEMS) return null;
  const items = [];
  for (const item of body.items) {
    if (!item || typeof item.kind !== "string" || !SYNC_KINDS.has(item.kind)) return null;
    if (typeof item.key !== "string" || item.key.length === 0 || item.key.length > SYNC_MAX_KEY) {
      return null;
    }
    let value = null;
    if (item.value !== undefined && item.value !== null) {
      value = JSON.stringify(item.value);
      if (value.length > SYNC_MAX_VALUE) return null;
    }
    items.push({ kind: item.kind, key: item.key, value });
  }
  return items;
}

/** A device's name, as shown in the list of linked devices. */
export function deviceName(value) {
  return typeof value === "string" ? value.replace(/[\u0000-\u001f]/g, "").slice(0, 60) : "";
}

export function randomToken(bytes = 32) {
  const array = crypto.getRandomValues(new Uint8Array(bytes));
  return btoa(String.fromCharCode(...array)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function newSyncCode() {
  const array = crypto.getRandomValues(new Uint8Array(SYNC_CODE_LENGTH));
  return Array.from(array, (b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
}

/** What the user typed, as the code it means (spaces, dashes and case do not matter). */
export function normalizeSyncCode(value) {
  if (typeof value !== "string") return null;
  const code = value.toUpperCase().replace(/[\s-]/g, "");
  if (code.length !== SYNC_CODE_LENGTH) return null;
  for (const c of code) if (!CODE_ALPHABET.includes(c)) return null;
  return code;
}

export const syncTopic = (group) => `sync_${group}`;
// endregion
