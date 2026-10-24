package com.supernutrition.core.config

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RevisionCheckedPrivateDocumentStoreTest {
    @Test
    fun `interleaved owners reject lost updates until the stale owner explicitly reloads`() {
        val document = RevisionDocumentFixture("old meal")
        val first = document.owner()
        val second = document.owner()
        assertEquals("old meal", first.load())
        assertEquals("old meal", second.load())

        assertTrue(first.save("first meal, old meal"))
        assertFalse(second.save("second meal, old meal"))
        assertEquals("first meal, old meal", document.encoded)
        assertEquals(1, document.writes)

        assertEquals("first meal, old meal", second.load())
        assertTrue(second.save("second meal, first meal, old meal"))
        assertEquals("second meal, first meal, old meal", document.owner().load())
    }

    @Test
    fun `a stale owner cannot resurrect a successfully deleted document value`() {
        val document = RevisionDocumentFixture("saved meal")
        val deleting = document.owner()
        val stale = document.owner()
        deleting.load()
        stale.load()

        assertTrue(deleting.save(""))
        assertFalse(stale.save("saved meal, new meal"))
        assertEquals("", document.owner().load())
        assertEquals(1, document.writes)
    }

    @Test
    fun `a missing document is distinct from an explicitly saved empty document`() {
        val document = RevisionDocumentFixture(null)
        val first = document.owner()
        val stale = document.owner()
        assertNull(first.load())
        assertNull(stale.load())

        assertTrue(first.save(""))
        assertFalse(stale.save("replacement"))
        assertEquals("", stale.load())
        document.encoded = null
        assertFalse(stale.save("replacement"))
        assertNull(document.encoded)
        assertEquals(1, document.writes)
    }

    @Test
    fun `an unobserved first save reads existing bytes before its writer runs`() {
        val document = RevisionDocumentFixture("existing")
        val owner = document.owner()
        assertTrue(owner.save("replacement"))
        assertEquals(listOf("read", "write"), document.calls)
        assertEquals("replacement", document.encoded)

        document.encoded = "another owner"
        assertFalse(owner.save("stale replacement"))
        assertEquals(1, document.writes)
    }

    @Test
    fun `an unreadable first save never calls the writer or adopts an initial revision`() {
        val document = RevisionDocumentFixture("existing")
        val owner = document.owner()
        val failure = IllegalStateException("temporarily unreadable")
        document.readFailure = failure
        assertSame(failure, assertFailsWith<IllegalStateException> { owner.save("replacement") })
        assertEquals(0, document.writes)

        document.readFailure = null
        document.encoded = "accepted elsewhere"
        assertTrue(owner.save("replacement"))
        assertEquals("replacement", document.encoded)
    }

    @Test
    fun `a failed reload retains the last observed revision until an explicit successful reload`() {
        val document = RevisionDocumentFixture("observed")
        val owner = document.owner()
        owner.load()
        document.encoded = "accepted elsewhere"
        val failure = IllegalStateException("temporarily unreadable")
        document.readFailure = failure
        assertSame(failure, assertFailsWith<IllegalStateException> { owner.load() })

        document.readFailure = null
        assertFalse(owner.save("stale"))
        assertEquals(0, document.writes)
        assertEquals("accepted elsewhere", owner.load())
        assertTrue(owner.save("recovered"))
    }

    @Test
    fun `a read failure during save preserves the revision and never invokes its writer`() {
        val document = RevisionDocumentFixture("observed")
        val owner = document.owner()
        owner.load()
        val failure = IllegalStateException("read failed")
        document.readFailure = failure
        assertSame(failure, assertFailsWith<IllegalStateException> { owner.save("replacement") })
        assertEquals(0, document.writes)
        assertEquals("observed", document.encoded)

        document.readFailure = null
        document.encoded = "accepted elsewhere"
        assertFalse(owner.save("stale"))
        assertEquals(0, document.writes)
    }

    @Test
    fun `a rejected write preserves an observed revision and permits retry`() {
        val document = RevisionDocumentFixture("observed")
        val owner = document.owner()
        owner.load()
        document.writeResult = false
        assertFalse(owner.save("rejected"))
        assertEquals("observed", document.encoded)

        document.writeResult = true
        assertTrue(owner.save("accepted"))
        assertEquals("accepted", document.encoded)
        assertTrue(owner.save("later accepted"))
    }

    @Test
    fun `a rejected first write does not adopt its read as an observed revision`() {
        val document = RevisionDocumentFixture("existing")
        val owner = document.owner()
        document.writeResult = false
        assertFalse(owner.save("rejected"))

        document.writeResult = true
        document.encoded = "accepted elsewhere"
        assertTrue(owner.save("accepted"))
        assertEquals("accepted", document.encoded)
    }

    @Test
    fun `a throwing writer preserves the observed revision and propagates its exact failure`() {
        val document = RevisionDocumentFixture("observed")
        val owner = document.owner()
        owner.load()
        val failure = IllegalStateException("write failed")
        document.writeFailure = failure
        assertSame(failure, assertFailsWith<IllegalStateException> { owner.save("rejected") })
        assertEquals("observed", document.encoded)

        document.writeFailure = null
        assertTrue(owner.save("accepted"))
        assertEquals("accepted", document.encoded)
    }

    @Test
    fun `callback cancellation propagates without adopting reads or writes`() {
        val document = RevisionDocumentFixture("observed")
        val owner = document.owner()
        owner.load()
        val cancelled = CancellationException("cancelled")
        document.readFailure = cancelled
        assertSame(cancelled, assertFailsWith<CancellationException> { owner.load() })
        assertSame(cancelled, assertFailsWith<CancellationException> { owner.save("cancelled read") })
        assertEquals(0, document.writes)

        document.readFailure = null
        document.writeFailure = cancelled
        assertSame(cancelled, assertFailsWith<CancellationException> { owner.save("cancelled write") })
        assertEquals("observed", document.encoded)
        document.writeFailure = null
        assertTrue(owner.save("accepted"))
    }
}

private class RevisionDocumentFixture(var encoded: String?) {
    val calls = mutableListOf<String>()
    var writes = 0
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    var writeResult = true

    fun owner() = RevisionCheckedPrivateDocumentStore(::read, ::write)

    private fun read(): String? {
        calls += "read"
        readFailure?.let { throw it }
        return encoded
    }

    private fun write(value: String): Boolean {
        calls += "write"
        writes++
        writeFailure?.let { throw it }
        if (!writeResult) return false
        encoded = value
        return true
    }
}
