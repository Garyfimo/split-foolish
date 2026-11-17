package com.garyfimo.splitfoolish

import com.garyfimo.splitfoolish.data.parseInviteToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge between platform URL entry points (Android's `Intent` handling, iOS's SwiftUI
 * `.onOpenURL`) and the Compose [App] tree.
 *
 * Why this exists as a per-app singleton rather than a global `object`: a global would make
 * tests hard (no way to provide a fresh instance per test case) and would also leak state
 * across hot-reload. A class instantiated once in [MainActivity] / [MainViewController],
 * handed to both the platform handler and [App], gives us the same "single source of truth"
 * guarantee without those drawbacks.
 *
 * Why a StateFlow rather than a Channel / SharedFlow: there is only ever one pending invite to
 * act on — if the user taps two invite URLs back-to-back before the first one has been routed,
 * acting on the second is the right behaviour (the first is stale). Replacing the value in a
 * StateFlow is exactly that semantic, and makes the read-side a plain collectAsStateWithLifecycle.
 *
 * The flow carries the RAW token (not the URL) so App.kt doesn't have to re-parse on every
 * emission; platform sites call [submitUrl] with the full URL and this class does the parse.
 */
class DeepLinkBridge {
    private val _pendingInviteToken = MutableStateFlow<String?>(null)
    val pendingInviteToken: StateFlow<String?> = _pendingInviteToken.asStateFlow()

    /**
     * Push a URL into the bridge. Non-invite URLs are silently ignored — the platform sites
     * forward every URL they receive, and this method is the filter.
     */
    fun submitUrl(url: String) {
        val token = parseInviteToken(url) ?: return
        _pendingInviteToken.value = token
    }

    /**
     * Mark the pending invite as consumed. Called by App.kt once it has routed the token to
     * [com.garyfimo.splitfoolish.core.nav.Screen.AcceptInvite], so a subsequent navigation
     * away from that screen doesn't bounce the user back onto it.
     */
    fun clearPendingInvite() {
        _pendingInviteToken.value = null
    }
}
