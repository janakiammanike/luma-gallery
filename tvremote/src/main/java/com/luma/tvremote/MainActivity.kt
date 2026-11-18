package com.luma.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.luma.tvremote.ui.RemoteApp
import com.luma.tvremote.ui.TVRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVRemoteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RemoteApp()
                }
            }
        }
    }
}
