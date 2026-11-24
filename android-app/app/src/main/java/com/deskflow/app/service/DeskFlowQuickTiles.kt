package com.deskflow.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.deskflow.app.data.api.ApiClientFactory
import com.deskflow.app.data.api.FocusApiService
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.model.LedControlRequest
import com.deskflow.app.data.model.TimerStartRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shared helper for (#82) Android Quick Settings Tiles so API construction
 * and tile state updates aren't duplicated across tile services.
 */
internal object DeskFlowTileHelper {
    fun getApiOrNull(service: TileService): FocusApiService? {
        val prefs = SecurePreferences(service)
        if (prefs.deviceToken.isNullOrBlank()) return null
        return ApiClientFactory.create(prefs)
    }
}

class DeskFlowLampTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "DeskFlow LED"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val nextActive = tile.state != Tile.STATE_ACTIVE
        tile.state = if (nextActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()

        scope.launch {
            runCatching {
                val api = DeskFlowTileHelper.getApiOrNull(this@DeskFlowLampTileService) ?: return@runCatching
                api.controlLed(LedControlRequest("toggle"))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class DeskFlowFocusTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Focus 25m"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()

        scope.launch {
            runCatching {
                val api = DeskFlowTileHelper.getApiOrNull(this@DeskFlowFocusTileService) ?: return@runCatching
                api.startTimer(TimerStartRequest(duration = "25m", note = "Quick Settings Focus"))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
