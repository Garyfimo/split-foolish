package com.garyfimo.splitfoolish.data

/**
 * Plain, platform-neutral snapshot of everything the app persists. Exposed from
 * [SplitFoolishRepository] so the feature/UI layer can hydrate its own editable types without
 * taking a dependency on SQLDelight's generated classes — that keeps the data plugin out of
 * the compile classpath of :feature:* modules.
 */
data class AppSnapshot(
    val friends: List<PersistedFriend>,
    val groups: List<PersistedGroup>,
)

data class PersistedFriend(
    val id: String,
    val name: String,
)

data class PersistedGroup(
    val id: String,
    val name: String,
    val members: List<PersistedMember>,
    val expenses: List<PersistedExpense>,
)

data class PersistedMember(
    val id: String,
    val name: String,
)

data class PersistedExpense(
    val id: String,
    val title: String,
    val amountInput: String,
    val payerId: String,
    val splitMode: String,
    val participants: List<PersistedExpenseParticipant>,
)

data class PersistedExpenseParticipant(
    val memberId: String,
    val included: Boolean,
    val percentInput: String,
)
