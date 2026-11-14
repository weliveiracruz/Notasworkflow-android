// NotasWorkflow Android - App.kt
// The screen being shown, the spoken messages (snackbars, read by TalkBack) and the user manual.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import br.com.wellingtoncruz.notasworkflow.AppViewModel
import br.com.wellingtoncruz.notasworkflow.Screen
import br.com.wellingtoncruz.notasworkflow.core.Block
import br.com.wellingtoncruz.notasworkflow.core.Exporter
import br.com.wellingtoncruz.notasworkflow.core.Markup
import br.com.wellingtoncruz.notasworkflow.core.Run
import kotlinx.coroutines.launch

@Composable
fun NotasWorkflowApp(vm: AppViewModel) {
	val snackbar = remember { SnackbarHostState() }
	LaunchedEffect(Unit) {
		vm.messages.collect { text ->
			// A new message replaces the one being shown (TalkBack reads the newest).
			snackbar.currentSnackbarData?.dismiss()
			launch { snackbar.showSnackbar(text, duration = SnackbarDuration.Short) }
		}
	}
	when (val screen = vm.current) {
		is Screen.Main -> MainScreen(vm, snackbar)
		is Screen.NoteEditor -> NoteScreen(vm, screen, snackbar)
		is Screen.Organize -> OrganizeScreen(vm, snackbar)
		is Screen.SearchNotes -> SearchScreen(vm, snackbar)
		is Screen.Settings -> SettingsScreen(vm, snackbar)
		is Screen.Manual -> ManualScreen(vm, snackbar)
	}
}

/** Text of runs with bold, italic, strikethrough and code. */
fun runsText(runs: List<Run>): AnnotatedString = buildAnnotatedString {
	for (run in runs) {
		val style = SpanStyle(
			fontWeight = if ("bold" in run.marks) FontWeight.Bold else null,
			fontStyle = if ("italic" in run.marks) FontStyle.Italic else null,
			textDecoration = if ("strike" in run.marks) TextDecoration.LineThrough else null,
			fontFamily = if ("code" in run.marks) FontFamily.Monospace else null,
		)
		withStyle(style) { append(run.text) }
	}
}

/** The user manual (assets/manual.md), with real headings to move by with TalkBack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
	val context = LocalContext.current
	val blocks: List<Block> = remember {
		val text = try {
			context.assets.open("manual.md").use { String(it.readBytes(), Charsets.UTF_8) }
		} catch (e: Exception) {
			"O manual de uso não foi encontrado. Instale o aplicativo de novo."
		}
		Exporter.contentBlocks(Markup.textToHtml(text), noteLevel = 0)
	}
	BackHandler { vm.back() }
	Scaffold(
		snackbarHost = { SnackbarHost(snackbar) },
		topBar = {
			TopAppBar(
				colors = trelloTopBarColors(),
				title = { Text("Manual de uso") },
				navigationIcon = {
					IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Fechar") }
				},
			)
		},
	) { padding ->
		SelectionContainer {
			LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
				items(blocks) { block -> ManualBlock(block) }
			}
		}
	}
}

@Composable
private fun ManualBlock(block: Block) {
	when (block.kind) {
		"h" -> Text(
			runsText(block.runs),
			style = when (block.level) {
				1 -> MaterialTheme.typography.headlineSmall
				2 -> MaterialTheme.typography.titleLarge
				else -> MaterialTheme.typography.titleMedium
			},
			modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
		)
		"li" -> Text(
			buildAnnotatedString {
				append(if (block.ordered) "${block.number}. " else "• ")
				append(runsText(block.runs))
			},
			modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
		)
		"quote" -> Text(runsText(block.runs), fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
		"code" -> Text(block.text, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp))
		"hr" -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
		else -> Column { Text(runsText(block.runs), modifier = Modifier.padding(vertical = 4.dp)) }
	}
}
