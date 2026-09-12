package com.lucapiciollo.giocodel15.feature.nearby

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lucapiciollo.giocodel15.R
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
            nearby.startDiscovery()
        } else {
            binding.nearbyStatus.setText(R.string.nearby_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyTablesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nearby = NearbySession.manager(this)
        nearby.resetTransport()
        nearby.setListener(this)
        startDiscoveryWhenAllowed()
    }

    override fun onResume() {
        super.onResume()
        nearby.setListener(this)
    }

    override fun onEndpointFound(endpointId: String, endpointName: String) {
        val advertisement = NearbyTableAdvertisement.decode(endpointName)
        val tableId = advertisement?.tableId ?: endpointId
        val hostName = advertisement?.hostName ?: endpointName

        endpointToTableId[endpointId] = tableId
        val existing = tablesById[tableId]
        if (existing != null) {
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

        if (table.endpointId != endpointId) return

        tablesById.remove(tableId)
        binding.tablesContainer.removeView(table.row.root)
        if (tablesById.isEmpty()) binding.nearbyStatus.setText(R.string.nearby_empty)
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
        finish()
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) = Unit

    override fun onError(message: String) {
        if (isFinishing || isDestroyed) return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.nearbyStatus.setText(R.string.nearby_error)
        tablesById.values.forEach { it.row.joinButton.isEnabled = true }
    }

    private fun startDiscoveryWhenAllowed() {
        val required = NearbyPermissions.requiredRuntimePermissions()
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) nearby.startDiscovery() else permissionLauncher.launch(missing.toTypedArray())
    }

    override fun onDestroy() {
        nearby.clearListener(this)
        if (!isChangingConfigurations) nearby.stopDiscovery()
        super.onDestroy()
    }
}
