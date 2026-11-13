package athena.exercises

import athena.domain.InvalidArgumentException
import athena.mcp.parseSince
import org.junit.jupiter.api.Disabled
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** EXERCISE 1 — see EXERCISES.md. Delete the @Disabled line, then make these pass. */
@Disabled("Exercise 1: implement parseRelativeSince in athena/mcp/Since.kt, then delete this line")
class SinceExerciseTest {
    private val today = LocalDate.parse("2026-11-14")

    @Test
    fun `short forms`() {
        assertEquals(LocalDate.parse("2026-11-07"), parseSince("7d", today))
        assertEquals(LocalDate.parse("2026-10-31"), parseSince("2w", today))
        assertEquals(LocalDate.parse("2026-08-14"), parseSince("3m", today))
    }

    @Test
    fun `long forms, singular and plural, any case`() {
        assertEquals(LocalDate.parse("2026-11-04"), parseSince("10 days", today))
        assertEquals(LocalDate.parse("2026-11-13"), parseSince("1 day", today))
        assertEquals(LocalDate.parse("2026-10-31"), parseSince("2 Weeks", today))
        assertEquals(LocalDate.parse("2026-10-14"), parseSince("1 month", today))
    }

    @Test
    fun `ISO dates still work and junk is still rejected`() {
        assertEquals(LocalDate.parse("2026-10-01"), parseSince("2026-10-01", today))
        assertFailsWith<InvalidArgumentException> { parseSince("7 fortnights", today) }
        assertFailsWith<InvalidArgumentException> { parseSince("d7", today) }
    }
}
