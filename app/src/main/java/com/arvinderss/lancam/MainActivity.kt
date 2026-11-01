package com.arvinderss.lancam

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arvinderss.lancam.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var b: ActivityMainBinding
    private var svc: StreamService? = null
    private var surfaceReady = false
    private val prefs by lazy { getSharedPreferences("lancam", MODE_PRIVATE) }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        maybeAttach()
    }
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!hasCallRole()) { b.blockCalls.isChecked = false; toast("Call rejection needs the call-screening role") }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            svc = (binder as StreamService.LocalBinder).service.also { s ->
                s.onStatus = { runOnUiThread { render() } }
                val best = StreamConfig.PRESETS.indexOfFirst { s.isSupported(it) }
                if (best >= 0 && !prefs.contains("preset")) b.resolution.setSelection(best)
            }
            maybeAttach(); render()
        }
        override fun onServiceDisconnected(name: ComponentName) { svc = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        b.preview.holder.addCallback(this)

        b.resolution.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            StreamConfig.PRESETS.map { it.label })
        b.resolution.setSelection(prefs.getInt("preset", 0))
        b.useMic.isChecked = prefs.getBoolean("mic", false)   // default: video only
        b.dnd.isChecked = prefs.getBoolean("dnd", true)
        b.blockCalls.isChecked = prefs.getBoolean("calls", false)

        b.useMic.setOnCheckedChangeListener { _, on -> if (on) requestPerms() }
        b.dnd.setOnCheckedChangeListener { _, on ->
            if (on && !Dnd(this).hasAccess()) {
                toast("Allow LanCam to control Do Not Disturb, then come back")
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
        b.blockCalls.setOnCheckedChangeListener { _, on -> if (on && !hasCallRole()) requestCallRole() }
        b.toggle.setOnClickListener { toggle() }
        requestPerms()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, StreamService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        prefs.edit().putInt("preset", b.resolution.selectedItemPosition)
            .putBoolean("mic", b.useMic.isChecked).putBoolean("dnd", b.dnd.isChecked)
            .putBoolean("calls", b.blockCalls.isChecked).apply()
        svc?.onStatus = null
        svc?.detachPreview()
        unbindService(conn)
        svc = null
        super.onStop()
    }

    private fun toggle() {
        val s = svc ?: return
        if (s.isLive) { s.stop(); return }
        if (!granted(Manifest.permission.CAMERA)) { requestPerms(); return }
        if (b.useMic.isChecked && !granted(Manifest.permission.RECORD_AUDIO)) { requestPerms(); return }
        val useDnd = b.dnd.isChecked && Dnd(this).hasAccess()
        val block = b.blockCalls.isChecked && hasCallRole()
        val err = s.start(StreamConfig.PRESETS[b.resolution.selectedItemPosition], b.useMic.isChecked, useDnd, block)
        if (err != null) toast(err)
        render()
    }

    private fun render() {
        val live = svc?.isLive == true
        b.status.text = svc?.status ?: "Idle"
        b.toggle.text = if (live) "Stop" else "Go live"
        listOf(b.resolution, b.useMic, b.dnd, b.blockCalls).forEach { it.isEnabled = !live }
    }

    private fun maybeAttach() {
        if (surfaceReady && granted(Manifest.permission.CAMERA)) svc?.attachPreview(b.preview)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) { surfaceReady = true; maybeAttach() }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false; svc?.detachPreview() }

    private fun requestPerms() {
        val want = mutableListOf(Manifest.permission.CAMERA)
        if (b.useMic.isChecked) want += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33) want += Manifest.permission.POST_NOTIFICATIONS
        val missing = want.filterNot { granted(it) }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasCallRole() = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    private fun requestCallRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING))
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
