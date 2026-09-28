package org.neteinstein.snap2sheet

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.neteinstein.snap2sheet.data.repository.DefaultInvoiceRepository
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.domain.qr.QrParseResult
import org.neteinstein.snap2sheet.testing.FixedClock
import org.neteinstein.snap2sheet.testing.InMemoryKeyValueStore
import org.neteinstein.snap2sheet.testing.InMemoryPhotoStore
import org.neteinstein.snap2sheet.testing.SAMPLE_QR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceRepositoryTest {

    private val store = InMemoryKeyValueStore()
    private val photos = InMemoryPhotoStore()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sheet = DriveItem("sheet-1", "Despesas 2026")
    private val folder = DriveItem("folder-1", "Faturas")

    private fun TestScope.repository() = DefaultInvoiceRepository(store, photos, json, this, FixedClock())

    @Test
    fun startDraftFromQr_publishesTheParsedDraft() = runTest {
        val repository = repository()

        assertIs<QrParseResult.Success>(repository.startDraftFromQr(SAMPLE_QR))

        val draft = repository.draftInvoice.value!!
        assertEquals("AAJFJMM9-4821", draft.atcud)
        assertEquals(SAMPLE_QR, draft.rawQr)
    }

    @Test
    fun startDraftFromQr_leavesTheDraftAloneForUnknownCodes() = runTest {
        val repository = repository()

        assertIs<QrParseResult.Failure>(repository.startDraftFromQr("hello"))
        assertNull(repository.draftInvoice.value)
    }

    @Test
    fun attachPhoto_storesItAndReplacingItDeletesTheOldOne() = runTest {
        val repository = repository()
        repository.startDraftFromQr(SAMPLE_QR)

        repository.attachPhoto(byteArrayOf(1, 2, 3), "image/jpeg")
        val first = repository.draftInvoice.value!!.photo!!
        repository.attachPhoto(byteArrayOf(4), "image/jpeg")
        testScheduler.advanceUntilIdle()

        val second = repository.draftInvoice.value!!.photo!!
        assertEquals(setOf(second.key), photos.photos.keys)
        assertTrue(first.key != second.key)
    }

    @Test
    fun submitDraft_queuesTheInvoiceWithItsTargetsAndWarnings() = runTest {
        val repository = repository()
        repository.startDraftFromQr(SAMPLE_QR) // No merchant name → a warning.
        repository.attachPhoto(byteArrayOf(1), "image/jpeg")

        val queued = assertNotNull(repository.submitDraft(sheet, folder))

        assertEquals(InvoiceStatus.QUEUED, queued.status)
        assertEquals("sheet-1", queued.destinationSpreadsheetId)
        assertEquals("folder-1", queued.destinationFolderId)
        assertTrue(queued.warnings.isNotEmpty())
        assertEquals(listOf(queued), repository.invoices.value)
        assertNull(repository.draftInvoice.value)
        assertEquals(1, photos.photos.size, "the queued invoice still needs its photo")
    }

    @Test
    fun discardDraft_deletesItsPhoto() = runTest {
        val repository = repository()
        repository.startDraftFromQr(SAMPLE_QR)
        repository.attachPhoto(byteArrayOf(1), "image/jpeg")

        repository.discardDraft()
        testScheduler.advanceUntilIdle()

        assertTrue(photos.photos.isEmpty())
    }

    @Test
    fun resubmittingAFailedDocument_replacesTheStaleFailure() = runTest {
        val repository = repository()
        repository.startDraftFromQr(SAMPLE_QR)
        val first = repository.submitDraft(sheet, folder)!!
        repository.update(first.copy(status = InvoiceStatus.FAILED))

        repository.startDraftFromQr(SAMPLE_QR)
        val second = repository.submitDraft(sheet, folder)!!

        assertEquals(listOf(second.id), repository.invoices.value.map { it.id })
    }

    @Test
    fun invoicesPersistAcrossInstances_andMerchantNamesAreRemembered() = runTest {
        val first = repository()
        first.startDraftFromQr(SAMPLE_QR)
        first.updateDraft(first.draftInvoice.value!!.copy(merchantName = "Continente"))
        first.submitDraft(sheet, folder)

        val second = repository()
        assertEquals(1, second.invoices.value.size)
        second.startDraftFromQr(SAMPLE_QR.replace("H:AAJFJMM9-4821", "H:AAJFJMM9-9999"))
        assertEquals("Continente", second.draftInvoice.value!!.merchantName)
    }

    @Test
    fun clear_forgetsInvoicesAndPhotos() = runTest {
        val repository = repository()
        repository.startDraftFromQr(SAMPLE_QR)
        repository.attachPhoto(byteArrayOf(1), "image/jpeg")
        repository.submitDraft(sheet, folder)

        repository.clear()
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), repository().invoices.value)
        assertTrue(photos.photos.isEmpty())
    }
}
