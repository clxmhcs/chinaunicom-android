package com.clxmhcs.chinaunicom

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.clxmhcs.chinaunicom.automation.AutomationCoordinator
import com.clxmhcs.chinaunicom.automation.NotificationPermissionCoordinator
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomTheme
import com.clxmhcs.chinaunicom.ui.ChinaUnicomApp

class MainActivity : ComponentActivity() {
    private var notificationPermissionRequested = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val notificationPermissionRequest: () -> Unit = {
        if (!notificationPermissionRequested && NotificationPermissionCoordinator.shouldRequest(this)) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutomationCoordinator.synchronize(this)
        enableEdgeToEdge()
        setContent {
            ChinaUnicomTheme {
                ChinaUnicomApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        NotificationPermissionCoordinator.attach(notificationPermissionRequest)
        notificationPermissionRequest()
    }

    override fun onStop() {
        NotificationPermissionCoordinator.detach(notificationPermissionRequest)
        super.onStop()
    }
}
