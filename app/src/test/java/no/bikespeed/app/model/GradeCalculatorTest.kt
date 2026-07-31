package no.bikespeed.app.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Grade is rise over run across a trailing window of distance — never fix-to-fix, where a ~5 m
 * distance delta against ±1 m of altitude noise is meaningless.
 */
class GradeCalculatorTest {

    @Test
    fun `grade is null until the window fills`() {
        val calculator = GradeCalculator(windowDistance = 30.0)
        calculator.add(distance = 0.0, altitude = 100.0)
        calculator.add(distance = 10.0, altitude = 100.5)
        calculator.add(distance = 20.0, altitude = 101.0)

        assertWithMessage("20 m of riding cannot fill a 30 m window")
            .that(calculator.grade).isNull()
    }

    @Test
    fun `a steady climb reads its true slope`() {
        val calculator = GradeCalculator(windowDistance = 30.0)
        for (step in 0..8) {
            calculator.add(distance = step * 10.0, altitude = 100 + step * 0.5) // 5 %
        }

        val grade = calculator.grade
        assertNotNull(grade)
        assertThat(grade!!).isWithin(0.001).of(0.05)
    }

    @Test
    fun `flat ground reads zero`() {
        val calculator = GradeCalculator(windowDistance = 30.0)
        for (step in 0..8) {
            calculator.add(distance = step * 10.0, altitude = 100.0)
        }

        val grade = calculator.grade
        assertNotNull(grade)
        assertThat(grade!!).isWithin(0.001).of(0.0)
    }

    @Test
    fun `a descent reads negative`() {
        val calculator = GradeCalculator(windowDistance = 30.0)
        for (step in 0..8) {
            calculator.add(distance = step * 10.0, altitude = 100 - step * 0.8) // -8 %
        }

        val grade = calculator.grade
        assertNotNull(grade)
        assertThat(grade!!).isWithin(0.001).of(-0.08)
    }

    /** The window slides: a hill crested 60 m ago must not still be reported as the current gradient. */
    @Test
    fun `an old climb falls out of the window`() {
        val calculator = GradeCalculator(windowDistance = 30.0)
        for (step in 0..4) {
            calculator.add(distance = step * 10.0, altitude = 100.0 + step) // 40 m at 10 %
        }
        for (step in 1..6) {
            calculator.add(distance = 40 + step * 10.0, altitude = 104.0) // then 60 m dead flat
        }

        val grade = calculator.grade
        assertNotNull(grade)
        assertThat(grade!!).isWithin(0.001).of(0.0)
    }

    @Test
    fun `reset forgets the ride so far`() {
        val calculator = GradeCalculator(windowDistance = 30.0)
        for (step in 0..8) {
            calculator.add(distance = step * 10.0, altitude = 100 + step * 0.5)
        }

        calculator.reset()

        assertThat(calculator.grade).isNull()
        calculator.add(distance = 0.0, altitude = 200.0)
        assertWithMessage("the old samples must not survive into the next trip")
            .that(calculator.grade).isNull()
    }
}
