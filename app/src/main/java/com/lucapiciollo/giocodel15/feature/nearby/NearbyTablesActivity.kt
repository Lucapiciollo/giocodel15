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

        nearby = NearbySession.manager(this)
        nearby.listener = this
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
        binding.rippleWave.isVisible = false
        hideFriendlyError()
    }

    override fun onEndpointLost(endpointId: String) {
        endpointRows.remove(endpointId)?.let { binding.tablesContainer.removeView(it.root) }
        if (endpointRows.isEmpty()) {
            binding.nearbyStatus.setText(R.string.nearby_empty)
            binding.rippleWave.isVisible = true
        }
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
        // Never surface raw Nearby/technical error strings to the player — log them for
        // debugging and show the friendly permission/connectivity card instead.
        Log.w(TAG, "Nearby error: $message")
        showFriendlyError()
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
        nearby.stopDiscovery()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NearbyTablesActivity"
    }
}
