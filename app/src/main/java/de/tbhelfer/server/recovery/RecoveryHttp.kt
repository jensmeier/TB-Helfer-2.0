package de.tbhelfer.server.recovery

import java.net.HttpURLConnection
import java.net.URL

/** Enforces recovery BEFORE outputStream can send a request body. No automatic retry. */
object RecoveryHttp {
    fun open(url: URL): HttpURLConnection = GuardedConnection(
        url.openConnection() as HttpURLConnection,
        RecoveryProtocol::prepare,
        RecoveryProtocol::observeResponse
    )
}

internal class GuardedConnection(
    private val delegate: HttpURLConnection,
    private val beforeIo: (HttpURLConnection) -> Unit,
    private val afterResponse: (HttpURLConnection, Int) -> Unit
) : HttpURLConnection(delegate.url) {
    private var prepared = false
    private fun prepare() {
        if (!prepared) {
            beforeIo(delegate)
            prepared = true
        }
    }
    override fun connect() { prepare(); delegate.connect() }
    override fun disconnect() = delegate.disconnect()
    override fun usingProxy() = delegate.usingProxy()
    override fun getResponseCode(): Int {
        prepare()
        val code = delegate.responseCode
        afterResponse(delegate, code)
        return code
    }
    override fun getResponseMessage(): String? { getResponseCode(); return delegate.responseMessage }
    override fun getInputStream(): java.io.InputStream { getResponseCode(); return delegate.inputStream }
    override fun getOutputStream(): java.io.OutputStream { prepare(); return delegate.outputStream }
    override fun getErrorStream(): java.io.InputStream? { getResponseCode(); return delegate.errorStream }
    override fun setRequestMethod(method: String) { delegate.requestMethod = method }
    override fun getRequestMethod(): String = delegate.requestMethod
    override fun setRequestProperty(key: String, value: String) = delegate.setRequestProperty(key, value)
    override fun addRequestProperty(key: String, value: String) = delegate.addRequestProperty(key, value)
    override fun getRequestProperty(key: String): String? = delegate.getRequestProperty(key)
    override fun getRequestProperties(): MutableMap<String, MutableList<String>> = delegate.requestProperties
    override fun setConnectTimeout(timeout: Int) { delegate.connectTimeout = timeout }
    override fun getConnectTimeout() = delegate.connectTimeout
    override fun setReadTimeout(timeout: Int) { delegate.readTimeout = timeout }
    override fun getReadTimeout() = delegate.readTimeout
    override fun setDoInput(value: Boolean) { delegate.doInput = value }
    override fun getDoInput() = delegate.doInput
    override fun setDoOutput(value: Boolean) { delegate.doOutput = value }
    override fun getDoOutput() = delegate.doOutput
    override fun setUseCaches(value: Boolean) { delegate.useCaches = value }
    override fun getUseCaches() = delegate.useCaches
    override fun setDefaultUseCaches(value: Boolean) { delegate.defaultUseCaches = value }
    override fun getDefaultUseCaches() = delegate.defaultUseCaches
    override fun setAllowUserInteraction(value: Boolean) { delegate.allowUserInteraction = value }
    override fun getAllowUserInteraction() = delegate.allowUserInteraction
    override fun setIfModifiedSince(value: Long) { delegate.ifModifiedSince = value }
    override fun getIfModifiedSince() = delegate.ifModifiedSince
    override fun setInstanceFollowRedirects(value: Boolean) { delegate.instanceFollowRedirects = value }
    override fun getInstanceFollowRedirects() = delegate.instanceFollowRedirects
    override fun setFixedLengthStreamingMode(length: Int) = delegate.setFixedLengthStreamingMode(length)
    override fun setFixedLengthStreamingMode(length: Long) = delegate.setFixedLengthStreamingMode(length)
    override fun setChunkedStreamingMode(length: Int) = delegate.setChunkedStreamingMode(length)
    override fun getHeaderField(name: String?): String? { getResponseCode(); return delegate.getHeaderField(name) }
    override fun getHeaderField(n: Int): String? { getResponseCode(); return delegate.getHeaderField(n) }
    override fun getHeaderFieldKey(n: Int): String? { getResponseCode(); return delegate.getHeaderFieldKey(n) }
    override fun getHeaderFields(): MutableMap<String, MutableList<String>> { getResponseCode(); return delegate.headerFields }
    override fun getContentLength(): Int { getResponseCode(); return delegate.contentLength }
    override fun getContentLengthLong(): Long { getResponseCode(); return delegate.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L }
    override fun getContentType(): String? { getResponseCode(); return delegate.contentType }
    override fun getContentEncoding(): String? { getResponseCode(); return delegate.contentEncoding }
}
