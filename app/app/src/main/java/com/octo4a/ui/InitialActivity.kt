package com.octo4a.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.octo4a.R
import com.octo4a.camera.CameraEnumerationRepository
import com.octo4a.camera.CameraService
import com.octo4a.camera.LegacyCameraService
import com.octo4a.repository.BootstrapRepository
import com.octo4a.service.OctoPrintService
import com.octo4a.utils.isServiceRunning
import com.octo4a.utils.preferences.MainPreferences
import kotlinx.android.synthetic.main.activity_landing.*
import org.koin.android.ext.android.inject


class InitialActivity: AppCompatActivity() {
    private val bootstrapRepository: BootstrapRepository by inject()
    private val prefs: MainPreferences by inject()
    private val cameraEnumerationRepository: CameraEnumerationRepository by inject()

    // Storage permission request
    private val hasStoragePermission: Boolean
        get() = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted ->
            if (isGranted.values.all { it }) {
                startApp()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.activity_landing)

        if (bootstrapRepository.isBootstrapInstalled) {
            checkWritePermissionAndRun()
        }

        cameraEnumerationRepository.enumerateCameras()
        installButton.setOnClickListener {
            if (!isNetworkConnected()) {
                Toast.makeText(this, getString(R.string.missing_network), Toast.LENGTH_LONG).show()
            } else {
                checkWritePermissionAndRun()
            }
        }
    }

    private fun checkWritePermissionAndRun() {
        if (!hasStoragePermission) {
            requestStoragePermission.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
            Toast.makeText(this, getString(R.string.missing_write_permission), Toast.LENGTH_LONG).show()
        }
        else {
            startApp()
        }
    }

    private fun startApp() {
        if (!bootstrapRepository.isBootstrapInstalled) {
            startOctoService()
            val intent = Intent(this, InstallationActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            startOctoService()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private val cameraServerRunning by lazy {
        isServiceRunning(CameraService::class.java) || isServiceRunning(
            LegacyCameraService::class.java
        )
    }

    private fun isNetworkConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo != null && cm.activeNetworkInfo.isConnected
    }

    private fun startOctoService() {
        if (!isServiceRunning(OctoPrintService::class.java)) {
            val intent = Intent(this, OctoPrintService::class.java)
            startService(intent)
        }

        if (!cameraServerRunning && prefs.enableCameraServer) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val intent = Intent(this, CameraService::class.java)
                startService(intent)
            } else {
                val intent = Intent(this, LegacyCameraService::class.java)
                startService(intent)
            }
        }
    }
}