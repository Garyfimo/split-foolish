package com.garyfimo.splitfoolish.feature.friends

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A friend lives in a global list, independent of any group. Picking a friend when adding a group
 * member copies the current name into a new group member; later edits on either side don't
 * propagate. Keeps removal simple — no cascade — at the cost of not being able to ask "which
 * groups is Alice in?" without a lookup pass.
 */
class EditableFriend(
    val id: Long,
    name: String,
) {
    var name: String by mutableStateOf(name)
}
