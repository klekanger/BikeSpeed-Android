package no.bikespeed.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pins that the JVM test toolchain (JUnit 4 + Truth) runs at all. */
class SmokeTest {
    @Test
    fun `the test toolchain runs`() {
        assertThat(1 + 1).isEqualTo(2)
    }
}
