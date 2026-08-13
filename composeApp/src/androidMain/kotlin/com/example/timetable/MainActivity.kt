package com.example.timetable

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.timetable.platform.AppContextHolder
import com.example.timetable.platform.FilePickerBridge

class MainActivity : ComponentActivity() {

    private val openCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        FilePickerBridge.finish(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        FilePickerBridge.register { mimeTypes -> openCsv.launch(mimeTypes) }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            App()
        }
    }
}
