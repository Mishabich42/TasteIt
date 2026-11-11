package com.example.tesltls.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.tesltls.di.ServiceLocator
import com.example.tesltls.ui.FoodCategory
import com.example.tesltls.ui.common.entryViewModel
import com.example.tesltls.ui.components.BottomTab
import com.example.tesltls.ui.components.FoodBottomBar
import com.example.tesltls.ui.details.ActionMenuScreen
import com.example.tesltls.ui.details.DishDetailsViewModel
import com.example.tesltls.ui.details.PlacesScreen
import com.example.tesltls.ui.details.RecipeScreen
import com.example.tesltls.ui.favorites.FavoritesScreen
import com.example.tesltls.ui.favorites.FavoritesViewModel
import com.example.tesltls.ui.home.HomeScreen
import com.example.tesltls.ui.home.HomeViewModel
import com.example.tesltls.ui.profile.ProfileScreen
import com.example.tesltls.ui.swipe.SwipeScreen
import com.example.tesltls.ui.swipe.SwipeViewModel
import com.example.tesltls.ui.theme.FoodColors
import kotlinx.serialization.Serializable

/** Типобезпечні маршрути навігації (Navigation Compose 2.8+). */
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Favorites : Route
    @Serializable data object Profile : Route

    /** [category] — значення з FoodCategory.dbValue (напр. "Soups"). */
    @Serializable data class Swipe(val category: String) : Route
    @Serializable data class Actions(val dishId: String) : Route
    @Serializable data class Recipe(val dishId: String) : Route
    @Serializable data class Places(val dishId: String) : Route
}

@Composable
fun FoodApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // Яка вкладка підсвічена: екрани пошукового сценарію (категорія, свайп, дії...) належать "Пошуку"
    val selectedTab = when {
        destination?.hasRoute<Route.Favorites>() == true -> BottomTab.FAVORITES
        destination?.hasRoute<Route.Profile>() == true -> BottomTab.PROFILE
        else -> BottomTab.SEARCH
    }

    Scaffold(
        containerColor = FoodColors.Background,
        bottomBar = {
            FoodBottomBar(selected = selectedTab) { tab -> navController.openTab(tab) }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            composable<Route.Home> {
                val vm = entryViewModel { HomeViewModel(ServiceLocator.foodRepository) }
                HomeScreen(
                    viewModel = vm,
                    onCategoryClick = { navController.navigate(Route.Swipe(it.dbValue)) },
                    onDishClick = { navController.navigate(Route.Actions(it)) }
                )
            }

            composable<Route.Swipe> { entry ->
                val route = entry.toRoute<Route.Swipe>()
                val vm = entryViewModel {
                    SwipeViewModel(
                        repository = ServiceLocator.foodRepository,
                        userId = { ServiceLocator.ensureUserId() },
                        category = route.category
                    )
                }
                SwipeScreen(
                    viewModel = vm,
                    category = FoodCategory.fromDb(route.category),
                    onBack = { navController.popBackStack() },
                    onOpenDish = { navController.navigate(Route.Actions(it)) }
                )
            }

            composable<Route.Actions> { entry ->
                val route = entry.toRoute<Route.Actions>()
                val vm = entryViewModel {
                    DishDetailsViewModel(ServiceLocator.foodRepository, route.dishId)
                }
                ActionMenuScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { navController.navigate(Route.Recipe(route.dishId)) },
                    onOpenPlaces = { navController.navigate(Route.Places(route.dishId)) },
                    // "Вийти до меню": повертаємось на головний екран з категоріями
                    onExitToMenu = { navController.popBackStack<Route.Home>(inclusive = false) }
                )
            }

            composable<Route.Recipe> { entry ->
                val route = entry.toRoute<Route.Recipe>()
                val vm = entryViewModel {
                    DishDetailsViewModel(ServiceLocator.foodRepository, route.dishId)
                }
                RecipeScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }

            composable<Route.Places> { entry ->
                val route = entry.toRoute<Route.Places>()
                val vm = entryViewModel {
                    DishDetailsViewModel(ServiceLocator.foodRepository, route.dishId)
                }
                PlacesScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }

            composable<Route.Favorites> {
                val vm = entryViewModel {
                    FavoritesViewModel(ServiceLocator.foodRepository) { ServiceLocator.ensureUserId() }
                }
                FavoritesScreen(
                    viewModel = vm,
                    onDishClick = { navController.navigate(Route.Actions(it)) }
                )
            }

            composable<Route.Profile> { ProfileScreen() }
        }
    }
}

/** Перехід між вкладками нижньої панелі. */
private fun NavHostController.openTab(tab: BottomTab) {
    when (tab) {
        // "Пошук" — повертаємось на головний екран, скидаючи все, що відкрито поверх
        BottomTab.SEARCH -> popBackStack<Route.Home>(inclusive = false)
        BottomTab.FAVORITES -> navigate(Route.Favorites) {
            popUpTo<Route.Home> { inclusive = false }
            launchSingleTop = true
        }
        BottomTab.PROFILE -> navigate(Route.Profile) {
            popUpTo<Route.Home> { inclusive = false }
            launchSingleTop = true
        }
    }
}
