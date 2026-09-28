package org.neteinstein.snap2sheet

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.neteinstein.snap2sheet.data.remote.SheetsException
import org.neteinstein.snap2sheet.data.repository.DefaultInvoiceRepository
import org.neteinstein.snap2sheet.data.repository.DefaultSettingsRepository
import org.neteinstein.snap2sheet.data.sync.InvoiceSyncer
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.testing.FakeSheetsGateway
import org.neteinstein.snap2sheet.testing.FixedClock
import org.neteinstein.snap2sheet.testing.InMemoryKeyValueStore
import org.neteinstein.snap2sheet.testing.InMemoryPhotoStore
import org.neteinstein.snap2sheet.testing.RecordingNotifier
import org.neteinstein.snap2sheet.testing.RecordingScheduler
import org.neteinstein.snap2sheet.testing.SAMPLE_QR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class InvoiceSyncerTest {

    private val store = InMemoryKeyValueStore()
    private val photos = InMemoryPhotoStore()
    private val gateway = FakeSheetsGateway()
    private val notifier = RecordingNotifier()
    private val scheduler = RecordingScheduler()
    private val clock = FixedClock()
    private val reports = mutableListOf<String>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private class Setup(val repository: DefaultInvoiceRepository, val syncer: InvoiceSyncer)

    private fun TestScope.setup(): Setup {
        val repository = DefaultInvoiceRepository(store, photos, json, backgroundScope, clock)
        val syncer = InvoiceSyncer(repository, gateway, DefaultSettingsRepository(store), notifier, scheduler, { reports += it }, backgroundScope, clock)
        return Setup(repository, syncer)
    }

    private suspend fun Setup.queueInvoice(withPhoto: Boolean = true): String {
        repository.startDraftFromQr(SAMPLE_QR)
        repository.updateDraft(repository.draftInvoice.value!!.copy(merchantName = "Continente"))
        if (withPhoto) repository.attachPhoto(ByteArray(10) { it.toByte() }, "image/jpeg")
        return repository.submitDraft(DriveItem("sheet-1", "Despesas 2026"), DriveItem("folder-1", "Faturas"))!!.id
    }

    @Test
    fun processDue_uploadsThePhotoThenAppendsTheRowWithItsLink() = runTest {
        val s = setup()
        val id = s.queueInvoice()

        s.syncer.processDue()

        val saved = s.repository.invoices.value.single { it.id == id }
        assertEquals(InvoiceStatus.SYNCED, saved.status)
        assertEquals(Triple("folder-1", "2026-09-18 Continente FS 2026-004821.jpg", 10), gateway.uploads.single())
        assertEquals(saved.driveFileLink, gateway.appended.single().second.driveFileLink)
        assertNull(saved.photo, "the local copy is dropped once it's in Drive")
        testScheduler.runCurrent()
        assertTrue(photos.photos.isEmpty())
        assertEquals("Saved Continente · €18.42", notifier.notifications.single().first)
    }

    @Test
    fun withoutAPhoto_onlyTheRowIsAppended() = runTest {
        val s = setup()
        s.queueInvoice(withPhoto = false)

        s.syncer.processDue()

        assertTrue(gateway.uploads.isEmpty())
        assertEquals(1, gateway.appended.size)
    }

    @Test
    fun duplicates_areSkippedBeforeUploadingAnything() = runTest {
        val s = setup()
        val id = s.queueInvoice()
        gateway.duplicate = true

        s.syncer.processDue()

        assertEquals(InvoiceStatus.DUPLICATE, s.repository.invoices.value.single { it.id == id }.status)
        assertTrue(gateway.uploads.isEmpty())
        assertTrue(notifier.notifications.single().first.startsWith("Already saved"))
    }

    @Test
    fun transientFailures_areRetriedWithBackoff_withoutUploadingTwice() = runTest {
        val s = setup()
        val id = s.queueInvoice()
        gateway.failNextAppendWith = SheetsException("Offline.", SheetsException.Kind.NETWORK)

        s.syncer.processDue()

        val waiting = s.repository.invoices.value.single { it.id == id }
        assertEquals(InvoiceStatus.QUEUED, waiting.status)
        assertEquals(1, waiting.attempts)
        assertTrue(waiting.errorMessage!!.contains("Retrying"))
        assertTrue(notifier.notifications.isEmpty(), "no notification until it's finished")

        s.syncer.processDue() // Not due yet: nothing happens.
        assertTrue(gateway.appended.isEmpty())

        clock.now += 1.minutes
        s.syncer.processDue()

        assertEquals(InvoiceStatus.SYNCED, s.repository.invoices.value.single { it.id == id }.status)
        assertEquals(1, gateway.uploads.size, "the photo uploaded on the first attempt isn't uploaded again")
    }

    @Test
    fun permanentFailures_failImmediately_andCanBeRetriedByHand() = runTest {
        val s = setup()
        val id = s.queueInvoice()
        gateway.failWith = SheetsException("Your Google session expired.", SheetsException.Kind.NOT_SIGNED_IN)

        s.syncer.processDue()

        val failed = s.repository.invoices.value.single { it.id == id }
        assertEquals(InvoiceStatus.FAILED, failed.status)
        assertTrue(notifier.notifications.single().first.startsWith("Couldn't save"))

        gateway.failWith = null
        s.syncer.retry(id)
        s.syncer.processDue()

        assertEquals(InvoiceStatus.SYNCED, s.repository.invoices.value.single { it.id == id }.status)
    }

    @Test
    fun givesUpAfterTheLastAutomaticRetry() = runTest {
        val s = setup()
        val id = s.queueInvoice()
        gateway.failWith = SheetsException("Offline.", SheetsException.Kind.NETWORK)

        repeat(InvoiceSyncer.MAX_ATTEMPTS) {
            s.syncer.processDue()
            clock.now += 30.minutes
        }

        assertEquals(InvoiceStatus.FAILED, s.repository.invoices.value.single { it.id == id }.status)
    }

    @Test
    fun queuedWork_survivesARestart() = runTest {
        val first = setup()
        first.queueInvoice()

        val restarted = setup()
        restarted.syncer.processDue()

        assertEquals(InvoiceStatus.SYNCED, restarted.repository.invoices.value.single().status)
    }

    @Test
    fun notificationsCanBeTurnedOff() = runTest {
        DefaultSettingsRepository(store).setNotifyWhenSaveFinishes(false)
        val s = setup()
        s.queueInvoice()

        s.syncer.processDue()

        assertTrue(notifier.notifications.isEmpty())
        assertEquals(1, reports.size, "the in-app message still shows")
    }

    @Test
    fun kick_schedulesBackgroundWork() = runTest {
        val s = setup()
        s.queueInvoice()

        s.syncer.kick()
        testScheduler.runCurrent()

        assertEquals(1, scheduler.scheduled)
        assertEquals(InvoiceStatus.SYNCED, s.repository.invoices.value.single().status)
    }
}
