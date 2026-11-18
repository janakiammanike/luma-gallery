package com.luma.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luma.tvremote.ui.RemoteApp
import com.luma.tvremote.ui.RemoteViewModel
import com.luma.tvremote.ui.TVRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: RemoteViewModel = viewModel()
            val state by vm.ui.collectAsState()
            TVRemoteTheme(darkTheme = state.darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RemoteApp(vm)
                }
            }
        }
    }
}
