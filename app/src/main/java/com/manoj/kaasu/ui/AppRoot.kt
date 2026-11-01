package com.manoj.kaasu.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import com.manoj.kaasu.ui.home.styles.HomeStyle
import com.manoj.kaasu.ui.home.styles.lookFor
import com.manoj.kaasu.ui.navigation.BarReservedHeight
import com.manoj.kaasu.ui.navigation.LiquidTabBar
import com.manoj.kaasu.ui.navigation.LocalBarInset
import com.manoj.kaasu.ui.navigation.tabIndex
import kotlin.math.sign
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.manoj.kaasu.ui.home.styles.AppBackdrop
import com.manoj.kaasu.ui.home.styles.HomeLook
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.manoj.kaasu.MainActivity
import com.manoj.kaasu.data.prefs.UserSettings
import com.manoj.kaasu.security.AppLockManager
import com.manoj.kaasu.ui.history.HistoryScreen
import com.manoj.kaasu.ui.history.TxnDetailScreen
import com.manoj.kaasu.ui.history.UntaggedScreen
import com.manoj.kaasu.ui.home.HomeScreen
import com.manoj.kaasu.ui.lock.LockScreen
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.onboarding.OnboardingScreen
import com.manoj.kaasu.ui.pay.PayScreen
import com.manoj.kaasu.ui.people.PeopleScreen
import com.manoj.kaasu.ui.people.PersonDetailScreen
import com.manoj.kaasu.ui.people.PersonEditScreen
import com.manoj.kaasu.ui.reports.ReportShareScreen
import com.manoj.kaasu.ui.reports.ReportsScreen
import com.manoj.kaasu.ui.scan.ScanScreen
import com.manoj.kaasu.ui.settings.CategoriesScreen
import com.manoj.kaasu.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppRoot(
    settings: UserSettings,
    lockManager: AppLockManager,
    incomingUpi: StateFlow<String?>,
    onIncomingHandled: () -> Unit,
) {
    val locked by lockManager.locked.collectAsStateWithLifecycle()
    val look = remember(settings.homeStyle) { lookFor(HomeStyle.from(settings.homeStyle)) }
    Box(Modifier.fillMaxSize()) {
        if (!settings.onboardingDone) {
            AppBackdrop(look)
            OnboardingScreen()
        } else {
            MainNavigation(look, incomingUpi, onIncomingHandled)
        }
        if (locked && settings.onboardingDone) {
            // The lock covers everything with the style's own live backdrop.
            Box(Modifier.fillMaxSize()) {
                AppBackdrop(look)
                LockScreen(biometricEnabled = settings.biometricEnabled)
            }
        }
    }
}

@Composable
private fun MainNavigation(look: HomeLook, incomingUpi: StateFlow<String?>, onIncomingHandled: () -> Unit) {
    val haze = remember { HazeState() }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val incoming by incomingUpi.collectAsStateWithLifecycle()

    LaunchedEffect(incoming) {
        val link = incoming ?: return@LaunchedEffect
        when (link) {
            MainActivity.OPEN_PREFIX + MainActivity.OPEN_SCAN -> navController.navigate(Routes.scan())
            MainActivity.OPEN_PREFIX + MainActivity.OPEN_PEOPLE -> navController.navigate(Routes.PICK_PERSON)
            MainActivity.OPEN_PREFIX + MainActivity.OPEN_HISTORY -> navController.navigateTopLevel(Routes.HISTORY)
            MainActivity.OPEN_PREFIX + MainActivity.OPEN_REPORTS -> navController.navigateTopLevel(Routes.REPORTS)
            else -> navController.navigate(Routes.pay(raw = link))
        }
        onIncomingHandled()
    }

    val onTopLevel = route in Routes.topLevel
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBarInset provides if (onTopLevel) BarReservedHeight + navInset else 0.dp) {
        // Everything inside this box is what the frosted tab bar blurs.
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
        AppBackdrop(look)
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val from = initialState.destination.route
                val to = targetState.destination.route
                if (from in Routes.topLevel && to in Routes.topLevel) {
                    // Tabs: a short glide in the direction of the tab, with a slight settle in scale.
                    val dir = sign((tabIndex(to) - tabIndex(from)).toFloat()).toInt()
                    slideInHorizontally(SlideSpring) { w -> dir * w / 6 } + fadeIn(FadeSpring) + scaleIn(ScaleSpring, initialScale = 0.97f)
                } else {
                    slideInHorizontally(PushSpring) { w -> w } + fadeIn(FadeSpring, initialAlpha = 0.3f)
                }
            },
            exitTransition = {
                val from = initialState.destination.route
                val to = targetState.destination.route
                if (from in Routes.topLevel && to in Routes.topLevel) {
                    val dir = sign((tabIndex(to) - tabIndex(from)).toFloat()).toInt()
                    slideOutHorizontally(SlideSpring) { w -> -dir * w / 6 } + fadeOut(FastFade) + scaleOut(ScaleSpring, targetScale = 0.97f)
                } else {
                    // iOS push: the old screen drifts back a third and fades under the new one.
                    slideOutHorizontally(PushSpring) { w -> -w / 3 } + fadeOut(FastFade) + scaleOut(ScaleSpring, targetScale = 0.96f)
                }
            },
            popEnterTransition = { slideInHorizontally(PushSpring) { w -> -w / 3 } + fadeIn(FadeSpring) + scaleIn(ScaleSpring, initialScale = 0.96f) },
            popExitTransition = { slideOutHorizontally(PushSpring) { w -> w } + fadeOut(FadeSpring) },
        ) {
            page(Routes.PROFILE) { com.manoj.kaasu.ui.profile.ProfileScreen(navController) }
            page(Routes.HOME) { HomeScreen(navController) }
            page(Routes.HISTORY) { HistoryScreen(navController) }
            page(Routes.PEOPLE) { PeopleScreen(navController) }
            page(Routes.REPORTS) { ReportsScreen(navController) }
            page(Routes.UNTAGGED) { UntaggedScreen(navController) }
            page(Routes.SETTINGS) { SettingsScreen(navController) }
            page(Routes.CATEGORIES) { CategoriesScreen(navController) }
            page(Routes.PICK_PERSON) { com.manoj.kaasu.ui.people.PickPersonScreen(navController) }
            page(
                Routes.SCAN,
                arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = Routes.SCAN_MODE_PAY }),
            ) { entry ->
                ScanScreen(navController, mode = entry.arguments?.getString("mode") ?: Routes.SCAN_MODE_PAY)
            }
            page(
                Routes.PAY,
                arguments = listOf(
                    navArgument("raw") { type = NavType.StringType; defaultValue = "" },
                    navArgument("contactId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("lend") { type = NavType.BoolType; defaultValue = false },
                    navArgument("amount") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { PayScreen(navController) }
            page(Routes.TXN, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                TxnDetailScreen(navController)
            }
            page(Routes.PERSON, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PersonDetailScreen(navController)
            }
            page(
                Routes.PERSON_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("vpa") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { PersonEditScreen(navController) }
            page(Routes.REPORT_SHARE, arguments = listOf(navArgument("week") { type = NavType.LongType })) {
                ReportShareScreen(navController)
            }
        }
        }
        }
        // The bar floats over the content and slides away (with the page) on inner screens.
        AnimatedVisibility(
            visible = onTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(BarSpring) { it } + fadeIn(FadeSpring),
            exit = slideOutVertically(BarSpring) { it } + fadeOut(FadeSpring),
        ) {
            LiquidTabBar(
                look = look,
                current = route,
                onSelect = { navController.navigateTopLevel(it) },
                onScan = { navController.navigate(Routes.scan()) },
                hazeState = haze,
            )
        }
    }
}

private val PushSpring = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)
private val SlideSpring = spring(dampingRatio = 0.86f, stiffness = 460f, visibilityThreshold = IntOffset.VisibilityThreshold)
private val BarSpring = spring(dampingRatio = 0.78f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)
private val FadeSpring = spring<Float>(stiffness = 500f)
private val FastFade = spring<Float>(stiffness = 1400f)
private val ScaleSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

/** Pages are transparent so the style's live wallpaper shows behind every screen. */
private fun NavGraphBuilder.page(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments = arguments) { entry ->
    Box(Modifier.fillMaxSize()) { content(entry) }
}

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

