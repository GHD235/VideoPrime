// VideoPrime push server (Cloudflare Worker + one Durable Object with SQLite).
//
// YouTube tells the server about each upload through WebSub, Twitch about each stream that
// starts through EventSub; the server passes it on to the phones with Firebase Cloud Messaging,
// one topic per channel. It only knows which public channels are watched, never by whom: the
// phones subscribe to the topics with Firebase and tell the server the list of channels.

import { DurableObject } from "cloudflare:workers";
import {
  MAX_TWITCH,
  MAX_YOUTUBE,
  fcmMessage,
  hmacHex,
  isFreshTimestamp,
  isRecent,
  parseRegistration,
  parseYouTubeAtom,
  safeEqual,
  serviceAccountJwt,
  sha256Hex,
  twitchTopic,
  verifyEventSub,
  verifyWebSub,
  youtubeFeedUrl,
  youtubeTopic,
} from "./lib.js";

const HUB_URL = "https://pubsubhubbub.appspot.com/subscribe";
const DAY = 24 * 3600 * 1000;
const HOUR = 3600 * 1000;
/** A channel no phone asked for in this long is forgotten. */
const STALE_MS = 30 * DAY;
/** The free plan allows 50 outgoing requests per invocation: maintenance stays below. */
const BUDGET = 40;
const MAX_BODY = 200_000;
const UPDATES_TOPIC = "vp_updates";
// the repository was renamed from VideoPrime to PulsePlay: both addresses are ours
const RELEASE_PREFIXES = [
  "https://github.com/GHD235/PulsePlay/releases/download/",
  "https://github.com/GHD235/VideoPrime/releases/download/",
];
/** A hub may not set a lease longer than this (a forged verification could, otherwise). */
const MAX_LEASE_SECONDS = 11 * 24 * 3600;
/** Registrations per address and hour (kept in memory only). */
const MAX_REGISTER_PER_HOUR = 20;
/** Twitch pushes sent per alarm (each may take 4 outgoing requests). */
const PENDING_PER_ALARM = 3;
/** How often the server asks Twitch whether its subscriptions still work. */
const TWITCH_CHECK_MS = 6 * HOUR;

export default {
  async fetch(request, env) {
    return hub(env).fetch(request);
  },

  async scheduled(controller, env, ctx) {
    ctx.waitUntil(hub(env).maintain());
  },
};

function hub(env) {
  return env.HUB.get(env.HUB.idFromName("main"));
}

const text = (body, status = 200) =>
  status === 204
    ? new Response(null, { status })
    : new Response(body, { status, headers: { "content-type": "text/plain; charset=utf-8" } });
const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

export class Hub extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS yt (
        channel TEXT PRIMARY KEY, last_seen INTEGER NOT NULL, state TEXT NOT NULL,
        lease_until INTEGER NOT NULL DEFAULT 0, asked_at INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS tw (
        login TEXT PRIMARY KEY, last_seen INTEGER NOT NULL, state TEXT NOT NULL,
        user_id TEXT, sub_id TEXT, asked_at INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS seen (key TEXT PRIMARY KEY, at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT NOT NULL, exp INTEGER NOT NULL DEFAULT 0);
      CREATE TABLE IF NOT EXISTS tw_pending (
        id TEXT PRIMARY KEY, login TEXT NOT NULL, name TEXT NOT NULL, user_id TEXT NOT NULL,
        at INTEGER NOT NULL);
    `);
    this.maintaining = false;
    this.registrations = new Map();
  }

  // region small storage helpers
  get(k) {
    const row = this.sql.exec("SELECT v, exp FROM kv WHERE k = ?", k).toArray()[0];
    return row && (row.exp === 0 || row.exp > Date.now()) ? row.v : null;
  }

  put(k, v, exp = 0) {
    this.sql.exec(
      "INSERT INTO kv (k, v, exp) VALUES (?, ?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v, exp = excluded.exp",
      k,
      v,
      exp,
    );
  }

  /** Remembers a key; false if it was already there (a repeated message). */
  firstTime(key) {
    const done = this.sql.exec("SELECT 1 FROM seen WHERE key = ?", key).toArray().length > 0;
    if (!done) this.sql.exec("INSERT INTO seen (key, at) VALUES (?, ?)", key, Date.now());
    return !done;
  }

  count(query, ...args) {
    return this.sql.exec(query, ...args).one().n;
  }
  // endregion

  // region secrets
  account() {
    if (!this.env.FCM_SERVICE_ACCOUNT) return null;
    try {
      return JSON.parse(this.env.FCM_SERVICE_ACCOUNT);
    } catch {
      return null;
    }
  }

  twitchConfigured() {
    return Boolean(this.env.TWITCH_CLIENT_ID && this.env.TWITCH_CLIENT_SECRET);
  }

  /** The secrets YouTube and Twitch sign with, derived from the keys the server already has. */
  async websubSecret() {
    return (await sha256Hex("videoprime-websub:" + this.account().private_key)).slice(0, 40);
  }

  async eventsubSecret() {
    return (await sha256Hex("videoprime-eventsub:" + this.env.TWITCH_CLIENT_SECRET)).slice(0, 40);
  }

  /** Signs a channel's callback address: only the hub, which got it from us, knows it. */
  async callbackKey(channel) {
    return (await hmacHex("SHA-256", await this.websubSecret(), "callback:" + channel)).slice(0, 32);
  }
  // endregion

  async fetch(request) {
    const url = new URL(request.url);
    try {
      if (url.pathname === "/" || url.pathname === "/status") return this.status();
      if (url.pathname === "/register" && request.method === "POST") {
        return await this.register(request, url);
      }
      if (url.pathname === "/websub/yt") {
        return request.method === "GET"
          ? await this.websubVerify(url)
          : await this.websubNotify(request, url);
      }
      if (url.pathname === "/release" && request.method === "POST") {
        return await this.release(request);
      }
      if (url.pathname === "/eventsub" && request.method === "POST") {
        return await this.eventsub(request);
      }
      return text("Not found", 404);
    } catch (e) {
      console.error("request failed", url.pathname, e?.stack || e);
      return text("Error", 500);
    }
  }

  status() {
    return json({
      ok: true,
      firebase: this.account() !== null,
      twitch: this.twitchConfigured(),
      youtubeChannels: this.count("SELECT COUNT(*) AS n FROM yt"),
      youtubeSubscribed: this.count("SELECT COUNT(*) AS n FROM yt WHERE state = 'ok'"),
      twitchChannels: this.count("SELECT COUNT(*) AS n FROM tw"),
      twitchSubscribed: this.count("SELECT COUNT(*) AS n FROM tw WHERE sub_id IS NOT NULL"),
      lastPush: this.get("last_push"),
      lastPushVideo: this.get("last_push_info"),
      lastShortSkipped: this.get("last_short"),
      lastTwitchEvent: this.get("last_tw_event"),
      lastTwitchPush: this.get("last_tw_push"),
      twitchCheck: JSON.parse(this.get("tw_check_info") || "null"),
    });
  }

  // region registration
  async register(request, url) {
    if (!this.allowRegistration(request.headers.get("cf-connecting-ip") || "")) {
      return text("Too many requests", 429);
    }
    const body = await request.text();
    if (body.length > MAX_BODY) return text("Too large", 413);
    let parsed;
    try {
      parsed = parseRegistration(JSON.parse(body));
    } catch {
      parsed = null;
    }
    if (!parsed) return text("Bad request", 400);
    // the maintenance (a cron, without a request) needs the public address for the callbacks
    this.put("origin", url.origin);

    const now = Date.now();
    let ytRoom = MAX_YOUTUBE - this.count("SELECT COUNT(*) AS n FROM yt");
    for (const channel of parsed.youtube) {
      const known = this.count("SELECT COUNT(*) AS n FROM yt WHERE channel = ?", channel) > 0;
      if (known) {
        this.sql.exec("UPDATE yt SET last_seen = ? WHERE channel = ?", now, channel);
      } else if (ytRoom-- > 0) {
        this.sql.exec("INSERT INTO yt (channel, last_seen, state) VALUES (?, ?, 'new')", channel, now);
      }
    }
    let twRoom = MAX_TWITCH - this.count("SELECT COUNT(*) AS n FROM tw");
    for (const login of parsed.twitch) {
      const known = this.count("SELECT COUNT(*) AS n FROM tw WHERE login = ?", login) > 0;
      if (known) {
        this.sql.exec("UPDATE tw SET last_seen = ? WHERE login = ?", now, login);
      } else if (twRoom-- > 0) {
        this.sql.exec("INSERT INTO tw (login, last_seen, state) VALUES (?, ?, 'new')", login, now);
      }
    }
    // the new channels are subscribed right away, not at the next cron
    await this.ctx.storage.setAlarm(now + 1000);
    return json({ ok: true, youtube: parsed.youtube.length, twitch: parsed.twitch.length });
  }

  allowRegistration(address) {
    const now = Date.now();
    for (const [key, entry] of this.registrations) {
      if (now - entry.since > HOUR) this.registrations.delete(key);
    }
    const entry = this.registrations.get(address) ?? { since: now, count: 0 };
    entry.count++;
    this.registrations.set(address, entry);
    return entry.count <= MAX_REGISTER_PER_HOUR;
  }

  async alarm() {
    const spent = await this.sendPendingTwitch();
    await this.maintain(spent);
  }
  // endregion

  // region YouTube (WebSub)
  async websubVerify(url) {
    const channel = url.searchParams.get("ch") || "";
    const mode = url.searchParams.get("hub.mode");
    const topic = url.searchParams.get("hub.topic");
    const challenge = url.searchParams.get("hub.challenge") || "";
    if (topic !== youtubeFeedUrl(channel) || !this.account()) return text("Unknown topic", 404);
    if (url.searchParams.get("k") !== (await this.callbackKey(channel))) return text("Bad key", 403);
    const row = this.sql.exec("SELECT state FROM yt WHERE channel = ?", channel).toArray()[0];
    const known = row !== undefined;
    if (mode === "subscribe") {
      // only what this server asked for (a new subscription or a renewal)
      if (!known || row.state === "new") return text("Not asked", 404);
      const lease = Math.min(
        Number(url.searchParams.get("hub.lease_seconds")) || 5 * 24 * 3600,
        MAX_LEASE_SECONDS,
      );
      this.sql.exec(
        "UPDATE yt SET state = 'ok', lease_until = ? WHERE channel = ?",
        Date.now() + lease * 1000,
        channel,
      );
      return text(challenge);
    }
    if (mode === "unsubscribe") return known ? text("Still watched", 404) : text(challenge);
    if (mode === "denied") {
      console.warn("WebSub denied", channel, url.searchParams.get("hub.reason"));
      this.sql.exec("UPDATE yt SET state = 'new' WHERE channel = ?", channel);
    }
    return text("");
  }

  async websubNotify(request, url) {
    const channel = url.searchParams.get("ch") || "";
    const body = await request.text();
    if (body.length > MAX_BODY || !this.account()) return text("", 204);
    if (!(await verifyWebSub(await this.websubSecret(), body, request.headers.get("x-hub-signature")))) {
      return text("Bad signature", 403);
    }
    const now = Date.now();
    for (const video of parseYouTubeAtom(body)) {
      // an edit of an old video comes the same way as an upload: only recent ones are news
      if (video.channelId !== channel || !isRecent(video.published, now)) continue;
      if (!this.firstTime("yt:" + video.videoId)) continue;
      // Shorts are not told (the user does not want them)
      if (video.link.includes("/shorts/") || (await this.isShort(video.videoId))) {
        this.put("last_short", `${video.videoId} ${video.published}`);
        console.log("Short not told", channel, video.videoId);
        continue;
      }
      const sent = await this.send(
        youtubeTopic(channel),
        { t: "yt", c: channel, v: video.videoId, n: video.title, a: video.author, p: video.published },
        6 * 3600,
      );
      // not sent: a repetition of the notification (or a later edit) may try again
      if (!sent) {
        this.sql.exec("DELETE FROM seen WHERE key = ?", "yt:" + video.videoId);
      } else {
        // to see how long it took: published by YouTube, told to the phones
        this.put("last_push_info", `${video.videoId} published ${video.published}`);
      }
    }
    return text("", 204);
  }

  /**
   * YouTube answers /shorts/<id> itself for a Short and redirects a normal video to /watch.
   * Unknown (a consent page, an error): not a Short, so that no video is lost.
   */
  async isShort(videoId) {
    try {
      const response = await fetch(`https://www.youtube.com/shorts/${videoId}`, {
        method: "HEAD",
        redirect: "manual",
        headers: { cookie: "SOCS=CAI", "user-agent": "Mozilla/5.0" },
      });
      return response.status === 200;
    } catch {
      return false;
    }
  }

  async websub(mode, channel) {
    const origin = this.get("origin");
    const form = new URLSearchParams({
      "hub.callback": `${origin}/websub/yt?ch=${channel}&k=${await this.callbackKey(channel)}`,
      "hub.topic": youtubeFeedUrl(channel),
      "hub.mode": mode,
      "hub.verify": "async",
      "hub.lease_seconds": String(10 * 24 * 3600),
    });
    if (mode === "subscribe") form.set("hub.secret", await this.websubSecret());
    const response = await fetch(HUB_URL, { method: "POST", body: form });
    if (!response.ok) console.warn("WebSub", mode, channel, response.status, await response.text());
    return response.ok;
  }
  // endregion

  // region new versions of the app

  /**
   * The APK build tells that a new version was published; the phones get it on their updates
   * topic. Only the build knows the key: it comes from the Firebase key both have.
   */
  async release(request) {
    const account = this.account();
    if (!account) return text("", 204);
    const expected = await sha256Hex("videoprime-release:" + account.private_key);
    if (!safeEqual(request.headers.get("x-release-key") || "", expected)) return text("Forbidden", 403);
    let body;
    try {
      body = JSON.parse(await request.text());
    } catch {
      body = null;
    }
    const commit = typeof body?.commit === "string" ? body.commit : "";
    const apk = typeof body?.url === "string" ? body.url : "";
    if (!/^[0-9a-f]{7,40}$/.test(commit) || !RELEASE_PREFIXES.some((p) => apk.startsWith(p))) return text("Bad request", 400);
    const sent = await this.send(UPDATES_TOPIC, { t: "up", c: commit, u: apk }, 2 * 24 * 3600);
    return sent ? text("Sent") : text("Not sent", 502);
  }
  // endregion

  // region Twitch (EventSub)
  async eventsub(request) {
    if (!this.twitchConfigured()) return text("", 204);
    const body = await request.text();
    const id = request.headers.get("twitch-eventsub-message-id");
    const timestamp = request.headers.get("twitch-eventsub-message-timestamp");
    const signature = request.headers.get("twitch-eventsub-message-signature");
    const ok = await verifyEventSub(await this.eventsubSecret(), id, timestamp, body, signature);
    if (!ok || !isFreshTimestamp(timestamp, Date.now())) return text("Bad signature", 403);
    const message = JSON.parse(body);
    const type = request.headers.get("twitch-eventsub-message-type");

    if (type === "webhook_callback_verification") return text(message.challenge);
    if (!this.firstTime("tw-msg:" + id)) return text("", 204);
    if (type === "revocation") {
      const sub = message.subscription;
      console.warn("EventSub revoked", sub?.status, sub?.condition?.broadcaster_user_id);
      // a removed user is looked up again by login (renamed?) a day later; any other reason,
      // the subscription is made again within the hour
      const removed = sub?.status === "user_removed";
      this.sql.exec(
        "UPDATE tw SET sub_id = NULL, state = ?, asked_at = ?, user_id = CASE WHEN ? THEN NULL ELSE user_id END WHERE sub_id = ?",
        removed ? "missing" : "new",
        Date.now(),
        removed ? 1 : 0,
        sub?.id ?? "",
      );
      return text("", 204);
    }
    if (type === "notification" && message.subscription?.type === "stream.online") {
      const event = message.event;
      if (event?.type === "live" && this.firstTime("tw-live:" + event.id)) {
        // Twitch wants its answer at once (late answers count as failures, and too many end
        // the subscription): the push is sent right after, from the alarm
        this.sql.exec(
          "INSERT OR IGNORE INTO tw_pending (id, login, name, user_id, at) VALUES (?, ?, ?, ?, ?)",
          event.id,
          event.broadcaster_user_login ?? "",
          event.broadcaster_user_name ?? "",
          event.broadcaster_user_id ?? "",
          Date.now(),
        );
        this.put("last_tw_event", `${event.broadcaster_user_login} ${new Date().toISOString()}`);
        await this.ctx.storage.setAlarm(Date.now());
      }
    }
    return text("", 204);
  }

  /**
   * Sends the pushes of the streams Twitch told about (a few per alarm: the outgoing requests
   * of one invocation are limited).
   * @returns the outgoing requests used
   */
  async sendPendingTwitch() {
    const rows = this.sql
      .exec("SELECT id, login, name, user_id, at FROM tw_pending ORDER BY at LIMIT ?", PENDING_PER_ALARM)
      .toArray();
    let spent = 0;
    for (const row of rows) {
      this.sql.exec("DELETE FROM tw_pending WHERE id = ?", row.id);
      // a push about a stream that started long ago is no news any more
      if (Date.now() - row.at > 15 * 60 * 1000) continue;
      spent += 4; // the stream, the push and maybe both tokens
      const stream = row.user_id ? await this.twitchStream(row.user_id) : null;
      const sent = await this.send(
        twitchTopic(row.login),
        { t: "tw", l: row.login, n: row.name, s: stream?.title ?? "", g: stream?.game_name ?? "" },
        15 * 60,
      );
      if (sent) this.put("last_tw_push", `${row.login} ${new Date().toISOString()}`);
    }
    if (this.count("SELECT COUNT(*) AS n FROM tw_pending") > 0) {
      await this.ctx.storage.setAlarm(Date.now() + 1000);
    }
    return spent;
  }

  async twitchToken() {
    const cached = this.get("twitch_token");
    if (cached) return cached;
    const response = await fetch("https://id.twitch.tv/oauth2/token", {
      method: "POST",
      body: new URLSearchParams({
        client_id: this.env.TWITCH_CLIENT_ID,
        client_secret: this.env.TWITCH_CLIENT_SECRET,
        grant_type: "client_credentials",
      }),
    });
    if (!response.ok) throw new Error(`Twitch token ${response.status}: ${await response.text()}`);
    const token = await response.json();
    this.put("twitch_token", token.access_token, Date.now() + (token.expires_in - 600) * 1000);
    return token.access_token;
  }

  async helix(path, init = {}) {
    const response = await fetch("https://api.twitch.tv/helix/" + path, {
      ...init,
      headers: {
        "client-id": this.env.TWITCH_CLIENT_ID,
        authorization: "Bearer " + (await this.twitchToken()),
        "content-type": "application/json",
      },
    });
    if (response.status === 401) this.put("twitch_token", "", 1);
    return response;
  }

  /** The stream's title and game, to show them in the notification (best effort). */
  async twitchStream(userId) {
    try {
      const response = await this.helix("streams?user_id=" + encodeURIComponent(userId));
      return response.ok ? (await response.json()).data?.[0] : null;
    } catch (e) {
      console.warn("Twitch stream info", e?.message);
      return null;
    }
  }
  // endregion

  // region Firebase Cloud Messaging
  async fcmToken() {
    const cached = this.get("fcm_token");
    if (cached) return cached;
    const account = this.account();
    const jwt = await serviceAccountJwt(
      account,
      "https://www.googleapis.com/auth/firebase.messaging",
      Date.now(),
    );
    const response = await fetch(account.token_uri || "https://oauth2.googleapis.com/token", {
      method: "POST",
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: jwt,
      }),
    });
    if (!response.ok) throw new Error(`Google token ${response.status}: ${await response.text()}`);
    const token = await response.json();
    this.put("fcm_token", token.access_token, Date.now() + (token.expires_in - 300) * 1000);
    return token.access_token;
  }

  /** @return whether FCM took the message */
  async send(topic, data, ttlSeconds) {
    const account = this.account();
    if (!account) return false;
    try {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
        {
          method: "POST",
          headers: {
            authorization: "Bearer " + (await this.fcmToken()),
            "content-type": "application/json",
          },
          body: JSON.stringify(fcmMessage(topic, data, ttlSeconds)),
        },
      );
      if (response.status === 401) this.put("fcm_token", "", 1);
      if (!response.ok) {
        console.error("FCM", topic, response.status, await response.text());
        return false;
      }
      this.put("last_push", new Date().toISOString());
      console.log("FCM sent", topic);
      return true;
    } catch (e) {
      console.error("FCM", topic, e?.message);
      return false;
    }
  }
  // endregion

  // region maintenance
  async maintain(spent = 0) {
    if (this.maintaining || !this.get("origin") || !this.account()) return;
    this.maintaining = true;
    const budget = { left: BUDGET - spent };
    try {
      await this.checkSecrets();
      await this.maintainYouTube(budget);
      if (this.twitchConfigured()) {
        await this.maintainTwitch(budget).catch((e) => console.error("Twitch maintenance", e?.message));
      }
      this.sql.exec("DELETE FROM seen WHERE at < ?", Date.now() - 7 * DAY);
    } catch (e) {
      console.error("maintenance failed", e?.stack || e);
    } finally {
      this.maintaining = false;
    }
    // more to do than one invocation may: go on in a minute
    if (budget.left <= 0) await this.ctx.storage.setAlarm(Date.now() + 60 * 1000);
  }

  /**
   * The signing secrets come from the keys: if a key was changed, YouTube and Twitch still sign
   * with the old one, so every subscription is made again.
   */
  async checkSecrets() {
    const twitch = this.twitchConfigured() ? await this.eventsubSecret() : "";
    const hash = await sha256Hex((await this.websubSecret()) + "|" + twitch);
    const before = this.get("secret_hash");
    if (before === hash) return;
    if (before !== null) {
      console.warn("keys changed: subscribing everything again");
      this.sql.exec("UPDATE yt SET state = 'new', asked_at = 0");
      this.sql.exec("UPDATE tw SET sub_id = NULL, asked_at = 0 WHERE sub_id IS NOT NULL");
    }
    this.put("secret_hash", hash);
  }

  async maintainYouTube(budget) {
    const now = Date.now();
    const stale = this.sql
      .exec("SELECT channel, state FROM yt WHERE last_seen < ? LIMIT ?", now - STALE_MS, budget.left)
      .toArray();
    for (const row of stale) {
      this.sql.exec("DELETE FROM yt WHERE channel = ?", row.channel);
      if (row.state !== "new") {
        budget.left--;
        await this.websub("unsubscribe", row.channel);
      }
    }
    // new ones, ones asked an hour ago without an answer, and leases ending within a day
    const due = this.sql
      .exec(
        `SELECT channel FROM yt
         WHERE asked_at < ? AND (state IN ('new', 'asked') OR lease_until < ?)
         ORDER BY asked_at LIMIT ?`,
        now - HOUR,
        now + DAY,
        Math.max(budget.left, 0),
      )
      .toArray();
    for (const row of due) {
      budget.left--;
      // marked before asking: the hub may call back to verify before its answer arrives; a
      // failure (the hub busy) waits an hour too
      this.sql.exec(
        "UPDATE yt SET asked_at = ?, state = CASE state WHEN 'new' THEN 'asked' ELSE state END WHERE channel = ?",
        now,
        row.channel,
      );
      await this.websub("subscribe", row.channel);
    }
  }

  async maintainTwitch(budget) {
    const now = Date.now();
    const stale = this.sql
      .exec("SELECT login, sub_id FROM tw WHERE last_seen < ? LIMIT ?", now - STALE_MS, budget.left)
      .toArray();
    for (const row of stale) {
      this.sql.exec("DELETE FROM tw WHERE login = ?", row.login);
      if (row.sub_id && row.sub_id !== "exists" && budget.left > 0) {
        budget.left--;
        await this.helix("eventsub/subscriptions?id=" + encodeURIComponent(row.sub_id), {
          method: "DELETE",
        });
      }
    }

    // the user ids, 100 logins per request (missing ones, renamed or banned, retried daily)
    const unknown = this.sql
      .exec(
        "SELECT login FROM tw WHERE user_id IS NULL AND (state = 'new' OR asked_at < ?) LIMIT 100",
        now - DAY,
      )
      .toArray()
      .map((r) => r.login);
    if (unknown.length > 0 && budget.left > 1) {
      budget.left -= 2; // the token too
      const response = await this.helix(
        "users?" + unknown.map((l) => "login=" + encodeURIComponent(l)).join("&"),
      );
      if (response.ok) {
        const found = new Map((await response.json()).data.map((u) => [u.login, u.id]));
        for (const login of unknown) {
          const id = found.get(login);
          if (id) {
            this.sql.exec("UPDATE tw SET user_id = ?, state = 'new' WHERE login = ?", id, login);
          } else {
            this.sql.exec("UPDATE tw SET state = 'missing', asked_at = ? WHERE login = ?", now, login);
          }
        }
      } else {
        console.warn("Twitch users", response.status, await response.text());
      }
    }

    const origin = this.get("origin");
    await this.checkTwitchSubscriptions(origin, budget);
    const secret = await this.eventsubSecret();
    const pending = this.sql
      .exec(
        "SELECT login, user_id FROM tw WHERE user_id IS NOT NULL AND sub_id IS NULL AND asked_at < ? LIMIT ?",
        now - HOUR,
        Math.max(budget.left, 0),
      )
      .toArray();
    for (const row of pending) {
      budget.left--;
      const response = await this.helix("eventsub/subscriptions", {
        method: "POST",
        body: JSON.stringify({
          type: "stream.online",
          version: "1",
          condition: { broadcaster_user_id: row.user_id },
          transport: { method: "webhook", callback: `${origin}/eventsub`, secret },
        }),
      });
      if (response.ok) {
        const id = (await response.json()).data?.[0]?.id ?? "exists";
        this.sql.exec("UPDATE tw SET sub_id = ?, state = 'ok' WHERE login = ?", id, row.login);
      } else if (response.status === 409) {
        // already there, maybe signed with an older secret: removed, and made again next time
        await this.removeTwitchSubscriptions(row.user_id, origin, budget);
        this.sql.exec("UPDATE tw SET asked_at = 0 WHERE login = ?", row.login);
      } else {
        console.warn("EventSub", row.login, response.status, await response.text());
        this.sql.exec("UPDATE tw SET asked_at = ? WHERE login = ?", now, row.login);
      }
    }
  }
  /**
   * Every few hours, asks Twitch which of our subscriptions really work: one that Twitch turned
   * off (too many failed deliveries, a revocation that never reached us...) still looked fine
   * here, and that channel's streams were never told. Those are made again.
   */
  async checkTwitchSubscriptions(origin, budget) {
    if (this.get("tw_check") || budget.left < 4) return;
    const subs = [];
    let cursor = "";
    for (let page = 0; page < 3 && budget.left > 2; page++) {
      budget.left--;
      const response = await this.helix(
        "eventsub/subscriptions?type=stream.online" + (cursor ? "&after=" + encodeURIComponent(cursor) : ""),
      );
      if (!response.ok) {
        console.warn("EventSub list", response.status, await response.text());
        return;
      }
      const body = await response.json();
      subs.push(...(body.data ?? []).filter((sub) => sub.transport?.callback?.startsWith(origin)));
      cursor = body.pagination?.cursor ?? "";
      if (!cursor) break;
    }
    const enabledByUser = new Map(
      subs.filter((sub) => sub.status === "enabled").map((sub) => [sub.condition?.broadcaster_user_id, sub.id]),
    );
    const others = {};
    for (const sub of subs) {
      if (sub.status !== "enabled") others[sub.status] = (others[sub.status] ?? 0) + 1;
    }
    let fixed = 0;
    for (const row of this.sql.exec("SELECT login, user_id, sub_id FROM tw WHERE sub_id IS NOT NULL").toArray()) {
      const working = enabledByUser.get(row.user_id);
      if (working) {
        if (working !== row.sub_id) this.sql.exec("UPDATE tw SET sub_id = ? WHERE login = ?", working, row.login);
        continue;
      }
      // made again in this same maintenance (the broken one is removed first if it is there)
      console.warn("EventSub not working for", row.login);
      this.sql.exec("UPDATE tw SET sub_id = NULL, state = 'new', asked_at = 0 WHERE login = ?", row.login);
      fixed++;
    }
    this.put(
      "tw_check_info",
      JSON.stringify({ at: new Date().toISOString(), enabled: enabledByUser.size, notEnabled: others, remade: fixed }),
    );
    this.put("tw_check", "1", Date.now() + TWITCH_CHECK_MS);
  }

  async removeTwitchSubscriptions(userId, origin, budget) {
    if (budget.left <= 0) return;
    budget.left--;
    const response = await this.helix("eventsub/subscriptions?user_id=" + encodeURIComponent(userId));
    if (!response.ok) return;
    for (const sub of (await response.json()).data ?? []) {
      if (sub.type !== "stream.online" || !sub.transport?.callback?.startsWith(origin)) continue;
      if (budget.left-- <= 0) return;
      await this.helix("eventsub/subscriptions?id=" + encodeURIComponent(sub.id), { method: "DELETE" });
    }
  }
  // endregion
}
