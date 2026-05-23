package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.example.ui.AppLockMainScreen
import com.example.ui.AppLockViewModel
import com.example.ui.BiometricAuthHelper
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {
  private val viewModel: AppLockViewModel by viewModels()
  private lateinit var biometricHelper: BiometricAuthHelper

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    biometricHelper = BiometricAuthHelper(this)

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize()
        ) {
          AppLockMainScreen(
            viewModel = viewModel,
            biometricHelper = biometricHelper,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }
}
