import assert from "node:assert/strict";
import { test } from "node:test";
import {
  fcmMessage,
  hmacHex,
  isFreshTimestamp,
  isRecent,
  parseRegistration,
  parseYouTubeAtom,
  serviceAccountJwt,
  verifyEventSub,
  verifyWebSub,
} from "../src/lib.js";

const ATOM = `<?xml version='1.0' encoding='UTF-8'?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
 <link rel="hub" href="https://pubsubhubbub.appspot.com"/>
 <title>YouTube video feed</title>
 <entry>
  <id>yt:video:dQw4w9WgXcQ</id>
  <yt:videoId>dQw4w9WgXcQ</yt:videoId>
  <yt:channelId>UCuAXFkgsw1L7xaCfnd5JJOw</yt:channelId>
  <title>Rock &amp; Roll &#233;xito &lt;3</title>
  <link rel="alternate" href="https://www.youtube.com/watch?v=dQw4w9WgXcQ"/>
  <author>
   <name>Rick Astley</name>
   <uri>https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw</uri>
  </author>
  <published>2026-10-22T10:00:00+00:00</published>
  <updated>2026-10-22T10:00:05.123+00:00</updated>
 </entry>
</feed>`;

test("parses a WebSub notification", () => {
  assert.deepEqual(parseYouTubeAtom(ATOM), [
    {
      videoId: "dQw4w9WgXcQ",
      channelId: "UCuAXFkgsw1L7xaCfnd5JJOw",
      title: "Rock & Roll éxito <3",
      author: "Rick Astley",
      published: "2026-10-22T10:00:00+00:00",
    },
  ]);
});

test("a deleted video gives nothing", () => {
  const deleted = `<feed xmlns:at="http://purl.org/atompub/tombstones/1.0"><at:deleted-entry ref="yt:video:x" when="2026-10-22T10:00:00+00:00"/></feed>`;
  assert.deepEqual(parseYouTubeAtom(deleted), []);
});

test("only recent videos are news", () => {
  const now = Date.parse("2026-10-22T12:00:00Z");
  assert.equal(isRecent("2026-10-22T10:00:00+00:00", now), true);
  assert.equal(isRecent("2026-10-10T10:00:00+00:00", now), false);
  assert.equal(isRecent("not a date", now), false);
});

test("registration keeps only valid channels", () => {
  const parsed = parseRegistration({
    youtube: ["UCuAXFkgsw1L7xaCfnd5JJOw", "UCuAXFkgsw1L7xaCfnd5JJOw", "bad", 3, "UC/../x"],
    twitch: ["Ibai", "ibai", "a b", "x".repeat(30)],
  });
  assert.deepEqual(parsed, { youtube: ["UCuAXFkgsw1L7xaCfnd5JJOw"], twitch: ["ibai"] });
  assert.equal(parseRegistration(null), null);
  assert.equal(parseRegistration({ youtube: new Array(5000).fill("x") }), null);
});

test("WebSub signatures", async () => {
  const signature = "sha1=" + (await hmacHex("SHA-1", "secret", ATOM));
  assert.equal(await verifyWebSub("secret", ATOM, signature), true);
  assert.equal(await verifyWebSub("other", ATOM, signature), false);
  assert.equal(await verifyWebSub("secret", ATOM + " ", signature), false);
  assert.equal(await verifyWebSub("secret", ATOM, null), false);
});

test("EventSub signatures and replays", async () => {
  const id = "e0b7a2a0", at = "2026-10-22T10:00:00.123Z", body = '{"a":1}';
  const signature = "sha256=" + (await hmacHex("SHA-256", "secret", id + at + body));
  assert.equal(await verifyEventSub("secret", id, at, body, signature), true);
  assert.equal(await verifyEventSub("secret", id, at, body + "x", signature), false);
  assert.equal(await verifyEventSub("secret", null, at, body, signature), false);
  assert.equal(isFreshTimestamp(at, Date.parse(at) + 60_000), true);
  assert.equal(isFreshTimestamp(at, Date.parse(at) + 11 * 60_000), false);
});

test("service account JWT is a valid RS256 token", async () => {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const pkcs8 = Buffer.from(await crypto.subtle.exportKey("pkcs8", pair.privateKey)).toString("base64");
  const pem = `-----BEGIN PRIVATE KEY-----\n${pkcs8.match(/.{1,64}/g).join("\n")}\n-----END PRIVATE KEY-----\n`;
  const account = { client_email: "a@b.iam.gserviceaccount.com", private_key: pem, private_key_id: "k1" };
  const jwt = await serviceAccountJwt(account, "scope", Date.parse("2026-10-22T10:00:00Z"));
  const [h, c, s] = jwt.split(".");
  const claims = JSON.parse(Buffer.from(c, "base64url").toString());
  assert.equal(claims.iss, account.client_email);
  assert.equal(claims.aud, "https://oauth2.googleapis.com/token");
  assert.equal(claims.exp - claims.iat, 3600);
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    pair.publicKey,
    Buffer.from(s, "base64url"),
    new TextEncoder().encode(h + "." + c),
  );
  assert.equal(ok, true);
});

test("FCM message is data only and high priority", () => {
  assert.deepEqual(fcmMessage("yt_UC1", { t: "yt", n: null, x: 3 }, 60), {
    message: { topic: "yt_UC1", data: { t: "yt", n: "", x: "3" }, android: { priority: "HIGH", ttl: "60s" } },
  });
});
