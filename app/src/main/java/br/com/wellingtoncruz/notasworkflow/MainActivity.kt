// NotasWorkflow Android - MainActivity.kt
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import br.com.wellingtoncruz.notasworkflow.ui.NotasWorkflowApp
import br.com.wellingtoncruz.notasworkflow.ui.NotasWorkflowTheme

class MainActivity : ComponentActivity() {
	private val viewModel: AppViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			NotasWorkflowTheme {
				NotasWorkflowApp(viewModel)
			}
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.onResume()
	}

	override fun onStop() {
		viewModel.onStop()
		super.onStop()
	}
}
