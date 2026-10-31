package org.schabi.newpipe.videoprime.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.BuildConfig

/**
 * PulsePlay: the sync routes of our own server (the one that sends the instant notifications,
 * see push-server/src/index.js). Blocking calls: never on the main thread.
 */
object SyncApi {
    private const val TIMEOUT_MILLIS = 20_000

    /** The server answered with an error; [code] 401 means this device is not in a group any more. */
    class HttpException(val code: Int, message: String) : IOException(message)

    data class Linked(val group: String, val token: String, val device: String, val topic: String)

    data class Item(val kind: String, val key: String, val value: JSONObject?, val version: Long, val device: String)

    data class Page(val version: Long, val more: Boolean, val items: List<Item>)

    data class Device(val id: String, val name: String, val lastSeen: Long, val me: Boolean)

    fun isConfigured(): Boolean = BuildConfig.PUSH_SERVER_URL.isNotEmpty()

    fun create(deviceName: String): Linked = linked(post("create", JSONObject().put("device", deviceName), null))

    fun join(code: String, deviceName: String): Linked = linked(post("join", JSONObject().put("code", code).put("device", deviceName), null))

    /** @return a code to type on another device, valid for 10 minutes, and when it ends */
    fun code(token: String): Pair<String, Long> {
        val answer = post("code", JSONObject(), token)
        return answer.getString("code") to answer.getLong("expiresAt")
    }

    /** @return the group's version after the change */
    fun push(token: String, items: List<Triple<String, String, JSONObject?>>, ping: Boolean): Long {
        val array = JSONArray()
        for ((kind, key, value) in items) {
            array.put(JSONObject().put("kind", kind).put("key", key).put("value", value ?: JSONObject.NULL))
        }
        return post("push", JSONObject().put("items", array).put("ping", ping), token).getLong("version")
    }

    fun pull(token: String, since: Long): Page {
        val answer = get("pull?since=$since", token)
        val array = answer.getJSONArray("items")
        val items = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Item(
                kind = item.getString("kind"),
                key = item.getString("key"),
                value = item.optJSONObject("value"),
                version = item.getLong("version"),
                device = item.optString("device")
            )
        }
        return Page(answer.getLong("version"), answer.optBoolean("more"), items)
    }

    fun devices(token: String): List<Device> {
        val array = get("devices", token).getJSONArray("devices")
        return (0 until array.length()).map { i ->
            val device = array.getJSONObject(i)
            Device(device.getString("id"), device.optString("name"), device.optLong("lastSeen"), device.optBoolean("me"))
        }
    }

    fun leave(token: String) {
        request("POST", "leave", JSONObject().toString(), token)
    }

    private fun linked(answer: JSONObject) = Linked(
        group = answer.getString("group"),
        token = answer.getString("token"),
        device = answer.getString("device"),
        topic = answer.getString("topic")
    )

    private fun post(route: String, body: JSONObject, token: String?): JSONObject = JSONObject(request("POST", route, body.toString(), token))

    private fun get(route: String, token: String): JSONObject = JSONObject(request("GET", route, null, token))

    private fun request(method: String, route: String, body: String?, token: String?): String {
        val connection = URL(BuildConfig.PUSH_SERVER_URL + "/sync/" + route).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = connection.errorStream?.use { String(it.readBytes()) }.orEmpty().take(200)
                throw HttpException(code, "Sync server answered $code: $message")
            }
            return connection.inputStream.use { String(it.readBytes()) }
        } finally {
            connection.disconnect()
        }
    }
}
