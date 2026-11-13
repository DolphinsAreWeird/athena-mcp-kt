package athena.mcp

import athena.domain.InvalidArgumentException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SinceTest {
    @Test
    fun `missing or blank since means no lower bound`() {
        assertNull(parseSince(null))
        assertNull(parseSince("  "))
    }

    @Test
    fun `ISO dates are accepted and junk is rejected`() {
        assertEquals(LocalDate.parse("2026-10-01"), parseSince(" 2026-10-01 "))
        assertFailsWith<InvalidArgumentException> { parseSince("last tuesday") }
    }
}
