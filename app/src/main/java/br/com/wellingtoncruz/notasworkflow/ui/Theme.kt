// NotasWorkflow Android - Theme.kt
// Material 3 colours (the dynamic colours of the system when there are), light and dark.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Accent = Color(0xFF1F4E79)

@Composable
fun NotasWorkflowTheme(content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	val context = LocalContext.current
	val colors = when {
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
		dark -> darkColorScheme(primary = Color(0xFF9CC3EA))
		else -> lightColorScheme(primary = Accent)
	}
	MaterialTheme(colorScheme = colors, content = content)
}

/** Colour of a lane (the same as the add-on and the web app), used only as a small mark. */
fun laneColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
