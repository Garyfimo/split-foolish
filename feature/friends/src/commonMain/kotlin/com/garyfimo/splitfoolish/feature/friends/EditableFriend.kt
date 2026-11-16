package com.garyfimo.splitfoolish.feature.friends

/**
 * A friend lives in a global list, independent of any group. After the profile-and-username PR
 * every friend is a *linked* reference to a real SplitFoolish account — the add-friend dialog
 * resolves a username to a uid via the Firestore /usernames index. The displayed name is cached
 * at resolution time; rename on the owning user's side doesn't propagate here until the next
 * sign-in refresh.
 *
 * [uid] is the Firebase auth uid of the linked user. [iconId] is the profile icon id from the
 * shared ProfileAvatar catalog — null falls back to the initials bubble.
 */
data class EditableFriend(
    val id: String,
    val uid: String,
    val name: String,
    val iconId: String?,
)
