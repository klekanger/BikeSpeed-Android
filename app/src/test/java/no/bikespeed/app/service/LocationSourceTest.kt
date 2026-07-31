package no.bikespeed.app.service

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import no.bikespeed.app.fixtures.FakeLocationSource
import no.bikespeed.app.fixtures.makeFix
import org.junit.Test

/**
 * Pins the seam's load-bearing contract: fix delivery is **synchronous** and in registration
 * order. Dozens of trip tests assert state on the line after feeding a fix, and the
 * auto-pause ordering depends on it — which is why the seam is a plain listener registry and
 * not a SharedFlow (whose collector delivery is dispatcher-scheduled).
 */
class LocationSourceTest {

    @Test
    fun `fixes are delivered synchronously, before emit returns`() {
        val source = FakeLocationSource()
        var delivered = false
        source.addFixListener { delivered = true }

        source.emit(makeFix())

        assertWithMessage("the listener must have run before emit() returned")
            .that(delivered).isTrue()
    }

    @Test
    fun `listeners run in registration order`() {
        val source = FakeLocationSource()
        val order = mutableListOf<String>()
        source.addFixListener { order.add("first") }
        source.addFixListener { order.add("second") }

        source.emit(makeFix())

        assertThat(order).containsExactly("first", "second").inOrder()
    }
}
