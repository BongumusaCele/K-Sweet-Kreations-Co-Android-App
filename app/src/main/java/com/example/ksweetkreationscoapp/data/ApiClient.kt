package com.example.ksweetkreationscoapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.ksweetkreationscoapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.util.UUID

class ApiException(val status: Int, message: String) : IOException(message)
data class Upload(val name: String, val mime: String, val bytes: ByteArray) {
    companion object {
        fun read(context: Context, uri: Uri, proof: Boolean): Upload {
            val resolver = context.contentResolver
            val originalName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "attachment"
            val limit = (if (proof) 5 else 2) * 1024 * 1024
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    require(out.size() + count <= limit) { "Choose a file up to ${if (proof) 5 else 2} MB." }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            } ?: throw IOException("The selected file could not be opened. Choose it again.")
            return validate(originalName, bytes, proof)
        }
        fun validate(originalName: String, bytes: ByteArray, proof: Boolean): Upload {
            require(bytes.isNotEmpty() && bytes.size <= (if (proof) 5 else 2) * 1024 * 1024) { "Choose a non-empty file up to ${if (proof) 5 else 2} MB." }
            val ext = originalName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val mime = when {
                ext in listOf("jpg", "jpeg") && bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
                ext == "png" && bytes.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
                ext == "webp" && bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                proof && ext == "pdf" && bytes.size >= 5 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-" -> "application/pdf"
                else -> throw IllegalArgumentException(if (proof) "Choose a valid PDF, JPG, PNG or WebP proof." else "Choose a valid JPG, PNG or WebP image.")
            }
            return Upload("attachment.$ext", mime, bytes)
        }
    }
}
data class Document(val bytes: ByteArray, val mime: String)

class ApiClient(context: Context, baseUrl: String = BuildConfig.API_BASE_URL) {
    private val base = URI(baseUrl.trimEnd('/') + "/")
    private val session = SecureSession(context.applicationContext)
    private var cookie: String? = session.read()
    private var epoch = 0L
    init {
        require(base.scheme == "https" || (BuildConfig.DEBUG && base.scheme == "http" && base.host in listOf("10.0.2.2", "127.0.0.1", "localhost"))) { "Use HTTPS for the bakery API." }
        require(base.userInfo == null && base.query == null && base.fragment == null)
    }
    @Synchronized fun clearSession() { epoch++; cookie = null; session.clear() }
    @Synchronized fun hasSession(): Boolean = cookie != null
    @Synchronized private fun sessionState(): Pair<Long, String?> = epoch to cookie
    private fun resolve(path: String): URI {
        val url = base.resolve(path)
        require(url.scheme == base.scheme && url.host == base.host && url.port == base.port) { "This file is outside the bakery service." }
        require(url.userInfo == null)
        return url
    }
    private fun connect(path: String, method: String): Pair<HttpURLConnection, Long> {
        val url = resolve(path)
        val (generation, activeCookie) = sessionState()
        val connection = url.toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        if (url.path.startsWith(base.path) && activeCookie != null) connection.setRequestProperty("Cookie", "ksk_session=$activeCookie")
        return connection to generation
    }
    @Synchronized private fun acceptCookie(connection: HttpURLConnection, generation: Long) {
        if (generation != epoch) return
        connection.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }.values.flatten().forEach { header ->
            HttpCookie.parse(header).filter { it.name == "ksk_session" }.forEach { value ->
                if (value.maxAge == 0L || value.value.isBlank()) { cookie = null; session.clear() }
                else { cookie = value.value; session.write(value.value) }
            }
        }
    }
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null,
                        fields: Map<String, String>? = null, upload: Upload? = null): JSONObject = withContext(Dispatchers.IO) {
        try {
            val (connection, generation) = connect(path, method)
            try {
                if (fields != null) {
                    val boundary = "ksweet-${UUID.randomUUID()}"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connection.outputStream.use { output ->
                        fun write(value: String) = output.write(value.toByteArray(Charsets.UTF_8))
                        fields.forEach { (name, value) ->
                            require(name.matches(Regex("[A-Za-z0-9]+")))
                            write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                        }
                        upload?.let {
                            write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${it.name}\"\r\nContent-Type: ${it.mime}\r\n\r\n")
                            output.write(it.bytes); write("\r\n")
                        }
                        write("--$boundary--\r\n")
                    }
                } else if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                acceptCookie(connection, generation)
                if (status !in 200..299) throw error(connection, status)
                if (status == 204) JSONObject() else connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            } finally { connection.disconnect() }
        } catch (e: ApiException) { throw e }
        catch (e: SocketTimeoutException) { throw IOException("The bakery service timed out. Check your connection and try again.", e) }
        catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IOException("Could not reach the bakery service. Check your connection and try again.", e) }
    }
    suspend fun document(path: String): Document = withContext(Dispatchers.IO) {
        val (connection, generation) = connect(path, "GET")
        try {
            val status = connection.responseCode
            acceptCookie(connection, generation)
            if (status !in 200..299) throw error(connection, status)
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    if (out.size() + count > 8 * 1024 * 1024) throw IOException("This document is too large to display.")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            Document(bytes, connection.contentType.orEmpty().substringBefore(';'))
        } finally { connection.disconnect() }
    }
    private fun error(connection: HttpURLConnection, status: Int): ApiException {
        val data = runCatching { connection.errorStream?.bufferedReader()?.use { JSONObject(it.readText()) } }.getOrNull()
        val validation = data?.optJSONObject("errors")?.let { obj -> obj.keys().asSequence().flatMap { key ->
            val value = obj.optJSONArray(key)
            (0 until (value?.length() ?: 0)).map { value!!.optString(it) }.asSequence()
        }.joinToString(" ") }
        val message = validation?.takeIf(String::isNotBlank) ?: data?.text("detail")?.takeIf(String::isNotBlank)
            ?: data?.text("message")?.takeIf(String::isNotBlank) ?: when (status) {
                401 -> "Your session has expired. Please sign in again."
                403 -> "You do not have permission to do this."
                404 -> "This record could not be found. Refresh and try again."
                409 -> "This record has changed. Refresh before trying again."
                413 -> "The selected file is too large."
                429 -> "Too many requests. Please wait a minute and try again."
                else -> "The service could not complete your request. Please try again."
            }
        return ApiException(status, message)
    }
}
