package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NewGroupScreenTest {

    @Test
    fun create_disabled_until_name_is_entered() = runComposeUiTest {
        setContent {
            NewGroupScreen(onCreateGroup = {}, onBack = {})
        }
        onNodeWithText("Create").assertIsNotEnabled()
        onNodeWithText("Group name (e.g. Beach trip)").performTextInput("Trip")
        onNodeWithText("Create").assertIsEnabled()
    }

    @Test
    fun create_passes_trimmed_name_to_callback() = runComposeUiTest {
        var created: String? = null
        setContent {
            NewGroupScreen(onCreateGroup = { created = it }, onBack = {})
        }
        onNodeWithText("Group name (e.g. Beach trip)").performTextInput("  Trip  ")
        onNodeWithText("Create").performClick()
        assertEquals("Trip", created)
    }

    @Test
    fun cancel_button_fires_back_callback() = runComposeUiTest {
        var back = false
        setContent {
            NewGroupScreen(onCreateGroup = {}, onBack = { back = true })
        }
        onNodeWithText("Cancel").performClick()
        assertTrue(back)
    }

    @Test
    fun top_bar_back_button_fires_back() = runComposeUiTest {
        var back = false
        setContent {
            NewGroupScreen(onCreateGroup = {}, onBack = { back = true })
        }
        onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }

    @Test
    fun whitespace_only_name_keeps_create_disabled() = runComposeUiTest {
        var created: String? = null
        setContent {
            NewGroupScreen(onCreateGroup = { created = it }, onBack = {})
        }
        onNodeWithText("Group name (e.g. Beach trip)").performTextInput("   ")
        onNodeWithText("Create").assertIsNotEnabled()
        assertNull(created)
    }
}
