package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.GameAssets
import com.example.game.GameViewModel
import com.example.game.TrenchWarGameApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Pre-load all game assets synchronously on app launch to avoid race conditions
    GameAssets.loadAll(applicationContext)
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          val gameViewModel: GameViewModel = viewModel()
          TrenchWarGameApp(
            viewModel = gameViewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}
