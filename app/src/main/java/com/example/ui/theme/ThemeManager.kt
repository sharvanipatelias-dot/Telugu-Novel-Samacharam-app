package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeManager {
    var isDarkTheme by mutableStateOf(false)
    var currentLanguage by mutableStateOf("TE") // "TE" for Telugu, "EN" for English
}
