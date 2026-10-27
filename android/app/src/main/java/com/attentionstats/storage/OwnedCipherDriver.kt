package com.attentionstats.storage

import android.database.sqlite.SQLiteException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import java.util.concurrent.atomic.AtomicInteger

/** Never delete/retry a damaged generation and never log paths, SQL or key material. */
internal class OwnedCipherDriver(key: ByteArray) : SQLiteDriver {
    private val ownedKey = key.copyOf()
    private val active = AtomicInteger()
    private var retired = false
    private val delegate = SQLCipherDriver(ownedKey, null, object : DatabaseErrorHandler {
        override fun onCorruption(dbObj: SQLiteDatabase, exception: SQLiteException) {
            throw SQLiteException("Encrypted history unavailable; original files preserved")
        }
    })
    override val hasConnectionPool: Boolean get() = delegate.hasConnectionPool()

    @Synchronized
    override fun open(fileName: String): SQLiteConnection {
        check(!retired)
        val connection = delegate.open(fileName)
        active.incrementAndGet()
        return object : SQLiteConnection by connection {
            private var closed = false
            @Synchronized override fun close() {
                if (!closed) {
                    connection.close()
                    closed = true
                    active.decrementAndGet()
                }
            }
        }
    }

    @Synchronized fun retire() {
        check(active.get() == 0)
        ownedKey.fill(0)
        retired = true
    }
}
