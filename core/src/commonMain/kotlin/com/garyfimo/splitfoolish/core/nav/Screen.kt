package com.garyfimo.splitfoolish.core.nav

/**
 * Routes for the single Compose tree. Enumerated here in :core (rather than inside a feature) so
 * every feature can set [com.garyfimo.splitfoolish.core.nav.Screen] without creating a feature↔feature
 * dependency just to navigate. Routes never carry typed feature data — only primitive IDs, which
 * are UUID strings generated client-side at entity creation so the same id is usable as a Firebase
 * document key when cloud sync lands.
 */
sealed class Screen {
    data object GroupsList : Screen()
    data object Friends : Screen()
    data object NewGroup : Screen()
    data object Settings : Screen()
    data class GroupDetail(val groupId: String) : Screen()
    data class ExpenseEditor(val groupId: String, val expenseId: String?) : Screen()
}
