package org.neteinstein.snap2sheet

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.neteinstein.snap2sheet.data.remote.SheetsException
import org.neteinstein.snap2sheet.data.repository.DefaultSpreadsheetRepository
import org.neteinstein.snap2sheet.testing.FakeSheetsGateway
import org.neteinstein.snap2sheet.testing.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpreadsheetRepositoryTest {

    private val store = InMemoryKeyValueStore()
    private val gateway = FakeSheetsGateway()
    private val json = Json { ignoreUnknownKeys = true }

    private fun repository() = DefaultSpreadsheetRepository(store, gateway, json)

    @Test
    fun refresh_loadsAndCachesTheList() = runTest {
        repository().refresh()

        assertEquals(listOf("Despesas 2026"), repository().items.value.map { it.name })
    }

    @Test
    fun refresh_failure_keepsTheCachedList() = runTest {
        repository().refresh()
        gateway.failWith = SheetsException("Offline", SheetsException.Kind.NETWORK)
        val repository = repository()

        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertEquals(1, repository.items.value.size)
    }

    @Test
    fun createDestination_selectsItAsTheDefault() = runTest {
        val repository = repository()

        val created = repository.create("  Faturas 2026 ").getOrThrow()

        assertEquals("Faturas 2026", created.name)
        assertEquals(created, repository().selected.value)
    }

    @Test
    fun clear_forgetsTheDefault() = runTest {
        val repository = repository()
        repository.create("X")

        repository.clear()

        assertNull(repository().selected.value)
        assertEquals(emptyList(), repository().items.value)
    }
}
