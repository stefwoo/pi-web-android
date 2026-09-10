package dev.piweb.android.network

import dev.piweb.android.data.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PiWebClient(
    private var baseUrl: String = "",
    private var authToken: String? = null
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // For WebSocket
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    fun updateConfig(url: String, token: String?) {
        this.baseUrl = url.trimEnd('/')
        this.authToken = token?.takeIf { it.isNotBlank() }
    }

    private fun newRequestBuilder(path: String): Request.Builder {
        val builder = Request.Builder().url("$baseUrl/$path")
        authToken?.let {
            builder.addHeader("Authorization", "Bearer $it")
            // Also add CF-Access compatibility header if needed
            builder.addHeader("cf-access-token", it)
        }
        return builder
    }

    private fun encodeCwd(cwd: String?): String {
        return if (!cwd.isNullOrBlank()) "?cwd=${URLEncoder.encode(cwd, "UTF-8")}" else ""
    }

    // ==================== Session Management ====================

    fun listSessions(machineId: String = "local", callback: (List<SessionInfo>?, Exception?) -> Unit) {
        val request = newRequestBuilder("api/machines/$machineId/sessions").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null, e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    try {
                        val sessions = json.decodeFromString<List<SessionInfo>>(body)
                        callback(sessions, null)
                    } catch (e: Exception) {
                        callback(null, e)
                    }
                }
            }
        })
    }

    fun createSession(name: String? = null, cwd: String? = null, machineId: String = "local", callback: (SessionInfo?, Exception?) -> Unit) {
        val payload = json.encodeToString(SessionActionRequest(name = name)).toRequestBody(mediaTypeJson)
        val request = newRequestBuilder("api/machines/$machineId/sessions${encodeCwd(cwd)}")
            .post(payload)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null, e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    try {
                        val session = json.decodeFromString<SessionInfo>(body)
                        callback(session, null)
                    } catch (e: Exception) {
                        callback(null, e)
                    }
                }
            }
        })
    }

    fun deleteSession(sessionId: String, cwd: String? = null, machineId: String = "local", callback: (Boolean, Exception?) -> Unit) {
        val request = newRequestBuilder("api/machines/$machineId/sessions/$sessionId${encodeCwd(cwd)}")
            .delete()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(false, e)
            override fun onResponse(call: Call, response: Response) =
                response.use { callback(response.isSuccessful, null) }
        })
    }

    fun abortSession(sessionId: String, cwd: String? = null, machineId: String = "local", callback: (Boolean, Exception?) -> Unit) {
        val emptyBody = "".toRequestBody(null)
        val request = newRequestBuilder("api/machines/$machineId/sessions/$sessionId/abort${encodeCwd(cwd)}")
            .post(emptyBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(false, e)
            override fun onResponse(call: Call, response: Response) =
                response.use { callback(response.isSuccessful, null) }
        })
    }

    fun compactSession(sessionId: String, cwd: String? = null, machineId: String = "local", callback: (Boolean, Exception?) -> Unit) {
        val emptyBody = "".toRequestBody(null)
        val request = newRequestBuilder("api/machines/$machineId/sessions/$sessionId/compact${encodeCwd(cwd)}")
            .post(emptyBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(false, e)
            override fun onResponse(call: Call, response: Response) =
                response.use { callback(response.isSuccessful, null) }
        })
    }

    fun forkSession(sessionId: String, fromMessageId: String, cwd: String? = null, machineId: String = "local", callback: (SessionInfo?, Exception?) -> Unit) {
        val payload = json.encodeToString(SessionActionRequest(fromMessageId = fromMessageId)).toRequestBody(mediaTypeJson)
        val request = newRequestBuilder("api/machines/$machineId/sessions/$sessionId/fork${encodeCwd(cwd)}")
            .post(payload)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null, e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    try {
                        val session = json.decodeFromString<SessionInfo>(body)
                        callback(session, null)
                    } catch (e: Exception) {
                        callback(null, e)
                    }
                }
            }
        })
    }

    // ==================== Messages & Status ====================

    fun getMessages(sessionId: String, cwd: String?, machineId: String = "local", callback: (List<Message>?, Exception?) -> Unit) {
        val request = newRequestBuilder("api/machines/$machineId/sessions/$sessionId/messages${encodeCwd(cwd)}").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null, e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    try {
                        val messages = json.decodeFromString<List<Message>>(body)
                        callback(messages, null)
                    } catch (e: Exception) {
                        callback(null, e)
                    }
                }
            }
        })
    }

    fun getSessionStatus(sessionId: String, cwd: String?, machineId: String = "local", callback: (SessionStatus?, Exception?) -> Unit) {
        val request = newRequestBuilder("api/machines/$machineId/sessions/$sessionId/status${encodeCwd(cwd)}").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(null, e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    try {
                        val status = json.decodeFromString<SessionStatus>(body)
                        callback(status, null)
                    } catch (e: Exception) {
                        callback(null, e)
                    }
                }
            }
        })
    }

    fun sendPrompt(sessionId: String, cwd: String?, prompt: String, machineId: String = "local", callback: (Boolean, Exception?) -> Unit) {
        val body = json.encodeToString(PromptRequest(prompt)).toRequestBody(mediaTypeJson)
        val request = newRequestBuilder("api/machines/$machineId/sessions/$sessionId/prompt${encodeCwd(cwd)}")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(false, e)
            override fun onResponse(call: Call, response: Response) =
                response.use { callback(response.isSuccessful, null) }
        })
    }

    fun respondPermission(sessionId: String, requestId: String, approved: Boolean, reason: String? = null, cwd: String? = null, machineId: String = "local", callback: (Boolean, Exception?) -> Unit) {
        val body = json.encodeToString(PermissionResponse(requestId, approved, reason)).toRequestBody(mediaTypeJson)
        val request = newRequestBuilder("api/machines/$machineId/sessions/$sessionId/permissions${encodeCwd(cwd)}")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = callback(false, e)
            override fun onResponse(call: Call, response: Response) =
                response.use { callback(response.isSuccessful, null) }
        })
    }

    // ==================== WebSocket Real-time Stream ====================

    fun observeEvents(sessionId: String, cwd: String?, machineId: String = "local"): Flow<StreamEvent> = callbackFlow {
        val wsBase = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        val wsUrl = "$wsBase/api/machines/$machineId/sessions/$sessionId/events${encodeCwd(cwd)}"
        val request = Request.Builder().url(wsUrl).apply {
            authToken?.let { addHeader("Authorization", "Bearer $it") }
        }.build()

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(StreamEvent(type = "connected"))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = json.decodeFromString<StreamEvent>(text)
                    trySend(event)
                } catch (_: Exception) {
                    trySend(StreamEvent(type = "token", chunk = text))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                trySend(StreamEvent(type = "closing", error = reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(StreamEvent(type = "closed"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(StreamEvent(type = "error", error = t.localizedMessage ?: "WebSocket error"))
                close(t)
            }
        })

        awaitClose { webSocket.cancel() }
    }
}
