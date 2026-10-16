package com.example.ksweetkreationscoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ksweetkreationscoapp.ui.BakeryApp
import com.example.ksweetkreationscoapp.ui.theme.KSweetKreationsCoAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KSweetKreationsCoAppTheme {
                BakeryApp(viewModel())
            }
        }
    }
}
