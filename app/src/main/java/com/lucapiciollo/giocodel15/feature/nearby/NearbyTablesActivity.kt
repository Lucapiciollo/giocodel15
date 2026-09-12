package com.lucapiciollo.giocodel15.feature.nearby

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.applyNavigationBarBottomInset
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.core.ui.playEntranceAnimation
import com.lucapiciollo.giocodel15.databinding.ActivityNearbyTablesBinding
import com.lucapiciollo.giocodel15.databinding.ItemNearbyTableBinding
import com.lucapiciollo.giocodel15.feature.lobby.LobbyActivity
import com.lucapiciollo.giocodel15.multiplayer.nearby.DeviceIdentity
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyConnectionManager
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyPermissions
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbySession
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyTableAdvertisement
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage

class NearbyTablesActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    /** A discovered table, keyed by its stable [tableId] (from [NearbyTableAdvertisement]) rather
     * than the raw Nearby endpoint id, which can flap (a host's endpoint id may briefly change
     * across advertise/discover cycles) and used to cause duplicate/flickering rows for what is
     * really the same table. [endpointId] is mutable because the same table can be re-discovered
     * under a new endpoint id while [tableId] stays constant. */
    private data class DiscoveredTable(
        var endpointId: String,
        val tableId: String,
        val hostName: String,
        val row: ItemNearbyTableBinding
    )

    private lateinit var binding: ActivityNearbyTablesBinding
    private lateinit var nearby: NearbyConnectionManager
    private val tablesById = linkedMapOf<String, DiscoveredTable>()
    private val endpointToTableId = linkedMapOf<String, String>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            hideFriendlyError()
            nearby.startDiscovery()
        } else {
            showFriendlyError()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyTablesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopInset()
        binding.root.applyNavigationBarBottomInset()
        binding.root.playEntranceAnimation()

        // Defensive: discard any stale manager/listener from a previous discovery/hosting
        // attempt before starting a fresh one.
        NearbySession.reset()
        nearby = NearbySession.manager(this)
        nearby.setListener(this)
        startDiscoveryWhenAllowed()

        binding.cancelButton.setOnClickListener {
            nearby.stopDiscovery()
            finish()
        }
        binding.nearbyErrorRetryButton.setOnClickListener {
            startDiscoveryWhenAllowed()
        }
    }

    override fun onResume() {
        super.onResume()
        nearby.setListener(this)
    }

    override fun onEndpointFound(endpointId: String, endpointName: String) {
        if (isFinishing || isDestroyed) return

        val advertisement = NearbyTableAdvertisement.decode(endpointName)
        val tableId = advertisement?.tableId ?: endpointId
        val hostName = advertisement?.hostName ?: endpointName

        endpointToTableId[endpointId] = tableId
        val existing = tablesById[tableId]
        if (existing != null) {
            // Same table re-discovered under a different endpoint id: just repoint the join
            // action, don't add a second row for it.
            existing.endpointId = endpointId
            bindJoin(existing)
            return
        }

        val row = ItemNearbyTableBinding.inflate(layoutInflater, binding.tablesContainer, false)
        row.tableName.text = hostName
        val table = DiscoveredTable(endpointId, tableId, hostName, row)
        tablesById[tableId] = table
        bindJoin(table)
        binding.tablesContainer.addView(row.root)
        binding.nearbyStatus.text = getString(R.string.nearby_title)
        binding.rippleWave.isVisible = false
        hideFriendlyError()
    }

    private fun bindJoin(table: DiscoveredTable) {
        table.row.joinButton.isEnabled = true
        table.row.joinButton.setOnClickListener {
            table.row.joinButton.isEnabled = false
            nearby.requestConnection(DeviceIdentity.displayName(this), table.endpointId)
        }
    }

    override fun onEndpointLost(endpointId: String) {
        val tableId = endpointToTableId.remove(endpointId) ?: return
        val table = tablesById[tableId] ?: return
        // Ignore a stale "lost" callback for an endpoint id this table has already moved on from
        // (it was re-discovered under a newer endpoint id in the meantime).
        if (table.endpointId != endpointId) return

        tablesById.remove(tableId)
        binding.tablesContainer.removeView(table.row.root)
        if (tablesById.isEmpty()) {
            binding.nearbyStatus.setText(R.string.nearby_empty)
            binding.rippleWave.isVisible = true
        }
    }

    override fun onConnected(endpointId: String) {
        nearby.stopDiscovery()
        val tableId = endpointToTableId[endpointId]
        startActivity(
            Intent(this, LobbyActivity::class.java).apply {
                putExtra(LobbyActivity.EXTRA_IS_HOST, false)
                putExtra(LobbyActivity.EXTRA_HOST_ENDPOINT_ID, endpointId)
                if (tableId != null) putExtra(LobbyActivity.EXTRA_TABLE_ID, tableId)
            }
        )
        // Leave this screen behind instead of letting it sit under LobbyActivity in the back
        // stack (it used to stay there, so a guest's back press from Lobby could pop back to a
        // stale "searching for tables" screen instead of Home).
        finish()
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) = Unit

    override fun onError(message: String) {
        if (isFinishing || isDestroyed) return
        // Never surface raw Nearby/technical error strings to the player — log them for
        // debugging and show the friendly permission/connectivity card instead.
        Log.w(TAG, "Nearby error: $message")
        showFriendlyError()
        tablesById.values.forEach { it.row.joinButton.isEnabled = true }
    }

    private fun showFriendlyError() {
        binding.nearbyErrorCard.isVisible = true
        binding.rippleWave.isVisible = false
        binding.nearbyStatus.setText(R.string.nearby_permission_denied)
    }

    private fun hideFriendlyError() {
        binding.nearbyErrorCard.isVisible = false
    }

    private fun startDiscoveryWhenAllowed() {
        val required = NearbyPermissions.requiredRuntimePermissions()
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            hideFriendlyError()
            binding.nearbyStatus.setText(R.string.nearby_searching)
            binding.rippleWave.isVisible = true
            nearby.startDiscovery()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        nearby.clearListener(this)
        if (!isChangingConfigurations) nearby.stopDiscovery()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NearbyTablesActivity"
    }
}
