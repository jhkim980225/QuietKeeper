package com.quietkeeper.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quietkeeper.app.R
import com.quietkeeper.app.audio.MeasurementService
import com.quietkeeper.app.data.NoiseEvent
import com.quietkeeper.app.ui.theme.QuietKeeperTheme
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI instrumented tests that exercise each screen composable IN ISOLATION:
 * fake data + lambda flags are passed directly, no Service / Room / billing is involved.
 *
 * Localized strings (which differ between the EN and KO resources) are read from the
 * activity's resources at runtime via [string], so assertions are locale-independent.
 * Where a value is identical across locales (numbers, the "₩4,900" price prefix, the
 * "●" / "OK" glyphs) we assert on that literal directly.
 */
@RunWith(AndroidJUnit4::class)
class ScreensUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(resId: Int): String = rule.activity.getString(resId)

    // ---------------------------------------------------------------- PrepScreen

    @Test
    fun prepScreen_showsStatusRow_andStartButton_clicks() {
        var started = false
        rule.setContent {
            QuietKeeperTheme {
                PrepScreen(onStart = { started = true })
            }
        }

        // Status row label (locale-dependent) is shown.
        rule.onNodeWithText(string(R.string.prep_mic)).assertIsDisplayed()
        // "OK" is identical across locales.
        rule.onAllNodesWithText("OK", substring = true).onFirst().assertIsDisplayed()

        // Start button: "●" prefix is locale-independent; click toggles the flag.
        rule.onAllNodesWithText("●", substring = true).onFirst().assertIsDisplayed()
        rule.onNodeWithText(string(R.string.prep_start)).performClick()
        assertTrue("onStart should fire when the start button is clicked", started)
    }

    // -------------------------------------------------------------- PaywallScreen

    @Test
    fun paywallScreen_free_showsPrice_andSubscribeFires() {
        var subscribed = false
        rule.setContent {
            QuietKeeperTheme {
                PaywallScreen(onBack = {}, onSubscribe = { subscribed = true }, isPro = false)
            }
        }

        // Price prefix is identical across locales ("₩4,900/월" vs "₩4,900/mo").
        rule.onAllNodesWithText("₩4,900", substring = true).onFirst().assertIsDisplayed()

        // Subscribe CTA shown and wired.
        rule.onNodeWithText(string(R.string.paywall_subscribe)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.paywall_subscribe)).performClick()
        assertTrue("onSubscribe should fire when the subscribe button is clicked", subscribed)
    }

    @Test
    fun paywallScreen_pro_showsActiveState_notSubscribeCta() {
        var subscribed = false
        rule.setContent {
            QuietKeeperTheme {
                PaywallScreen(onBack = {}, onSubscribe = { subscribed = true }, isPro = true)
            }
        }

        // Pro -> the "active" label is shown instead of the subscribe CTA.
        rule.onNodeWithText(string(R.string.paywall_active)).assertIsDisplayed()
        rule.onAllNodesWithText(string(R.string.paywall_subscribe)).assertCountEquals(0)
        assertFalse("onSubscribe must not fire in the Pro state", subscribed)
    }

    // ------------------------------------------------------------ EventListScreen

    @Test
    fun eventListScreen_empty_showsEmptyState() {
        rule.setContent {
            QuietKeeperTheme {
                EventListScreen(events = emptyList(), onBack = {}, onOpen = {})
            }
        }
        rule.onNodeWithText(string(R.string.events_empty)).assertIsDisplayed()
    }

    @Test
    fun eventListScreen_withOneEvent_showsDb_andRowClickOpensIt() {
        var opened: Long = -1L
        val event = NoiseEvent(
            id = 42L,
            timestamp = 0L,
            peakDb = 71f,
            leq = 50f,
            wavPath = "/data/events/clip.wav",
            moved = false,
            tag = null,
            note = null,
        )
        rule.setContent {
            QuietKeeperTheme {
                EventListScreen(events = listOf(event), onBack = {}, onOpen = { opened = it })
            }
        }

        // peakDb 71f renders as "71" (locale-independent number).
        rule.onAllNodesWithText("71", substring = true).onFirst().assertIsDisplayed()

        // Clicking the row invokes onOpen with the event id.
        rule.onAllNodesWithText("71", substring = true).onFirst().performClick()
        assertEquals(42L, opened)
    }

    // ----------------------------------------------------------------- SaveScreen

    @Test
    fun saveScreen_showsEventCount_andSaveDiscardFire() {
        var saved = false
        var discarded = false
        val summary = MeasurementService.SessionSummary(
            durationMs = 83_000L,
            eventCount = 7,
            maxLmax = 71f,
            avgLeq = 42f,
        )
        rule.setContent {
            QuietKeeperTheme {
                SaveScreen(
                    summary = summary,
                    onSave = { _, _ -> saved = true },
                    onDiscard = { discarded = true },
                )
            }
        }

        // eventCount 7 is rendered (locale-independent).
        rule.onAllNodesWithText("7", substring = true).onFirst().assertIsDisplayed()

        rule.onNodeWithText(string(R.string.save_button)).performClick()
        assertTrue("onSave should fire when the save button is clicked", saved)

        rule.onNodeWithText(string(R.string.save_discard)).performClick()
        assertTrue("onDiscard should fire when the discard button is clicked", discarded)
    }
}
