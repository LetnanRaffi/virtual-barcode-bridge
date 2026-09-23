package com.raffitech.scanbridge.network

import com.raffitech.scanbridge.model.ConnectionState
import com.raffitech.scanbridge.model.ScanDelivery
import com.raffitech.scanbridge.model.TransportType
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class BridgeSocket {
    companion object { const val USB_URL = "ws://127.0.0.1:8080/ws" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private val lock = Any()
    private val pending = LinkedHashMap<String, String>()
    private var socket: WebSocket? = null
    private var endpoint: String? = null
    private var transport = TransportType.WIFI
    private var retry: Job? = null
    private var generation = 0
    private var everConnected = false
    private var stopped = false

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state = _state.asStateFlow()
    private val _delivery = MutableSharedFlow<ScanDelivery>(extraBufferCapacity = 32)
    val delivery = _delivery.asSharedFlow()

    fun connect(url: String, mode: TransportType) {
        synchronized(lock) {
            retry?.cancel()
            stopped = false
            endpoint = url
            transport = mode
            everConnected = false
            generation++
            socket?.close(1000, "switch connection")
            socket = null
            _state.value = ConnectionState.Connecting
            openLocked(generation)
        }
    }

    private fun openLocked(epoch: Int) {
        val url = endpoint ?: return
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (epoch == generation && socket === webSocket) _state.value = ConnectionState.ComputerFound
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (json.optString("type")) {
                    "hello" -> synchronized(lock) {
                        if (epoch != generation || socket !== webSocket || json.optString("app") != "vbb") return@synchronized
                        everConnected = true
                        _state.value = ConnectionState.Connected(
                            json.optString("name").ifBlank { "Komputer" }, transport,
                        )
                        pending.forEach { (id, value) -> transmit(webSocket, id, value) }
                    }
                    "barcode_ack" -> {
                        val id = json.optString("id")
                        val value = synchronized(lock) {
                            if (epoch != generation || socket !== webSocket) null else pending.remove(id)
                        } ?: return
                        _delivery.tryEmit(ScanDelivery(id, value, json.optBoolean("success"), json.optString("error")))
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                lost(epoch, webSocket, t.message ?: "Koneksi gagal")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                lost(epoch, webSocket, if (reason.isBlank()) "Koneksi terputus" else reason)
            }
        })
        scope.launch {
            delay(5000)
            synchronized(lock) {
                if (epoch == generation && (_state.value is ConnectionState.Connecting || _state.value is ConnectionState.ComputerFound)) {
                    socket?.cancel()
                    lost(epoch, socket, "Komputer tidak merespons")
                }
            }
        }
    }

    private fun lost(epoch: Int, source: WebSocket?, message: String) {
        synchronized(lock) {
            if (epoch != generation || stopped || socket !== source) return
            socket = null
            _state.value = ConnectionState.Error(message)
            if (everConnected) {
                retry?.cancel()
                retry = scope.launch {
                    delay(2000)
                    synchronized(lock) {
                        if (epoch == generation && !stopped) {
                            _state.value = ConnectionState.Connecting
                            openLocked(epoch)
                        }
                    }
                }
            }
        }
    }

    fun send(value: String): String? {
        synchronized(lock) {
            if (endpoint == null || pending.size >= 20) return null
            val id = UUID.randomUUID().toString()
            pending[id] = value
            if (_state.value is ConnectionState.Connected) socket?.let { transmit(it, id, value) }
            return id
        }
    }

    private fun transmit(webSocket: WebSocket, id: String, value: String) {
        val payload = JSONObject()
            .put("type", "barcode")
            .put("id", id)
            .put("value", value)
            .put("timestamp", System.currentTimeMillis())
            .put("auto_enter", true)
        webSocket.send(payload.toString())
        scope.launch {
            delay(7000)
            synchronized(lock) {
                if (pending[id] == value && _state.value is ConnectionState.Connected && socket === webSocket) {
                    transmit(webSocket, id, value)
                }
            }
        }
    }

    fun disconnect() {
        synchronized(lock) {
            stopped = true
            generation++
            retry?.cancel()
            socket?.close(1000, "disconnect")
            socket = null
            endpoint = null
            pending.clear()
            _state.value = ConnectionState.Disconnected
        }
    }
}
