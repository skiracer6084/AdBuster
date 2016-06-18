package app.adbuster

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.LayerDrawable
import android.net.VpnService
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast
import net.hockeyapp.android.CrashManager
import kotlinx.android.synthetic.main.form.*
import net.hockeyapp.android.CrashManagerListener
import net.hockeyapp.android.utils.Util

fun vpnStatusToToggleLevel(status: Int): Int = when(status) {
    VPN_STATUS_STOPPED -> 0
    VPN_STATUS_RUNNING -> 2
    else -> 1
}

fun vpnStatusShouldStop(status: Int): Boolean = when(status) {
    VPN_STATUS_STOPPED -> false
    else -> true
}

class MainActivity : Activity() {
    companion object {
        private val TAG = "MainActivity"
    }

    var mVpnServiceBroadcastReceiver : BroadcastReceiver? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.form)

        // Should we make sure the vpn service is started already based o the preferences?

        vpn_toggle.setOnClickListener {
            if (vpnStatusShouldStop(AdVpnService.vpnStatus)) {
                Log.i(TAG, "Attempting to disconnect")

                val intent = Intent(this, AdVpnService::class.java)
                intent.putExtra("COMMAND", Command.STOP.ordinal)
                startService(intent)
            } else {
                Log.i(TAG, "Attempting to connect")

                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 0)
                } else {
                    onActivityResult(0, RESULT_OK, null)
                }

            }
        }

        mVpnServiceBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val str_id = intent.getIntExtra(VPN_UPDATE_STATUS_EXTRA, R.string.notification_stopped)
                updateStatus(str_id)
            }
        }
    }

    private fun updateStatus(status: Int) {
        Log.i(TAG, "STATUS! " + status + " == " + if (AdVpnService.vpnStatus != VPN_STATUS_RUNNING) { 0 } else { 1 })
        text_status.text = getString(vpnStatusToTextId(status))
        val level = vpnStatusToToggleLevel(status)
        Log.i(TAG, "LEVEL! " + level)
        vpn_toggle.setImageLevel(level)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        if (result == RESULT_OK) {
            val intent = Intent(this, AdVpnService::class.java)
            intent.putExtra("COMMAND", Command.START.ordinal)
            intent.putExtra("NOTIFICATION_INTENT",
                PendingIntent.getActivity(this, 0,
                        Intent(this, MainActivity::class.java), 0))
            startService(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mVpnServiceBroadcastReceiver)
    }

    override fun onResume() {
        super.onResume()
        CrashManager.register(this, Util.getAppIdentifier(this), object: CrashManagerListener() {
            override fun onCrashesNotSent() {
                this@MainActivity.runOnUiThread({
                    Toast.makeText(this@MainActivity, "Failed to send crash data, will try again later.", Toast.LENGTH_LONG).show()
                })
            }

            override fun getMaxRetryAttempts() : Int = 3
        })

        updateStatus(AdVpnService.vpnStatus)
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mVpnServiceBroadcastReceiver, IntentFilter(VPN_UPDATE_STATUS_INTENT))
    }
}
