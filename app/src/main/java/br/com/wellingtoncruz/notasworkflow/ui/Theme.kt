// NotasWorkflow Android - Theme.kt
// The design tokens of the app, in the visual language of Trello (the Atlassian Design System
// palette): blue board, grey lists, white cards with a light shadow, the label colours for the
// lanes, the system font in the sizes of Trello, small rounded corners. Every screen takes its
// colours, shapes, spacing and type from here: nothing is hard-coded in the screens.
// Copyright (C) 2026 Wellington Cruz
// This file is covered by the GNU General Public License, version 2.

package br.com.wellingtoncruz.notasworkflow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colour tokens (names of the Atlassian Design System used by Trello). */
@Immutable
data class TrelloColors(
	/** The board behind the lists. */
	val board: Color,
	val onBoard: Color,
	/** Buttons over the board (translucent white). */
	val boardButton: Color,
	/** A lane (Trello list). */
	val list: Color,
	/** A note (Trello card). */
	val card: Color,
	val cardHovered: Color,
	/** color.text */
	val text: Color,
	/** color.text.subtle */
	val textSubtle: Color,
	/** color.text.subtlest */
	val textSubtlest: Color,
	/** color.background.brand.bold: the primary button. */
	val primary: Color,
	val onPrimary: Color,
	val topBar: Color,
	val onTopBar: Color,
	/** color.border */
	val border: Color,
	/** color.border.focused: the focus ring. */
	val focus: Color,
	val danger: Color,
	/** Label colours of Trello, one for each lane: blue, orange, red, green. */
	val laneTodo: Color,
	val laneInProgress: Color,
	val laneBlocked: Color,
	val laneDone: Color,
	/** Text over a label. */
	val onLabel: Color,
)

val LightTrello = TrelloColors(
	board = Color(0xFF0079BF),
	onBoard = Color(0xFFFFFFFF),
	boardButton = Color(0x33FFFFFF),
	list = Color(0xFFF1F2F4),
	card = Color(0xFFFFFFFF),
	cardHovered = Color(0xFFF7F8F9),
	text = Color(0xFF172B4D),
	textSubtle = Color(0xFF44546F),
	textSubtlest = Color(0xFF626F86),
	primary = Color(0xFF0C66E4),
	onPrimary = Color(0xFFFFFFFF),
	topBar = Color(0xFF026AA7),
	onTopBar = Color(0xFFFFFFFF),
	border = Color(0x24091E42),
	focus = Color(0xFF388BFF),
	danger = Color(0xFFC9372C),
	laneTodo = Color(0xFF579DFF),
	laneInProgress = Color(0xFFFEA362),
	laneBlocked = Color(0xFFF87168),
	laneDone = Color(0xFF4BCE97),
	onLabel = Color(0xFF172B4D),
)

val DarkTrello = TrelloColors(
	board = Color(0xFF09326C),
	onBoard = Color(0xFFDEE4EA),
	boardButton = Color(0x29A6C5E2),
	list = Color(0xFF101204),
	card = Color(0xFF22272B),
	cardHovered = Color(0xFF282E33),
	text = Color(0xFFB6C2CF),
	textSubtle = Color(0xFF9FADBC),
	textSubtlest = Color(0xFF8C9BAB),
	primary = Color(0xFF579DFF),
	onPrimary = Color(0xFF1D2125),
	topBar = Color(0xFF1D2125),
	onTopBar = Color(0xFFB6C2CF),
	border = Color(0x29A6C5E2),
	focus = Color(0xFF85B8FF),
	danger = Color(0xFFF87168),
	laneTodo = Color(0xFF0055CC),
	laneInProgress = Color(0xFFA54800),
	laneBlocked = Color(0xFFAE2E24),
	laneDone = Color(0xFF216E4E),
	onLabel = Color(0xFFFFFFFF),
)

private val LocalTrello = staticCompositionLocalOf { LightTrello }

/** The colour tokens of the current theme (light or dark). */
val trello: TrelloColors
	@Composable
	@ReadOnlyComposable
	get() = LocalTrello.current

/** Spacing, corners and elevation tokens. */
object Tokens {
	object Space {
		val s025 = 2.dp
		val s050 = 4.dp
		val s075 = 6.dp
		val s100 = 8.dp
		val s150 = 12.dp
		val s200 = 16.dp
		val s300 = 24.dp
		val s400 = 32.dp
	}

	object Radius {
		/** Buttons and fields (Trello uses 3 to 4 px). */
		val button = RoundedCornerShape(4.dp)
		/** Labels (the coloured bars of a card). */
		val label = RoundedCornerShape(4.dp)
		/** A card. */
		val card = RoundedCornerShape(8.dp)
		/** A list (lane), whole or split in top and bottom. */
		val list = RoundedCornerShape(12.dp)
		val listTop = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
		val listBottom = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
		val dialog = RoundedCornerShape(12.dp)
	}

	object Elevation {
		/** Shadow of a card (elevation.shadow.raised). */
		val card = 1.dp
		/** Shadow of a menu or of the create button (elevation.shadow.overlay). */
		val overlay = 6.dp
	}

	object Size {
		/** Smallest touch target. */
		val touch = 48.dp
		val labelWidth = 40.dp
		val labelHeight = 8.dp
	}
}

/** The type of Trello: the system font (Roboto on Android), 14 sp text, semibold titles. */
private val TrelloTypography = Typography(
	headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
	titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
	titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
	titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
	bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
	bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
	bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
	labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
	labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
	labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

private val TrelloShapes = Shapes(
	extraSmall = RoundedCornerShape(4.dp),
	small = RoundedCornerShape(4.dp),
	medium = RoundedCornerShape(8.dp),
	large = RoundedCornerShape(12.dp),
	extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun NotasWorkflowTheme(content: @Composable () -> Unit) {
	val colors = if (isSystemInDarkTheme()) DarkTrello else LightTrello
	val scheme = if (colors === DarkTrello) {
		darkColorScheme(
			primary = colors.primary, onPrimary = colors.onPrimary,
			secondary = colors.primary, onSecondary = colors.onPrimary,
			background = colors.card, onBackground = colors.text,
			surface = colors.card, onSurface = colors.text,
			surfaceVariant = colors.list, onSurfaceVariant = colors.textSubtle,
			surfaceContainerLowest = colors.list, surfaceContainerLow = colors.list, surfaceContainer = colors.card,
			surfaceContainerHigh = colors.card, surfaceContainerHighest = colors.cardHovered,
			outline = colors.textSubtlest, outlineVariant = colors.border,
			error = colors.danger,
		)
	} else {
		lightColorScheme(
			primary = colors.primary, onPrimary = colors.onPrimary,
			secondary = colors.primary, onSecondary = colors.onPrimary,
			background = colors.card, onBackground = colors.text,
			surface = colors.card, onSurface = colors.text,
			surfaceVariant = colors.list, onSurfaceVariant = colors.textSubtle,
			surfaceContainerLowest = colors.card, surfaceContainerLow = colors.list, surfaceContainer = colors.card,
			surfaceContainerHigh = colors.card, surfaceContainerHighest = colors.list,
			outline = colors.textSubtlest, outlineVariant = colors.border,
			error = colors.danger,
		)
	}
	CompositionLocalProvider(LocalTrello provides colors) {
		MaterialTheme(colorScheme = scheme, typography = TrelloTypography, shapes = TrelloShapes, content = content)
	}
}

/** The top bar of every screen: Trello's header blue (dark grey in the dark theme). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun trelloTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
	containerColor = trello.topBar,
	titleContentColor = trello.onTopBar,
	navigationIconContentColor = trello.onTopBar,
	actionIconContentColor = trello.onTopBar,
)

/** The colour of a lane: the Trello label colour (blue, orange, red, green). */
@Composable
@ReadOnlyComposable
fun laneColor(laneId: String): Color = when (laneId) {
	"inprogress" -> trello.laneInProgress
	"blocked" -> trello.laneBlocked
	"done" -> trello.laneDone
	else -> trello.laneTodo
}
