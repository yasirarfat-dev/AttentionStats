package com.attentionstats.qualification

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.SystemClock
import com.attentionstats.backup.SystemBackupState
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Controlled overlap against the production guard; no framework calls, preferences or live store. */
internal object BackupAdmissionVerification {
    fun run(instrumentation: Instrumentation): Int {
        val target = instrumentation.targetContext
        val root = File(target.cacheDir, "backup-admission-${UUID.randomUUID()}")
        check(root.mkdir())
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = root
        }
        val workers = Executors.newFixedThreadPool(2)
        val release = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val bytes = "synthetic delivered archive".toByteArray()
        var checks = 0
        fun expect(value: Boolean) { check(value) { "backup admission check ${checks + 1}" }; checks++ }
        fun await(latch: CountDownLatch) = check(latch.await(5, TimeUnit.SECONDS))
        fun blocked(thread: AtomicReference<Thread?>): Boolean {
            val until = SystemClock.elapsedRealtime() + 3_000
            while (SystemClock.elapsedRealtime() < until) {
                if (thread.get()?.state == Thread.State.BLOCKED) return true
                SystemClock.sleep(10)
            }
            return false
        }
        val offered = AtomicInteger()
        fun rejected(): Boolean = try {
            SystemBackupState.withBackupAdmission(context) { offered.incrementAndGet() }
            false
        } catch (_: IllegalStateException) { true }
        try {
            // Reproduce the former separated-check window, without invoking a real transport.
            val initiallyAdmitted = !SystemBackupState.hasPending(context)
            expect(initiallyAdmitted)
            workers.submit { SystemBackupState.writePending(context, bytes) }.get(5, TimeUnit.SECONDS)
            expect(initiallyAdmitted && SystemBackupState.hasPending(context))
            expect(rejected())
            expect(offered.get() == 0)
            SystemBackupState.clearPending(context)

            // Snapshot work intentionally holds no lock. Delivery wins before final admission.
            val snapshotStarted = CountDownLatch(1)
            val snapshotReady = CountDownLatch(1)
            val exported = workers.submit<Boolean> {
                snapshotStarted.countDown(); await(snapshotReady)
                rejected()
            }
            await(snapshotStarted)
            SystemBackupState.writePending(context, bytes)
            snapshotReady.countDown()
            expect(exported.get(5, TimeUnit.SECONDS))
            expect(offered.get() == 0)
            SystemBackupState.clearPending(context)

            // Admission wins: delivery cannot commit inside the guarded handoff callback.
            val handingOff = CountDownLatch(1)
            val deliveryThread = AtomicReference<Thread?>()
            val handoff = workers.submit {
                SystemBackupState.withBackupAdmission(context) {
                    handingOff.countDown(); await(release)
                    check(!SystemBackupState.hasPending(context))
                    offered.incrementAndGet()
                }
            }
            await(handingOff)
            val delivery = workers.submit {
                deliveryThread.set(Thread.currentThread())
                SystemBackupState.writePending(context, bytes)
            }
            expect(blocked(deliveryThread))
            expect(!delivery.isDone && offered.get() == 0)
            release.countDown()
            handoff.get(5, TimeUnit.SECONDS); delivery.get(5, TimeUnit.SECONDS)
            expect(offered.get() == 1 && SystemBackupState.hasPending(context))
            expect(SystemBackupState.readPending(context) { it.readBytes().contentEquals(bytes) })

            // A pending-file operation wins: admission waits, then rejects the delivered archive.
            val reading = CountDownLatch(1)
            val admissionThread = AtomicReference<Thread?>()
            val read = workers.submit {
                SystemBackupState.readPending(context) {
                    reading.countDown(); await(releaseRead)
                }
            }
            await(reading)
            val admission = workers.submit<Boolean> {
                admissionThread.set(Thread.currentThread()); rejected()
            }
            expect(blocked(admissionThread))
            expect(!admission.isDone)
            releaseRead.countDown()
            read.get(5, TimeUnit.SECONDS)
            expect(admission.get(5, TimeUnit.SECONDS))
            expect(offered.get() == 1)
            SystemBackupState.clearPending(context)

            for (suffix in listOf(".bak", ".new")) {
                File(SystemBackupState.pending(context).path + suffix).writeBytes(bytes)
                expect(rejected())
                SystemBackupState.clearPending(context)
            }
            var threw = false
            try { SystemBackupState.withBackupAdmission(context) { throw java.io.IOException("synthetic handoff failure") } }
            catch (_: java.io.IOException) { threw = true }
            expect(threw)
            workers.submit { SystemBackupState.writePending(context, bytes) }.get(5, TimeUnit.SECONDS)
            expect(SystemBackupState.hasPending(context))
            expect(offered.get() == 1)
            return checks
        } finally {
            release.countDown(); releaseRead.countDown()
            workers.shutdownNow()
            check(workers.awaitTermination(10, TimeUnit.SECONDS))
            bytes.fill(0)
            check(root.parentFile?.canonicalFile == target.cacheDir.canonicalFile)
            check(root.deleteRecursively())
        }
    }
}
