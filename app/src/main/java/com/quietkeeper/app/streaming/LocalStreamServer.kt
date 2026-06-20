package com.quietkeeper.app.streaming

import android.content.Context
import android.util.Log
import com.quietkeeper.app.data.AppDatabase
import com.quietkeeper.app.data.NoiseEvent
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface

private const val TAG = "LocalStreamServer"

/**
 * Parse an HTTP `Range` request header into a concrete byte range over a resource of [total]
 * bytes. Pure and host-testable (no Android dependencies).
 *
 * Supported forms:
 *  - `bytes=start-end`  → start..end (end clamped to total-1)
 *  - `bytes=start-`     → start..total-1
 *  - `bytes=-suffix`    → last `suffix` bytes, i.e. (total-suffix)..total-1
 *
 * Returns null when [header] is null/blank, malformed, uses a unit other than `bytes`, or the
 * resulting range is empty/out of bounds.
 */
internal fun parseRange(header: String?, total: Long): LongRange? {
    if (header == null || total <= 0L) return null
    val trimmed = header.trim()
    val prefix = "bytes="
    if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
    // Only handle a single range (no comma-separated multi-range).
    val spec = trimmed.substring(prefix.length).trim()
    if (spec.isEmpty() || spec.contains(',')) return null
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val startStr = spec.substring(0, dash).trim()
    val endStr = spec.substring(dash + 1).trim()

    if (startStr.isEmpty()) {
        // Suffix form: bytes=-N → last N bytes.
        val suffix = endStr.toLongOrNull() ?: return null
        if (suffix <= 0L) return null
        val start = (total - suffix).coerceAtLeast(0L)
        return start..(total - 1)
    }

    val start = startStr.toLongOrNull() ?: return null
    if (start < 0L || start >= total) return null
    val end = if (endStr.isEmpty()) {
        total - 1
    } else {
        val e = endStr.toLongOrNull() ?: return null
        e.coerceAtMost(total - 1)
    }
    if (end < start) return null
    return start..end
}

/**
 * NanoHTTPD-based server that streams saved noise event WAVs to viewers on the same LAN.
 *
 * Routes:
 *  - `GET /`               → tiny HTML index linking to each event's audio.
 *  - `GET /events`         → JSON array of events (from Room).
 *  - `GET /audio/{file}`   → streams a WAV from the events dir, with HTTP Range support.
 *
 * Never crashes on a busy port: [startSafely] catches and logs.
 */
class LocalStreamServer(
    private val context: Context,
    port: Int = 8080,
) : NanoHTTPD(port) {

    private val eventsDir: File
        get() = context.getExternalFilesDir("events")
            ?: context.filesDir.resolve("events").also { it.mkdirs() }

    /** Start the server, swallowing failures (e.g. port already in use). Returns true on success. */
    fun startSafely(): Boolean = try {
        start(SOCKET_READ_TIMEOUT, false)
        val ip = wifiIpv4(context)
        Log.i(TAG, "serving on http://${ip ?: "<device-ip>"}:$listeningPort/")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "could not start streaming server on port $listeningPort", t)
        false
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri ?: "/"
            when {
                uri == "/" -> serveIndex()
                uri == "/events" -> serveEvents()
                uri.startsWith("/audio/") -> serveAudio(uri.removePrefix("/audio/"), session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "serve failed for ${session.uri}", t)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Error"
            )
        }
    }

    private fun loadEvents(): List<NoiseEvent> = runBlocking {
        // Running blocking inside a NanoHTTPD worker thread is acceptable here.
        AppDatabase.getInstance(context).noiseEventDao().getAll()
    }

    private fun serveIndex(): Response {
        val events = loadEvents()
        val sb = StringBuilder()
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
        sb.append("<title>QuietKeeper LAN Stream</title></head><body>")
        sb.append("<h1>QuietKeeper events</h1>")
        sb.append("<p><a href=\"/events\">events JSON</a></p>")
        if (events.isEmpty()) {
            sb.append("<p>No saved events yet.</p>")
        } else {
            sb.append("<ul>")
            for (e in events) {
                val file = File(e.wavPath).name
                sb.append("<li>#${e.id} ")
                    .append(String.format("%.1f", e.peakDb)).append(" dB ")
                    .append("<a href=\"/audio/").append(escape(file)).append("\">")
                    .append(escape(file)).append("</a></li>")
            }
            sb.append("</ul>")
        }
        sb.append("</body></html>")
        return newFixedLengthResponse(Response.Status.OK, "text/html", sb.toString())
    }

    private fun serveEvents(): Response {
        val arr = JSONArray()
        for (e in loadEvents()) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("timestamp", e.timestamp)
            o.put("peakDb", e.peakDb.toDouble())
            o.put("leq", e.leq.toDouble())
            o.put("tag", e.tag ?: JSONObject.NULL)
            o.put("address", e.address ?: JSONObject.NULL)
            o.put("wavFile", File(e.wavPath).name)
            arr.put(o)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", arr.toString())
    }

    private fun serveAudio(rawName: String, session: IHTTPSession): Response {
        // Path-traversal guard: only a bare filename, no separators or parent refs.
        if (rawName.isEmpty() ||
            rawName.contains('/') || rawName.contains('\\') || rawName.contains("..")
        ) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden"
            )
        }
        val file = File(eventsDir, rawName)
        // Belt-and-suspenders: resolved file must stay inside the events dir.
        if (!file.exists() || !file.isFile ||
            file.canonicalFile.parentFile != eventsDir.canonicalFile
        ) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found"
            )
        }

        val total = file.length()
        val range = parseRange(session.headers?.get("range"), total)
        val mime = "audio/wav"

        return if (range == null) {
            // Full content.
            val resp = newFixedLengthResponse(
                Response.Status.OK, mime, file.inputStream(), total
            )
            resp.addHeader("Accept-Ranges", "bytes")
            resp
        } else {
            // Partial content (206).
            val length = range.last - range.first + 1
            val raf = RandomAccessFile(file, "r")
            raf.seek(range.first)
            val stream = object : java.io.InputStream() {
                private var remaining = length
                override fun read(): Int {
                    if (remaining <= 0L) return -1
                    val b = raf.read()
                    if (b >= 0) remaining--
                    return b
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (remaining <= 0L) return -1
                    val toRead = minOf(len.toLong(), remaining).toInt()
                    val n = raf.read(b, off, toRead)
                    if (n > 0) remaining -= n
                    return n
                }

                override fun close() = raf.close()
            }
            val resp = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT, mime, stream, length
            )
            resp.addHeader("Accept-Ranges", "bytes")
            resp.addHeader("Content-Range", "bytes ${range.first}-${range.last}/$total")
            resp
        }
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        /**
         * The device's current WiFi IPv4 address (e.g. "192.168.1.42"), or null if not on WiFi
         * / no IPv4 found. Enumerates non-loopback network interfaces; usable to surface the
         * `http://<ip>:8080/` stream URL.
         */
        fun wifiIpv4(context: Context): String? {
            return try {
                val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
                for (iface in ifaces) {
                    if (!iface.isUp || iface.isLoopback) continue
                    val name = iface.name?.lowercase().orEmpty()
                    // Prefer WiFi-style interfaces; skip cellular/usb where obvious.
                    val likelyWifi = name.startsWith("wlan") || name.startsWith("ap") ||
                        name.startsWith("eth") || name.startsWith("en")
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress &&
                            addr.isSiteLocalAddress
                        ) {
                            if (likelyWifi) return addr.hostAddress
                        }
                    }
                }
                // Fallback: any site-local IPv4.
                for (iface in NetworkInterface.getNetworkInterfaces()) {
                    if (!iface.isUp || iface.isLoopback) continue
                    for (addr in iface.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress &&
                            addr.isSiteLocalAddress
                        ) {
                            return addr.hostAddress
                        }
                    }
                }
                null
            } catch (t: Throwable) {
                Log.w(TAG, "wifiIpv4 lookup failed", t)
                null
            }
        }
    }
}
