package com.aiceviri.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.aiceviri.app.ui.AppRoot
import com.aiceviri.app.ui.theme.AiCeviriTheme

class MainActivity : ComponentActivity() {

    private val micGranted = mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            micGranted.value = result[Manifest.permission.RECORD_AUDIO] == true || hasMicPermission()
            app().router.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micGranted.value = hasMicPermission()
        askPermissions()
        setContent {
            AiCeviriTheme {
                AppRoot(
                    micGranted = micGranted.value,
                    onRequestPermission = ::askPermissions,
                )
            }
        }
    }

    private fun app() = application as AiCeviriApp

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
