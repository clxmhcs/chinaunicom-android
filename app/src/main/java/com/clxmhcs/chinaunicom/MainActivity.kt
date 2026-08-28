package com.clxmhcs.chinaunicom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.clxmhcs.chinaunicom.automation.AutomationCoordinator
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomTheme
import com.clxmhcs.chinaunicom.ui.ChinaUnicomApp

class MainActivity : ComponentActivity() {
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
}
