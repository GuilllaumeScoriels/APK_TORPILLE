package com.torpille.app.ui

import com.torpille.app.util.t
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.torpille.app.repo.AuthRepository
import com.torpille.app.repo.UserRepository
import com.torpille.app.ui.screens.VerifyEmailScreen
import com.google.firebase.auth.FirebaseAuth
import com.torpille.app.ui.screens.AuthScreen
import com.torpille.app.ui.screens.CommunitiesTabScreen
import com.torpille.app.ui.screens.CommunityInfoScreen
import com.torpille.app.ui.screens.CommunityScreen
import com.torpille.app.ui.screens.CreateCommunityScreen
import com.torpille.app.ui.screens.HomeScreen
import com.torpille.app.ui.screens.JoinCommunityScreen
import com.torpille.app.ui.screens.LeaderboardScreen
import com.torpille.app.ui.screens.NotificationScreen
import com.torpille.app.ui.screens.ProfileSetupScreen
import com.torpille.app.ui.screens.ReelsScreen
import com.torpille.app.ui.screens.TorpilleursMapScreen
import com.torpille.app.ui.screens.UserProfileScreen
import com.torpille.app.util.DeepLinkStore

object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val VERIFY_EMAIL = "verifyEmail"
    const val PROFILE_SETUP = "profileSetup"

    const val HOME = "home"
    const val COMMUNITIES_TAB = "communitiesTab"
    const val PROFILE_TAB = "profileTab"
    const val MAP_TAB = "mapTab"
    const val LEADERBOARD_TAB = "leaderboardTab"
    const val NOTIFICATIONS = "notifications"
    const val REELS = "reels"

    const val CREATE_COMMUNITY = "createCommunity"
    const val COMMUNITY = "community"
    const val COMMUNITY_INFO = "communityInfo"
    const val JOIN = "join"
}

private fun com.torpille.app.util.PendingCommunityLink.route(): String =
    "${if (opensDiscussion) Routes.COMMUNITY else Routes.JOIN}/$id"

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val authRepo = remember { AuthRepository() }
    val userRepo = remember { UserRepository() }
    val currentUid by remember { authRepo.authState() }.collectAsState(initial = authRepo.currentUid)
    val legalRevision by com.torpille.app.util.DeviceLegalAcceptance.revision.collectAsState()
    val acceptedOnDevice = remember(currentUid, legalRevision) {
        com.torpille.app.util.DeviceLegalAcceptance.hasAccepted(currentUid)
    }
    if (currentUid != null && !acceptedOnDevice) {
        androidx.compose.runtime.key(currentUid) {
            androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false
            )) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    com.torpille.app.ui.screens.LegalWelcomeScreen(onContinue = {
                        currentUid?.let { com.torpille.app.util.DeviceLegalAcceptance.record(it) }
                    })
                }
            }
        }
    }

    val scope = rememberCoroutineScope()
    fun signOutAndReset() {
        scope.launch {
            authRepo.signOut()
            nav.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
        }
    }

    val pendingLink by DeepLinkStore.pendingLink.collectAsState()
    val pendingActivity by DeepLinkStore.pendingActivity.collectAsState()
    val pendingBackStack by nav.currentBackStackEntryAsState()
    val pendingRoute = pendingBackStack?.destination?.route

    LaunchedEffect(pendingLink, pendingActivity, currentUid, acceptedOnDevice, pendingRoute) {
        if (!acceptedOnDevice || currentUid == null || pendingRoute == null || pendingRoute in setOf(Routes.SPLASH, Routes.AUTH, Routes.VERIFY_EMAIL, Routes.PROFILE_SETUP)) return@LaunchedEffect
        if (pendingActivity) {
            nav.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
            DeepLinkStore.consumeActivity()
            return@LaunchedEffect
        }
        val link = pendingLink ?: return@LaunchedEffect

        val uid = authRepo.currentUid
        if (uid == null) {
            nav.navigate(Routes.AUTH) { launchSingleTop = true }
            return@LaunchedEffect
        }

        if (FirebaseAuth.getInstance().currentUser?.isEmailVerified != true) {
            nav.navigate(Routes.VERIFY_EMAIL) { launchSingleTop = true }
            return@LaunchedEffect
        }
        val needsPseudo = try {
            val me = userRepo.getMeOrThrow()
            !me.hasRequiredIdentity
        } catch (_: Exception) {
            true
        }

        if (needsPseudo) {
            nav.navigate(Routes.PROFILE_SETUP) { launchSingleTop = true }
            return@LaunchedEffect
        }

        // Keep the invitation pending until JoinCommunityScreen has taken it.
        // Consuming here made Splash and this effect race after sign-in/profile setup.
        val target = link.route()
        if (nav.currentDestination?.route != target) {
            nav.navigate(target) { launchSingleTop = true }
            DeepLinkStore.consumePendingLink(link)
        }
    }

    val bottomDestinations = listOf(
        BottomDestination(
            route = Routes.HOME,
            label = t("Accueil"),
            icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") }
        ),
        BottomDestination(
            route = Routes.COMMUNITIES_TAB,
            label = t("Communautés"),
            icon = { Icon(Icons.Default.Groups, contentDescription = "Communautés") }
        ),
        BottomDestination(
            route = Routes.PROFILE_TAB,
            label = t("Profil"),
            icon = { Icon(Icons.Default.Person, contentDescription = "Profil") }
        ),
        BottomDestination(
            route = Routes.LEADERBOARD_TAB,
            label = t("Classement"),
            icon = { Icon(Icons.Default.Leaderboard, contentDescription = "Classement") }
        ),
        BottomDestination(
            route = Routes.MAP_TAB,
            label = t("Carte"),
            icon = { Icon(Icons.Default.Map, contentDescription = "Carte") }
        ),
    )

fun navigateToTopLevel(route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination.isBottomBarDestination()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.White,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color.White
                ) {
                    bottomDestinations.forEach { item ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == item.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTopLevel(item.route) },
                            icon = item.icon,
                            label = { Text(item.label) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = androidx.compose.ui.graphics.Color(0xFFD31A29),
                                selectedTextColor = androidx.compose.ui.graphics.Color(0xFFD31A29),
                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unselectedIconColor = androidx.compose.ui.graphics.Color(0xFF6E6E73),
                                unselectedTextColor = androidx.compose.ui.graphics.Color(0xFF6E6E73)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = nav,
            startDestination = Routes.SPLASH,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            composable(Routes.SPLASH) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                LaunchedEffect(currentUid, acceptedOnDevice) {
                    val uid = authRepo.currentUid
                    // As on iOS, an invitation must not join a community before
                    // the signed-in account has accepted the legal documents.
                    if (uid != null && !acceptedOnDevice) return@LaunchedEffect

                    val target = if (uid == null) {
                        Routes.AUTH
                    } else if (FirebaseAuth.getInstance().currentUser?.isEmailVerified != true) {
                        Routes.VERIFY_EMAIL
                    } else {
                        val needsPseudo = try {
                            val me = userRepo.getMeOrThrow()
                            !me.hasRequiredIdentity
                        } catch (_: Exception) {
                            true
                        }

                        if (needsPseudo) {
                            Routes.PROFILE_SETUP
                        } else {
                            if (DeepLinkStore.consumeActivity()) Routes.NOTIFICATIONS else DeepLinkStore.consumePendingLink()?.route() ?: Routes.HOME
                        }
                    }

                    nav.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            composable(Routes.VERIFY_EMAIL) {
                VerifyEmailScreen(onVerified = {
                    nav.navigate(Routes.SPLASH) { popUpTo(Routes.VERIFY_EMAIL) { inclusive = true } }
                }, onSignOut = { signOutAndReset() })
            }

            composable(Routes.AUTH) {
                AuthScreen(
                    onAuthed = {
                        nav.navigate(Routes.SPLASH) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.PROFILE_SETUP) {
                ProfileSetupScreen(
                    onDone = {
                        val previousRoute = nav.previousBackStackEntry?.destination?.route
                        if (previousRoute == Routes.PROFILE_TAB) {
                            nav.popBackStack()
                        } else {
                            val target = if (DeepLinkStore.consumeActivity()) Routes.NOTIFICATIONS else DeepLinkStore.consumePendingLink()?.route() ?: Routes.HOME

                            nav.navigate(target) {
                                popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onCreateCommunity = { nav.navigate(Routes.CREATE_COMMUNITY) },
                    onOpenCommunity = { id -> nav.navigate("${Routes.COMMUNITY}/$id") },
                    onSignOut = { signOutAndReset() },
                    onOpenLeaderboard = { navigateToTopLevel(Routes.LEADERBOARD_TAB) },
                    onOpenProfile = { navigateToTopLevel(Routes.PROFILE_TAB) },
                    onOpenNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    onOpenReels = { nav.navigate(Routes.REELS) }
                )
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationScreen(
                    onBack = { nav.popBackStack() },
                    onOpenCommunity = { id -> nav.navigate("${Routes.COMMUNITY}/$id") }
                )
            }

            composable(Routes.REELS) {
                ReelsScreen(onBack = { nav.popBackStack() })
            }

            composable(Routes.COMMUNITIES_TAB) {
                CommunitiesTabScreen(
                    onCreateCommunity = { nav.navigate(Routes.CREATE_COMMUNITY) },
                    onJoinCommunity = { communityId -> nav.navigate("${Routes.JOIN}/${android.net.Uri.encode(communityId)}") },
                    onOpenCommunity = { communityId ->
                        nav.navigate("${Routes.COMMUNITY}/$communityId")
                    }
                )
            }

            composable(Routes.PROFILE_TAB) {
                UserProfileScreen(
                    onOpenLeaderboard = { navigateToTopLevel(Routes.LEADERBOARD_TAB) },
                    onEditProfile = { nav.navigate(Routes.PROFILE_SETUP) },
                    onSignOut = { signOutAndReset() }
                )
            }

            composable(Routes.MAP_TAB) {
                TorpilleursMapScreen(onTorpille = { communityId, targetUid ->
                    DeepLinkStore.pendingTorpilleTargetUid = targetUid
                    nav.navigate("${Routes.COMMUNITY}/$communityId")
                })
            }

            composable(Routes.LEADERBOARD_TAB) {
                LeaderboardScreen()
            }

            composable(Routes.CREATE_COMMUNITY) {
                CreateCommunityScreen(
                    onCreated = { id ->
                        nav.navigate("${Routes.COMMUNITY_INFO}/$id") {
                            popUpTo(Routes.COMMUNITIES_TAB)
                            launchSingleTop = true
                        }
                    },
                    onBack = { nav.popBackStack() }
                )
            }

            composable(
                route = "${Routes.JOIN}/{communityId}",
                arguments = listOf(navArgument("communityId") { type = NavType.StringType })
            ) { backStack ->
                val communityId = backStack.arguments?.getString("communityId") ?: return@composable
                JoinCommunityScreen(
                    communityId = communityId,
                    onJoined = {
                        nav.navigate("${Routes.COMMUNITY}/$communityId") {
                            popUpTo("${Routes.JOIN}/{communityId}") { inclusive = true }
                        }
                    },
                    onCancel = {
                        if (!nav.popBackStack()) nav.navigate(Routes.HOME) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "${Routes.COMMUNITY}/{communityId}",
                arguments = listOf(navArgument("communityId") { type = NavType.StringType })
            ) { backStack ->
                val communityId = backStack.arguments?.getString("communityId") ?: return@composable
                CommunityScreen(
                    communityId = communityId,
                    onOpenInfo = { nav.navigate("${Routes.COMMUNITY_INFO}/$communityId") },
                    onBack = { nav.popBackStack() }
                )
            }

            composable(
                route = "${Routes.COMMUNITY_INFO}/{communityId}",
                arguments = listOf(navArgument("communityId") { type = NavType.StringType })
            ) { backStack ->
                val communityId = backStack.arguments?.getString("communityId") ?: return@composable
                CommunityInfoScreen(
                    communityId = communityId,
                    onBack = { nav.popBackStack() },
                    onDeleted = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
                )
            }
        }
    }
}

private fun NavDestination?.isBottomBarDestination(): Boolean {
    val route = this?.route ?: return false
    return route == Routes.HOME ||
            route == Routes.COMMUNITIES_TAB ||
            route == Routes.PROFILE_TAB ||
            route == Routes.MAP_TAB ||
            route == Routes.LEADERBOARD_TAB
}
