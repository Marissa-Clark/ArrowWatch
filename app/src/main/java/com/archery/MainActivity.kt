package com.archery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.archery.ui.live.LiveScoringScreen
import com.archery.ui.sessions.AnalyticsScreen
import com.archery.ui.sessions.LogSessionScreen
import com.archery.ui.sessions.ManageProfilesScreen
import com.archery.ui.sessions.SessionDetailScreen
import com.archery.ui.sessions.SessionListScreen
import com.archery.ui.theme.ArcheryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArcheryTheme {
                ArcheryApp()
            }
        }
    }
}

@Composable
private fun ArcheryApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "sessions") {
        composable("sessions") {
            SessionListScreen(
                onSessionClick     = { id -> navController.navigate("session/$id") },
                onLiveSessionClick = { navController.navigate("live") },
                onManageProfiles   = { navController.navigate("profiles") },
                onLogPastSession   = { navController.navigate("log_session") },
            )
        }

        composable("log_session") {
            LogSessionScreen(
                onBack  = { navController.popBackStack() },
                onSaved = { id ->
                    // Pop log_session, then open the new session detail
                    navController.popBackStack()
                    navController.navigate("session/$id")
                },
            )
        }

        composable("profiles") {
            ManageProfilesScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "session/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStack ->
            val id = backStack.arguments!!.getLong("id")
            SessionDetailScreen(
                sessionId = id,
                onBack = { navController.popBackStack() },
                onAnalyticsClick = { navController.navigate("analytics/$id") },
            )
        }

        composable(
            route = "analytics/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStack ->
            val id = backStack.arguments!!.getLong("id")
            AnalyticsScreen(
                sessionId = id,
                onBack = { navController.popBackStack() },
            )
        }

        composable("live") {
            LiveScoringScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
