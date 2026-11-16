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
    // Firebase auth uid of the linked user — empty for legacy typed-name entries. Carried through
    // so the groups Add-member-by-username flow can pre-fill from the friends quick-pick without
    // a round-trip to Firestore.
    val uid: String,
    // Profile icon id from the ProfileAvatar catalog. Null for legacy entries; the UI falls back
    // to the initials bubble.
    val iconId: String?,
)

data class PersistedGroup(
    val id: String,
    val name: String,
    // Firebase auth uid of the user who originally created the group. Threaded end-to-end from
    // Firestore so remote-hydrated groups keep their real owner — without this field we'd have
    // to re-attribute on every hydration (defaulting to the signed-in user) which both fails
    // the server-side "createdByUid pinned on update" rule and corrupts ownership. Empty string
    // means "unknown" (legacy / pre-Firestore local data); the cloud-layer fallback fills it in.
    val createdByUid: String,
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
    // Auth uid of the user who created the expense. This is the field the UI's delete-control
    // visibility check depends on; previously the Persisted type dropped it, which meant every
    // remote-hydrated expense was mis-attributed to the signed-in user and the "only creator
    // can delete" guarantee collapsed on receiver devices. Empty string → unknown (local-only
    // path); SyncedRepository fills it from CloudExpense on the Firestore path.
    val createdByUid: String,
)

data class PersistedExpenseParticipant(
    val memberId: String,
    val included: Boolean,
    val percentInput: String,
)
