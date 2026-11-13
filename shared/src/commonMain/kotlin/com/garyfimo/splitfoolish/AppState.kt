package com.garyfimo.splitfoolish

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.garyfimo.splitfoolish.core.nav.Screen
import com.garyfimo.splitfoolish.feature.friends.EditableFriend
import com.garyfimo.splitfoolish.feature.groups.EditableGroup
import com.garyfimo.splitfoolish.feature.groups.EditableMember

/**
 * Root coordinator for the whole app. Owns the nav state plus the two cross-feature collections
 * (friends and groups) and the monotonic id counter that every editable entity pulls from. Lives
 * in :shared because this is the only module that depends on both features and can see all of
 * their types at once; the features themselves don't know about each other (groups depends on
 * friends only for the EditableFriend type, which it renders in its pickers).
 */
class AppState {
    private var idCounter: Long = 0
    fun nextId(): Long = ++idCounter

    var screen: Screen by mutableStateOf(Screen.GroupsList)
    val groups: SnapshotStateList<EditableGroup> = mutableStateListOf()
    val friends: SnapshotStateList<EditableFriend> = mutableStateListOf()

    fun addFriend(name: String): EditableFriend {
        val friend = EditableFriend(nextId(), name)
        friends.add(friend)
        return friend
    }

    fun removeFriend(id: Long) {
        friends.removeAll { it.id == id }
    }

    fun deleteGroup(id: Long) {
        groups.removeAll { it.id == id }
    }

    fun findGroup(id: Long): EditableGroup? = groups.firstOrNull { it.id == id }

    /**
     * Create a new group with the given name, pre-populated with members copied from the given
     * friend ids. Member names are snapshot-copied — no reference link between members and
     * friends — see docs on [EditableFriend] for why.
     */
    fun createGroupWithFriends(name: String, friendIds: List<Long>): EditableGroup {
        val group = EditableGroup(nextId(), name)
        friendIds.forEach { friendId ->
            val friend = friends.firstOrNull { it.id == friendId } ?: return@forEach
            group.members.add(EditableMember(nextId(), friend.name.trim()))
        }
        groups.add(group)
        return group
    }
}
