package org.attentionstats.qualification.storage

import android.database.sqlite.SQLiteException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import java.util.concurrent.atomic.AtomicInteger

/** Never logs paths or deletes/retries a damaged database. */
class PreserveOnlyHandler : DatabaseErrorHandler {
    val calls = AtomicInteger()

    override fun onCorruption(
        dbObj: net.zetetic.database.sqlcipher.SQLiteDatabase,
        exception: SQLiteException,
    ) {
        calls.incrementAndGet()
        throw SQLiteException("Synthetic database preserved")
    }
}

/** Owns the exact supplied array until Room has closed every driver connection. */
class OwnedCipherDriver(key: ByteArray) : SQLiteDriver {
    private val ownedKey = key.copyOf()
    val handler = PreserveOnlyHandler()
    private val delegate = SQLCipherDriver(ownedKey, null, handler)
    private val active = AtomicInteger()
    val opened = AtomicInteger()
    private var retired = false

    override val hasConnectionPool: Boolean get() = delegate.hasConnectionPool()

    @Synchronized
    override fun open(fileName: String): SQLiteConnection {
        check(!retired)
        val connection = delegate.open(fileName)
        active.incrementAndGet()
        opened.incrementAndGet()
        return object : SQLiteConnection by connection {
            private var closed = false

            @Synchronized
            override fun close() {
                if (!closed) {
                    // Only count a successful close as proof that key retirement is safe.
                    connection.close()
                    closed = true
                    active.decrementAndGet()
                }
            }
        }
    }

    @Synchronized
    fun retire() {
        check(active.get() == 0)
        ownedKey.fill(0)
        retired = true
        check(ownedKey.all { it == 0.toByte() })
    }
}
