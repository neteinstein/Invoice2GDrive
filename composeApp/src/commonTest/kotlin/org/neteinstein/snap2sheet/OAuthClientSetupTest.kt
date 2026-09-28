package org.neteinstein.snap2sheet

import org.neteinstein.snap2sheet.data.auth.OAuthClientSetup
import org.neteinstein.snap2sheet.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuthClientSetupTest {

    private val store = InMemoryKeyValueStore()
    private fun setup(buildTimeId: String = "") = OAuthClientSetup(store, buildTimeId, "Web application", "Origin")

    @Test
    fun buildWithoutIdHasNoneUntilOneIsEntered() {
        val setup = setup()
        assertEquals("", setup.clientId)
        setup.update("  123456789-abc123def.apps.googleusercontent.com ")
        assertEquals("123456789-abc123def.apps.googleusercontent.com", setup.clientId)
        assertTrue(setup.isEnteredInApp)
    }

    @Test
    fun enteredIdWinsOverTheBuildsAndSurvivesARestart() {
        setup(buildTimeId = "1-build.apps.googleusercontent.com").update("2-typed.apps.googleusercontent.com")
        assertEquals("2-typed.apps.googleusercontent.com", setup(buildTimeId = "1-build.apps.googleusercontent.com").clientId)
    }

    @Test
    fun clearingFallsBackToTheBuildsId() {
        val setup = setup(buildTimeId = "1-build.apps.googleusercontent.com")
        setup.update("2-typed.apps.googleusercontent.com")
        setup.update(null)
        assertEquals("1-build.apps.googleusercontent.com", setup.clientId)
        assertFalse(setup.isEnteredInApp)
    }

    @Test
    fun rejectsSomethingThatIsNotAClientId() {
        val setup = setup()
        assertFailsWith<IllegalArgumentException> { setup.update("GOCSPX-thisIsAClientSecret") }
        assertFailsWith<IllegalArgumentException> { setup.update("https://example.com") }
        assertEquals("", setup.clientId)
    }
}
