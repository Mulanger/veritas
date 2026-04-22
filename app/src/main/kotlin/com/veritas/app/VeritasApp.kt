@file:Suppress("FunctionNaming")

package com.veritas.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasType
import com.veritas.feature.history.HistoryScreen
import com.veritas.feature.home.HomeRecentMode
import com.veritas.feature.home.HomeRoute
import com.veritas.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

const val VERIFY_NAV_TAG = "bottom_nav_verify"
const val HISTORY_NAV_TAG = "bottom_nav_history"
const val ABOUT_NAV_TAG = "bottom_nav_about"
const val HISTORY_SCREEN_TAG = "screen_history"
const val SETTINGS_SCREEN_TAG = "screen_settings"

@Serializable
object HomeDestination

@Serializable
object HistoryDestination

@Serializable
object SettingsDestination

@Composable
fun VeritasApp(
    initialRecentMode: HomeRecentMode,
    enableHomeDevMenu: Boolean,
    onPickFile: () -> Unit,
    onPasteLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = HomeDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<HomeDestination> {
                    HomeRoute(
                        initialRecentMode = initialRecentMode,
                        enableDebugMenu = enableHomeDevMenu,
                        onPickFile = onPickFile,
                        onPasteLink = onPasteLink,
                        onOpenSettings = { navController.navigateToTopLevel(VeritasTopLevelDestination.About) },
                        onOpenRecent = { navController.navigateToTopLevel(VeritasTopLevelDestination.History) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<HistoryDestination> {
                    HistoryScreen(
                        onCheckSomething = {
                            navController.navigateToTopLevel(VeritasTopLevelDestination.Verify)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable<SettingsDestination> {
                    SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        VeritasBottomNav(
            currentDestination = currentDestination,
            onNavigate = navController::navigateToTopLevel,
        )
    }
}

private fun NavHostController.navigateToTopLevel(destination: VeritasTopLevelDestination) {
    when (destination) {
        VeritasTopLevelDestination.Verify -> navigate(HomeDestination, builder = topLevelNavOptions())
        VeritasTopLevelDestination.History -> navigate(HistoryDestination, builder = topLevelNavOptions())
        VeritasTopLevelDestination.About -> navigate(SettingsDestination, builder = topLevelNavOptions())
    }
}

private fun NavHostController.topLevelNavOptions(): androidx.navigation.NavOptionsBuilder.() -> Unit = {
    launchSingleTop = true
    restoreState = true
    popUpTo(graph.findStartDestination().id) {
        saveState = true
    }
}

@Composable
private fun VeritasBottomNav(
    currentDestination: NavDestination?,
    onNavigate: (VeritasTopLevelDestination) -> Unit,
) {
    HorizontalDivider(color = VeritasColors.line)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(VeritasColors.bg)
                .padding(top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VeritasTopLevelDestination.entries.forEach { destination ->
            val selected = currentDestination.hasTopLevelRoute(destination.route)
            BottomNavItem(
                label = destination.label,
                testTag = destination.testTag,
                glyph = destination.glyph,
                selected = selected,
                onClick = { onNavigate(destination) },
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    testTag: String,
    glyph: BottomNavGlyph,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) VeritasColors.accent else VeritasColors.inkMute

    Column(
        modifier =
            Modifier
                .testTag(testTag)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                )
                .padding(vertical = 4.dp)
                .widthIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BottomNavGlyphMark(
            glyph = glyph,
            tint = tint,
        )
        Text(
            text = label,
            style =
                VeritasType.monoXs.copy(
                    color = tint,
                    fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
                    letterSpacing = 0.1.em,
                ),
        )
    }
}

@Composable
private fun BottomNavGlyphMark(
    glyph: BottomNavGlyph,
    tint: Color,
) {
    val shape =
        when (glyph) {
            BottomNavGlyph.Circle -> CircleShape
            BottomNavGlyph.Square -> RoundedCornerShape(4.dp)
        }

    Box(
        modifier =
            Modifier
                .size(20.dp)
                .border(
                    width = 1.3.dp,
                    color = tint,
                    shape = shape,
                ),
    )
}

private fun NavDestination?.hasTopLevelRoute(route: KClass<out Any>): Boolean {
    var destination = this
    while (destination != null) {
        if (destination.hasRoute(route)) {
            return true
        }
        destination = destination.parent
    }
    return false
}

private enum class BottomNavGlyph {
    Square,
    Circle,
}

private enum class VeritasTopLevelDestination(
    val label: String,
    val testTag: String,
    val route: KClass<out Any>,
    val glyph: BottomNavGlyph,
) {
    Verify(
        label = "VERIFY",
        testTag = VERIFY_NAV_TAG,
        route = HomeDestination::class,
        glyph = BottomNavGlyph.Square,
    ),
    History(
        label = "HISTORY",
        testTag = HISTORY_NAV_TAG,
        route = HistoryDestination::class,
        glyph = BottomNavGlyph.Circle,
    ),
    About(
        label = "ABOUT",
        testTag = ABOUT_NAV_TAG,
        route = SettingsDestination::class,
        glyph = BottomNavGlyph.Square,
    ),
}
