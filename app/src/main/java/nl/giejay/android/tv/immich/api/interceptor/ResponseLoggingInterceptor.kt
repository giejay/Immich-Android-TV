package nl.giejay.android.tv.immich.api.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.GzipSource
import okio.Okio
import okio.buffer
import timber.log.Timber

// https://gist.github.com/erickok/e371a9e0b9e702ed441d
class ResponseLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        var response: Response = chain.proceed(request)
        var content: String? = null
        response.body()?.let {
            val contentType: MediaType? = it.contentType()
            val contentEncoding: String? = response.header(CONTENT_ENCODING)
            if ("gzip" == contentEncoding) {
                val buffer: BufferedSource = GzipSource(it.source()).buffer()
                content = buffer.readUtf8()
                val wrappedBody: ResponseBody = ResponseBody.create(contentType, content)
                response =
                    response.newBuilder().removeHeader(CONTENT_ENCODING).body(wrappedBody).build()
            } else {
                content = it.string()
                val wrappedBody: ResponseBody = ResponseBody.create(contentType, content)
                response = response.newBuilder().body(wrappedBody).build()
            }
        }
        var protocol: String = response.protocol().name.replaceFirst("_", "/")
        protocol = protocol.replace('_', '.')
        val httpLine = "" + protocol + ' ' + response.code()
        Timber.d("${request.url()}\n${httpLine}\n${response.headers()}\n\n${content?.take(500)}")
        return response
    }

    companion object {
        const val CONTENT_ENCODING = "content-encoding"
    }
}