package com.stopfat.app.ui

import androidx.compose.animation.AnimatedContent
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
    LiquidBackground {
        val profile = loadedProfile
        if (profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Glass.Teal) }
            return@LiquidBackground
        }
        val controller = rememberNavController()
        val nav = remember(controller) { Nav(controller) }
        val start = remember { if (profile.onboarded) Routes.MAIN else Routes.ONBOARDING }
        val mealArg = listOf(navArgument("meal") { type = NavType.StringType })
        NavHost(
            controller,
            startDestination = start,
            enterTransition = { slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(250)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(300)) },
        ) {
            composable(Routes.ONBOARDING) { OnboardingScreen(vm, onDone = nav::finishOnboarding) }
            composable(Routes.MAIN) { MainTabs(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.ANALYSIS) { AnalysisScreen(vm, nav) }
            composable(Routes.FASTING) { FastingScreen(vm, nav) }
            composable(Routes.ACHIEVEMENTS) { AchievementsScreen(vm, nav) }
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
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
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

/** Floating glass tab bar with a "liquid" selection bubble that springs between tabs. */
@Composable
fun LiquidNavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Glass.Violet)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2E2A6B).copy(alpha = 0.985f), Color(0xFF17143F).copy(alpha = 0.99f))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.25f))), shape),
    ) {
        val itemWidth = maxWidth / tabs.size
        val bubbleX by animateDpAsState(
            itemWidth * selected,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "bubble",
        )
        Box(
            Modifier
                .offset(x = bubbleX + 6.dp, y = 6.dp)
                .width(itemWidth - 12.dp)
                .fillMaxHeight()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Glass.Teal.copy(alpha = 0.35f), Glass.Violet.copy(alpha = 0.30f))))
                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(26.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, t ->
                val isSel = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(t.icon, t.label, tint = if (isSel) Color.White else Glass.TextTertiary, modifier = Modifier.size(24.dp))
                    Text(
                        t.label,
                        color = if (isSel) Color.White else Glass.TextTertiary,
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
