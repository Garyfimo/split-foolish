package com.garyfimo.splitfoolish.core.nav

/**
 * Routes for the single Compose tree. Enumerated here in :core (rather than inside a feature) so
 * every feature can set [com.garyfimo.splitfoolish.core.nav.Screen] without creating a feature↔feature
 * dependency just to navigate. Routes never carry typed feature data — only primitive IDs.
 */
sealed class Screen {
    data object GroupsList : Screen()
    data object Friends : Screen()
    data object NewGroup : Screen()
    data class GroupDetail(val groupId: Long) : Screen()
    data class ExpenseEditor(val groupId: Long, val expenseId: Long?) : Screen()
}
