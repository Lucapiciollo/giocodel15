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
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage

class NearbyTablesActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    private lateinit var binding: ActivityNearbyTablesBinding
    private lateinit var nearby: NearbyConnectionManager
    private val endpointRows = linkedMapOf<String, ItemNearbyTableBinding>()

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
        nearby.listener = this
        startDiscoveryWhenAllowed()
    }

    override fun onResume() {
        super.onResume()
        nearby.listener = this
    }

    override fun onEndpointFound(endpointId: String, endpointName: String) {
        if (endpointRows.containsKey(endpointId)) return

        val row = ItemNearbyTableBinding.inflate(layoutInflater, binding.tablesContainer, false)
        row.tableName.text = endpointName
        row.joinButton.setOnClickListener {
            row.joinButton.isEnabled = false
            nearby.requestConnection(DeviceIdentity.displayName(this), endpointId)
        }
        endpointRows[endpointId] = row
        binding.tablesContainer.addView(row.root)
        binding.nearbyStatus.text = getString(R.string.nearby_title)
    }

    override fun onEndpointLost(endpointId: String) {
        endpointRows.remove(endpointId)?.let { binding.tablesContainer.removeView(it.root) }
        if (endpointRows.isEmpty()) binding.nearbyStatus.setText(R.string.nearby_empty)
    }

    override fun onConnected(endpointId: String) {
        nearby.stopDiscovery()
        startActivity(
            Intent(this, LobbyActivity::class.java).apply {
                putExtra(LobbyActivity.EXTRA_IS_HOST, false)
                putExtra(LobbyActivity.EXTRA_HOST_ENDPOINT_ID, endpointId)
            }
        )
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) = Unit

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.nearbyStatus.setText(R.string.nearby_error)
    }

    private fun startDiscoveryWhenAllowed() {
        val required = NearbyPermissions.requiredRuntimePermissions()
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) nearby.startDiscovery() else permissionLauncher.launch(missing.toTypedArray())
    }
}
