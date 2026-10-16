package com.stopfat.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.stopfat.app.ui.components.GlassToast
import com.stopfat.app.ui.components.glassSurface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stopfat.app.data.MealType
import com.stopfat.app.ui.components.LiquidBackground
import com.stopfat.app.ui.screens.AchievementsScreen
import com.stopfat.app.ui.screens.AnalysisScreen
import com.stopfat.app.ui.screens.BarcodeScreen
import com.stopfat.app.ui.screens.CoachScreen
import com.stopfat.app.ui.screens.CustomFoodScreen
import com.stopfat.app.ui.screens.FastingScreen
import com.stopfat.app.ui.screens.FoodLogScreen
import com.stopfat.app.ui.screens.FoodSearchScreen
import com.stopfat.app.ui.screens.FridgeScreen
import com.stopfat.app.ui.screens.HomeScreen
import com.stopfat.app.ui.screens.OnboardingScreen
import com.stopfat.app.ui.screens.PhotoScanScreen
import com.stopfat.app.ui.screens.ProgressScreen
import com.stopfat.app.ui.screens.RecipeDetailScreen
import com.stopfat.app.ui.screens.RecipesScreen
import com.stopfat.app.ui.screens.SettingsScreen
import com.stopfat.app.ui.screens.TextFoodScreen
import com.stopfat.app.ui.screens.WorkoutScreen
import com.stopfat.app.ui.screens.WorkoutSessionScreen
import com.stopfat.app.ui.theme.Glass

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ANALYSIS = "analysis"
    const val FASTING = "fasting"
    const val ACHIEVEMENTS = "achievements"
    const val INSULIN = "insulin"
    fun search(meal: MealType) = "search/${meal.name}"
    fun barcode(meal: MealType) = "barcode/${meal.name}"
    fun photo(meal: MealType) = "photo/${meal.name}"
    fun text(meal: MealType) = "text/${meal.name}"
    fun recipes(meal: MealType) = "recipes/${meal.name}"
    fun recipe(id: String, meal: MealType) = "recipe/$id/${meal.name}"
    fun fridge(meal: MealType) = "fridge/${meal.name}"
    fun customFood(meal: MealType, barcode: String = "-") = "custom/${meal.name}/$barcode"
    fun session(day: Int) = "session/$day"
}

/** Navigation actions shared by all screens. */
class Nav(private val controller: NavHostController) {
    fun go(route: String) = controller.navigate(route) { launchSingleTop = true }
    fun back() { if (!controller.popBackStack()) Unit }
    fun finishOnboarding() = controller.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
}

@Composable
fun StopFatRoot(vm: MainViewModel) {
    val loadedProfile by vm.profileOrNull.collectAsState()
    val toast by vm.toast.collectAsState()
    LiquidBackground {
        StopFatNavHost(vm, loadedProfile)
        GlassToast(
            toast,
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun StopFatNavHost(vm: MainViewModel, loadedProfile: com.stopfat.app.data.UserProfile?) {
    Box(Modifier.fillMaxSize()) {
        val profile = loadedProfile
        if (profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Glass.Violet) }
            return@Box
        }
        val controller = rememberNavController()
        val nav = remember(controller) { Nav(controller) }
        val start = remember { if (profile.onboarded) Routes.MAIN else Routes.ONBOARDING }
        val mealArg = listOf(navArgument("meal") { type = NavType.StringType })
        NavHost(
            controller,
            startDestination = start,
            enterTransition = { slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.94f) },
            exitTransition = { fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.96f) },
            popEnterTransition = { fadeIn(tween(260)) + scaleIn(tween(300), initialScale = 0.96f) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it / 2 } + fadeOut(tween(280)) + scaleOut(tween(300), targetScale = 0.94f) },
        ) {
            composable(Routes.ONBOARDING) { OnboardingScreen(vm, onDone = nav::finishOnboarding) }
            composable(Routes.MAIN) { MainTabs(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.ANALYSIS) { AnalysisScreen(vm, nav) }
            composable(Routes.FASTING) { FastingScreen(vm, nav) }
            composable(Routes.ACHIEVEMENTS) { AchievementsScreen(vm, nav) }
            composable(Routes.INSULIN) { com.stopfat.app.ui.screens.InsulinScreen(vm, nav) }
            composable("search/{meal}", mealArg) { FoodSearchScreen(vm, nav, MealType.fromName(it.arguments?.getString("meal"))) }
            composable("barcode/{meal}", mealArg) { BarcodeScreen(vm, nav, MealType.fromName(it.arguments?.getString("meal"))) }
            composable("photo/{meal}", mealArg) { PhotoScanScreen(vm, nav, MealType.fromName(it.arguments?.getString("meal"))) }
            composable("text/{meal}", mealArg) { TextFoodScreen(vm, nav, MealType.fromName(it.arguments?.getString("meal"))) }
            composable("recipes/{meal}", mealArg) { RecipesScreen(vm, nav, MealType.fromName(it.arguments?.getString("meal"))) }
            composable("fridge/{meal}", mealArg) { FridgeScreen(vm, nav, MealType.fromName(it.arguments?.getString("meal"))) }
            composable(
                "recipe/{id}/{meal}",
                listOf(navArgument("id") { type = NavType.StringType }, navArgument("meal") { type = NavType.StringType }),
            ) {
                RecipeDetailScreen(vm, nav, it.arguments?.getString("id").orEmpty(), MealType.fromName(it.arguments?.getString("meal")))
            }
            composable(
                "custom/{meal}/{barcode}",
                listOf(navArgument("meal") { type = NavType.StringType }, navArgument("barcode") { type = NavType.StringType }),
            ) {
                CustomFoodScreen(vm, nav, MealType.fromName(it.arguments?.getString("meal")), it.arguments?.getString("barcode")?.takeIf { b -> b != "-" })
            }
            composable("session/{day}", listOf(navArgument("day") { type = NavType.IntType })) {
                WorkoutSessionScreen(vm, nav, it.arguments?.getInt("day") ?: 0)
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Start", Icons.Rounded.Home),
    Tab("Dziennik", Icons.Rounded.Restaurant),
    Tab("Trening", Icons.Rounded.FitnessCenter),
    Tab("Postęp", Icons.Rounded.Insights),
    Tab("Coach AI", Icons.Rounded.AutoAwesome),
)

@Composable
fun MainTabs(vm: MainViewModel, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val request by vm.openTabRequest.collectAsState()
    LaunchedEffect(request) {
        request?.let { tab = it; vm.openTabRequest.value = null }
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it / 4 * dir } + fadeIn(tween(240)) + scaleIn(tween(300), initialScale = 0.96f)) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(180)))
            },
            label = "tabs",
            modifier = Modifier.fillMaxSize(),
        ) { t ->
            when (t) {
                0 -> HomeScreen(vm, nav, onOpenTab = { tab = it })
                1 -> FoodLogScreen(vm, nav)
                2 -> WorkoutScreen(vm, nav)
                3 -> ProgressScreen(vm, nav)
                else -> CoachScreen(vm, nav)
            }
        }
        LiquidNavBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/**
 * Floating white glass tab bar. The selection is a liquid drop: its leading edge springs fast and the
 * trailing edge follows slower, so it stretches while moving between tabs and snaps back into a pill.
 */
@Composable
fun LiquidNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(34.dp)
    var previous by remember { mutableIntStateOf(selected) }
    val movingRight = selected >= previous
    LaunchedEffect(selected) { previous = selected }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(70.dp)
            .glassSurface(shape, strong = true, opaque = true),
    ) {
        val itemWidth = maxWidth / tabs.size
        val fast = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
        val slow = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
        val left by animateDpAsState(itemWidth * selected + 6.dp, if (movingRight) slow else fast, label = "left")
        val right by animateDpAsState(itemWidth * (selected + 1) - 6.dp, if (movingRight) fast else slow, label = "right")
        Box(
            Modifier
                .offset(x = left, y = 7.dp)
                .width((right - left).coerceAtLeast(20.dp))
                .height(56.dp)
                .shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = Glass.Violet.copy(alpha = 0.5f), spotColor = Glass.Violet.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(28.dp))
                .background(Glass.accent)
                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f), 0.5f to Color.Transparent))
                },
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, t ->
                val isSel = i == selected
                val bounce by animateFloatAsState(if (isSel) 1.18f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "icon")
                val lift by animateDpAsState(if (isSel) (-2).dp else 0.dp, spring(dampingRatio = 0.4f), label = "lift")
                val tint by animateColorAsState(if (isSel) Color.White else Glass.TextTertiary, tween(250), label = "tint")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        t.icon, t.label, tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .offset(y = lift)
                            .graphicsLayer { scaleX = bounce; scaleY = bounce },
                    )
                    Text(t.label, color = tint, fontSize = 10.sp, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}
