package com.raffitech.scanbridge.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raffitech.scanbridge.model.ScanSettings
import com.raffitech.scanbridge.model.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.scanBridgeStore by preferencesDataStore("scanbridge")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val continuous = booleanPreferencesKey("continuous")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val cooldown = intPreferencesKey("cooldown")
        val angledScan = booleanPreferencesKey("angled_scan")
        val lastUrl = stringPreferencesKey("last_url")
        val lastTransport = stringPreferencesKey("last_transport")
    }

    val settings: Flow<ScanSettings> = context.scanBridgeStore.data.map { p ->
        ScanSettings(
            continuous = p[Keys.continuous] ?: true,
            sound = p[Keys.sound] ?: true,
            vibration = p[Keys.vibration] ?: true,
            cooldownSeconds = (p[Keys.cooldown] ?: 2).coerceIn(1, 5),
            angledScan = p[Keys.angledScan] ?: false,
        )
    }

    val lastConnection: Flow<Pair<String, TransportType>?> = context.scanBridgeStore.data.map { p ->
        val url = p[Keys.lastUrl] ?: return@map null
        val transport = runCatching { TransportType.valueOf(p[Keys.lastTransport] ?: "") }.getOrNull()
            ?: return@map null
        url to transport
    }

    suspend fun saveConnection(url: String, transport: TransportType) {
        context.scanBridgeStore.edit { p ->
            p[Keys.lastUrl] = url
            p[Keys.lastTransport] = transport.name
        }
    }

    suspend fun clearConnection() {
        context.scanBridgeStore.edit { p ->
            p.remove(Keys.lastUrl)
            p.remove(Keys.lastTransport)
        }
    }

    suspend fun update(settings: ScanSettings) {
        context.scanBridgeStore.edit { p ->
            p[Keys.continuous] = settings.continuous
            p[Keys.sound] = settings.sound
            p[Keys.vibration] = settings.vibration
            p[Keys.cooldown] = settings.cooldownSeconds.coerceIn(1, 5)
            p[Keys.angledScan] = settings.angledScan
        }
    }
}
