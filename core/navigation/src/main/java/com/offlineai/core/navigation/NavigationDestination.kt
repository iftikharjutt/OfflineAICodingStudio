package com.offlineai.core.navigation

sealed class NavigationDestination(val route: String, val title: String) {
    data object Chat : NavigationDestination("chat", "Chat")
    data object Projects : NavigationDestination("projects", "Projects")
    data object Editor : NavigationDestination("editor", "Editor")
    data object Preview : NavigationDestination("preview", "Preview")
    data object Terminal : NavigationDestination("terminal", "Terminal")
    data object Models : NavigationDestination("models", "Models")
    data object Settings : NavigationDestination("settings", "Settings")
}
