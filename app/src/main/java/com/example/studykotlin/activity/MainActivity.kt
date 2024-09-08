package com.example.studykotlin.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.studykotlin.activity.ConnectActivity
import androidx.appcompat.app.AppCompatDelegate
import android.app.Application
import com.example.studykotlin.ui.theme.ColorModeTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }

    @Composable
    fun MainScreen() {
        ColorModeTheme {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = {
                    val intent = Intent(this@MainActivity, ConnectActivity::class.java)
                    startActivity(intent)
                }) {
                    Text(text = "게임 시작", fontSize = 24.sp)
                }
            }

            BackHandler(onBack = { showExitConfirmationDialog() })
        }

    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setMessage("종료하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                finish()
            }
            .setNegativeButton("아니오", null)
            .show()
    }
}
