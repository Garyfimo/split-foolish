package com.garyfimo.splitfoolish.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [parseInviteToken]. Pure logic — no Firestore. Lives under commonTest so it
 * runs on both Android host (JVM) and iOS sim without the compose-ui-test harness.
 */
class InviteUrlParsingTest {

    @Test
    fun parses_custom_scheme_url() {
        assertEquals("abc-123", parseInviteToken("splitfoolish://invite/abc-123"))
    }

    @Test
    fun parses_hosted_https_url() {
        assertEquals(
            "abc-123",
            parseInviteToken("https://splitfoolish-77870.web.app/i/abc-123"),
        )
    }

    @Test
    fun strips_query_string() {
        assertEquals(
            "abc-123",
            parseInviteToken("splitfoolish://invite/abc-123?foo=bar"),
        )
    }

    @Test
    fun strips_fragment() {
        assertEquals(
            "abc-123",
            parseInviteToken("splitfoolish://invite/abc-123#anchor"),
        )
    }

    @Test
    fun strips_trailing_path_from_hosted_form() {
        assertEquals(
            "abc-123",
            parseInviteToken("https://splitfoolish-77870.web.app/i/abc-123/extra"),
        )
    }

    @Test
    fun returns_null_for_unrelated_url() {
        assertNull(parseInviteToken("https://google.com/"))
        assertNull(parseInviteToken("mailto:foo@example.com"))
    }

    @Test
    fun returns_null_for_empty_token() {
        assertNull(parseInviteToken("splitfoolish://invite/"))
        assertNull(parseInviteToken("https://splitfoolish-77870.web.app/i/"))
    }

    @Test
    fun returns_null_for_blank_input() {
        assertNull(parseInviteToken(""))
        assertNull(parseInviteToken("   "))
    }

    @Test
    fun invite_link_builder_prepends_hosting_base() {
        val link = inviteLinkFor("tok-123")
        assertEquals("https://splitfoolish-77870.web.app/i/tok-123", link)
    }
}
