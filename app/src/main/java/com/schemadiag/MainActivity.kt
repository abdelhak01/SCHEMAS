package com.schemadiag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.schemadiag.ui.AppScreen
import com.schemadiag.ui.MainViewModel
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { AppScreen(vm) }
        }
    }
}
