package org.siloserver.silo.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.siloserver.silo.network.createPlatformHttpClient

/**
 * Plain text-in, text-out HTTP for screens that read APIs loosely: the TV Server page reads
 * the Silo admin API (through the signed-in [silo] client) and the automation apps on the LAN
 * (through a plain client with no Silo auth attached). Every call returns null on failure.
 */
class RawHttpApi(private val silo: HttpClient) {
    private val plain: HttpClient by lazy { createPlatformHttpClient() }

    suspend fun siloGet(path: String): String? = runCatching {
        val r = silo.get(path)
        if (r.status.isSuccess()) r.bodyAsText() else null
    }.getOrNull()

    suspend fun siloPost(path: String, json: String = "{}"): Boolean = runCatching {
        silo.post(path) { contentType(ContentType.Application.Json); setBody(json) }.status.isSuccess()
    }.getOrDefault(false)

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String? = runCatching {
        val r = plain.get(url) { headers.forEach { (k, v) -> header(k, v) } }
        if (r.status.isSuccess()) r.bodyAsText() else null
    }.getOrNull()

    suspend fun post(url: String, json: String, headers: Map<String, String> = emptyMap()): Boolean = runCatching {
        plain.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            contentType(ContentType.Application.Json)
            setBody(json)
        }.status.isSuccess()
    }.getOrDefault(false)
}
