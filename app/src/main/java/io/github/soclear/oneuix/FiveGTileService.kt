package io.github.soclear.oneuix

import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

class FiveGTileService : TileService() {
    private var permissionsGranted = false
    private lateinit var telephonyManager: TelephonyManager


    override fun onCreate() {
        permissionsGranted = ensurePermissions()
        if (!permissionsGranted) {
            return
        }
        val subscriptionId = SubscriptionManager.getActiveDataSubscriptionId()
        telephonyManager = getSystemService(TelephonyManager::class.java).createForSubscriptionId(subscriptionId)
    }

    private fun ensurePermissions(): Boolean {
        val hasRead = checkSelfPermission(READ_PRIVILEGED_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasModify = checkSelfPermission(MODIFY_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        return if (hasRead && hasModify) {
            true
        } else {
            grantPermissions()
        }
    }

    private fun updateTileState() {
        val has5G = telephonyManager.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER).has5G()
        qsTile.state = if (has5G) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        val currentState = qsTile.state
        qsTile.state = Tile.STATE_UNAVAILABLE
        qsTile.updateTile()
        val allowedNetworkTypes = telephonyManager.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)
        if (currentState == Tile.STATE_ACTIVE && allowedNetworkTypes.has5G()) {
            val allowedNetworkTypesWithout5G = allowedNetworkTypes and TelephonyManager.NETWORK_TYPE_BITMASK_NR.inv()
            telephonyManager.setAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, allowedNetworkTypesWithout5G)
        } else if (currentState == Tile.STATE_INACTIVE && !allowedNetworkTypes.has5G()) {
            val allowedNetworkTypesWith5G = allowedNetworkTypes or TelephonyManager.NETWORK_TYPE_BITMASK_NR
            telephonyManager.setAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, allowedNetworkTypesWith5G)
        }
        updateTileState()
    }

    override fun onStartListening() {
        if (!permissionsGranted) {
            qsTile.state = Tile.STATE_UNAVAILABLE
            qsTile.updateTile()
            return
        }
        val activeDataSubscriptionId = SubscriptionManager.getActiveDataSubscriptionId()
        if (telephonyManager.subscriptionId != activeDataSubscriptionId) {
            telephonyManager = telephonyManager.createForSubscriptionId(activeDataSubscriptionId)
        }
        updateTileState()
    }

    companion object {
        const val READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE"
        const val MODIFY_PHONE_STATE = "android.permission.MODIFY_PHONE_STATE"
        private fun Long.has5G(): Boolean {
            return (this and TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0L
        }

        private fun grantPermissions(): Boolean {
            val grant = "su -c pm grant"
            val id = BuildConfig.APPLICATION_ID
            val command = "$grant $id $READ_PRIVILEGED_PHONE_STATE && $grant $id $MODIFY_PHONE_STATE"
            return try {
                val process = Runtime.getRuntime().exec(command)
                val exitCode = process.waitFor()
                exitCode == 0
            } catch (_: Throwable) {
                false
            }
        }
    }
}
