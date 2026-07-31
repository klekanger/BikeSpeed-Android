package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompassDirectionTest {

    /** The middle of each 45° sector maps to that sector's direction. */
    @Test
    fun `sector midpoint maps to its direction`() {
        val cases = listOf(
            0.0 to CompassDirection.N,
            45.0 to CompassDirection.NE,
            90.0 to CompassDirection.E,
            135.0 to CompassDirection.SE,
            180.0 to CompassDirection.S,
            225.0 to CompassDirection.SW,
            270.0 to CompassDirection.W,
            315.0 to CompassDirection.NW,
        )
        for ((course, expected) in cases) {
            assertThat(CompassDirection.fromCourse(course)).isEqualTo(expected)
        }
    }

    /**
     * North straddles 0°, so its sector runs 337.5°..<22.5° — the one boundary that wraps,
     * and the one a naive `course / 45` would get wrong.
     */
    @Test
    fun `north sector wraps around zero`() {
        for (course in listOf(337.5, 350.0, 359.9, 0.0, 22.4)) {
            assertThat(CompassDirection.fromCourse(course)).isEqualTo(CompassDirection.N)
        }
    }

    /** 22.5° is the first bearing that belongs to NE rather than N. */
    @Test
    fun `sector boundary rounds up to the next direction`() {
        assertThat(CompassDirection.fromCourse(22.4)).isEqualTo(CompassDirection.N)
        assertThat(CompassDirection.fromCourse(22.5)).isEqualTo(CompassDirection.NE)
    }

    /**
     * A GPS course should never be negative or beyond 360, but `fromCourse` normalizes
     * rather than trapping, and that promise is worth holding it to.
     */
    @Test
    fun `courses outside 0 to 360 are normalized`() {
        val cases = listOf(
            -45.0 to CompassDirection.NW,
            -90.0 to CompassDirection.W,
            405.0 to CompassDirection.NE,
            720.0 to CompassDirection.N,
        )
        for ((course, expected) in cases) {
            assertThat(CompassDirection.fromCourse(course)).isEqualTo(expected)
        }
    }
}
