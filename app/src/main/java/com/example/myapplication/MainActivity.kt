package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ChatApplication
        setContent {
            AppRoot(
                repository = app.aiRepository,
                conversationRepository = app.conversationRepository,
                worldBookRepository = app.worldBookRepository,
                memoryRepository = app.memoryRepository,
                conversationSummaryRepository = app.conversationSummaryRepository,
                promptContextAssembler = app.promptContextAssembler,
                roleplayRepository = app.roleplayRepository,
                appUpdateRepository = app.appUpdateRepository,
                appUpdateDownloadController = app.appUpdateDownloadController,
            )
        }
    }
}
