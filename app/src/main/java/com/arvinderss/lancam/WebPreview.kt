package com.arvinderss.lancam

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tiny LAN-only HTTP server that shows a low-res MJPEG preview of the live feed
 * in any browser: http://<phone-ip>:8080. Frames are only grabbed while at least
 * one browser is watching, so it costs nothing otherwise.
 */
class WebPreview(private val grab: (onFrame: (Bitmap) -> Unit) -> Unit) {

    @Volatile private var running = false
    private var server: ServerSocket? = null
    private val viewers = AtomicInteger()
    @Volatile private var frame: ByteArray? = null
    private val lock = java.lang.Object()
    private val pool = Executors.newCachedThreadPool()
    private val encoder = Executors.newSingleThreadExecutor()

    fun start() {
        if (running) return
        running = true
        val s = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) }
        server = s
        pool.execute {
            while (running) {
                val c = try { s.accept() } catch (_: IOException) { break }
                pool.execute { handle(c) }
            }
        }
        pool.execute { captureLoop() }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
        frame = null
        synchronized(lock) { lock.notifyAll() }
    }

    private fun captureLoop() {
        while (running) {
            if (viewers.get() > 0) grab { bmp -> encoder.execute { encode(bmp) } }
            try { Thread.sleep(1000L / FPS) } catch (_: InterruptedException) { break }
        }
    }

    private fun encode(bmp: Bitmap) {
        val h = bmp.height * WIDTH / bmp.width
        val scaled = Bitmap.createScaledBitmap(bmp, WIDTH, h, true)
        if (scaled !== bmp) bmp.recycle()
        val out = ByteArrayOutputStream(64 * 1024)
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        scaled.recycle()
        frame = out.toByteArray()
        synchronized(lock) { lock.notifyAll() }
    }

    private fun handle(c: Socket) = c.use {
        if (!LanUtils.isPrivate(c.inetAddress)) return@use
        c.soTimeout = 5000
        val reader = c.getInputStream().bufferedReader()
        val path = reader.readLine()?.split(" ")?.getOrNull(1) ?: return@use
        while (true) { val l = reader.readLine() ?: break; if (l.isEmpty()) break }
        c.soTimeout = 0
        val out = BufferedOutputStream(c.getOutputStream())
        try {
            when {
                path.startsWith("/stream") -> mjpeg(out)
                path == "/" || path.startsWith("/?") -> text(out, "200 OK", "text/html; charset=utf-8", PAGE)
                else -> text(out, "404 Not Found", "text/plain", "Not found")
            }
        } catch (_: IOException) { }
    }

    private fun text(out: OutputStream, status: String, type: String, body: String) {
        val bytes = body.toByteArray()
        out.write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()
    }

    private fun mjpeg(out: OutputStream) {
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
            "Cache-Control: no-cache\r\nConnection: close\r\n\r\n").toByteArray())
        out.flush()
        viewers.incrementAndGet()
        try {
            var last: ByteArray? = null
            while (running) {
                synchronized(lock) { if (frame === last) lock.wait(2000) }
                val f = frame ?: continue
                if (f === last) continue
                last = f
                out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.size}\r\n\r\n".toByteArray())
                out.write(f); out.write("\r\n".toByteArray()); out.flush()
            }
        } finally { viewers.decrementAndGet() }
    }

    companion object {
        const val PORT = 8080
        private const val FPS = 4
        private const val WIDTH = 960
        private const val QUALITY = 60
        private val PAGE = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LanCam preview</title><style>body{margin:0;background:#000;color:#ccc;font:14px sans-serif;text-align:center}
img{max-width:100%;max-height:92vh}</style></head><body><img src="/stream" alt="LanCam live preview">
<p>LanCam monitor preview (low-res, ~4 fps). The full-quality stream goes over RTSP.</p></body></html>"""
    }
}
