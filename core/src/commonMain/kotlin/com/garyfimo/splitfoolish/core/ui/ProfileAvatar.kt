package com.garyfimo.splitfoolish.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The profile-icon catalog. Users pick one of these in Settings; the chosen [id] is the string
 * that gets persisted to Firestore on /users/{uid}.iconId. The id is deliberately human-readable
 * (not an ordinal) so adding/removing catalog entries later doesn't shuffle everyone's icon — old
 * ids will keep resolving to the same icon, and any id we later drop can be handled as "fall back
 * to the initials bubble".
 *
 * Each catalog entry pairs an icon with a fixed background tint so picking an icon is equivalent
 * to picking a complete visual identity — no separate color picker needed.
 */
data class ProfileIcon(
    val id: String,
    val vector: ImageVector,
    val background: Color,
)

private val catalog: List<ProfileIcon> = listOf(
    ProfileIcon("person", Icons.Filled.Person, Color(0xFF9CCC65)),       // light green
    ProfileIcon("face", Icons.Filled.Face, Color(0xFFFFB74D)),           // amber
    ProfileIcon("account_circle", Icons.Filled.AccountCircle, Color(0xFF4FC3F7)), // sky blue
    ProfileIcon("account_box", Icons.Filled.AccountBox, Color(0xFFBA68C8)),       // purple
    ProfileIcon("favorite", Icons.Filled.Favorite, Color(0xFFEF5350)),   // red
    ProfileIcon("star", Icons.Filled.Star, Color(0xFFFFD54F)),           // yellow
    ProfileIcon("thumb_up", Icons.Filled.ThumbUp, Color(0xFF26A69A)),    // teal
    ProfileIcon("home", Icons.Filled.Home, Color(0xFF8D6E63)),           // brown
    ProfileIcon("place", Icons.Filled.LocationOn, Color(0xFFEC407A)),    // pink
    ProfileIcon("play", Icons.Filled.PlayArrow, Color(0xFF7E57C2)),      // deep purple
    ProfileIcon("send", Icons.AutoMirrored.Filled.Send, Color(0xFF42A5F5)), // blue
    ProfileIcon("phone", Icons.Filled.Phone, Color(0xFF66BB6A)),         // green
    ProfileIcon("email", Icons.Filled.Email, Color(0xFF5C6BC0)),         // indigo
    ProfileIcon("settings", Icons.Filled.Settings, Color(0xFF78909C)),   // blue grey
    ProfileIcon("build", Icons.Filled.Build, Color(0xFFFF7043)),         // deep orange
    ProfileIcon("shopping_cart", Icons.Filled.ShoppingCart, Color(0xFF26C6DA)), // cyan
    ProfileIcon("notifications", Icons.Filled.Notifications, Color(0xFFD4E157)), // lime
    ProfileIcon("create", Icons.Filled.Create, Color(0xFFAB47BC)),       // mid purple
    ProfileIcon("share", Icons.Filled.Share, Color(0xFF29B6F6)),         // light blue
    ProfileIcon("check", Icons.Filled.CheckCircle, Color(0xFF66BB6A)),   // green
    ProfileIcon("date_range", Icons.Filled.DateRange, Color(0xFFFF8A65)), // light orange
    ProfileIcon("info", Icons.Filled.Info, Color(0xFF9575CD)),           // violet
)

/** The whole catalog, in display order. */
val profileIconCatalog: List<ProfileIcon> = catalog

/** Look up by id. Null if the id isn't in the catalog — the composable renders the initials
 *  fallback in that case, so an unknown id degrades gracefully rather than crashing. */
fun profileIconById(id: String?): ProfileIcon? =
    id?.let { catalog.firstOrNull { entry -> entry.id == it } }

/**
 * Round avatar for a user. Renders the catalog icon on its tinted background when [iconId]
 * resolves; otherwise draws an initials bubble derived from [fallbackLabel] over a neutral
 * surface tint. Centralising this means every surface that shows a user avatar — friends list,
 * group member chips, settings preview — gets the same visuals for free.
 */
@Composable
fun ProfileAvatar(
    iconId: String?,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val resolved = profileIconById(iconId)
    val background = resolved?.background
        ?: MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (resolved != null) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .size(size)
            .background(color = background, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (resolved != null) {
            Icon(
                imageVector = resolved.vector,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.6f),
            )
        } else {
            // Compute initials once per label — take the first alphabetic char of up to two
            // whitespace-separated tokens. "alice m" → "AM", "alice" → "A", "" → "?".
            val initials = fallbackLabel.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .take(2)
                .mapNotNull { it.firstOrNull { ch -> ch.isLetter() }?.uppercaseChar() }
                .joinToString("")
                .ifBlank { "?" }
            Text(
                text = initials,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}
