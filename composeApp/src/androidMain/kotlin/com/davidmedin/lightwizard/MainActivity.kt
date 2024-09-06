package com.davidmedin.lightwizard

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import AndroidPlatform
import Platform
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    val requestPermissionLauncher = registerForActivityResult( ActivityResultContracts.RequestPermission() ) {
        // This is some weird-ass syntax, but this is a callback argument for registerForActivityResult.
            isGranted : Boolean -> run {
            Log.i("Permission: ", "is $isGranted")
        }
    }

    // Setup Android Activity things.
    var platform : AndroidPlatform? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platform = AndroidPlatform(this)
        setContent {
            App(platform!!)
        }
    }

}

//@Preview
//@Composable
//fun AppAndroidPreview() {
//    val platform = AndroidPlatform(this.applicationContext)
//    App(platform)
//}