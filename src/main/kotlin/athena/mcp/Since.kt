package athena.mcp

import athena.domain.InvalidArgumentException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Parses the `since` argument of `recent_changes`.
 *
 * Today it accepts an ISO date ("2026-10-01"). Relative forms ("7d", "2w", "3 months") are
 * Exercise 1 — see EXERCISES.md and [parseRelativeSince].
 *
 * @return `null` when no `since` was given (meaning "no lower bound").
 */
fun parseSince(input: String?, today: LocalDate = LocalDate.now()): LocalDate? {
    val text = input?.trim()
    if (text.isNullOrEmpty()) return null
    parseRelativeSince(text, today)?.let { return it }
    return try {
        LocalDate.parse(text)
    } catch (e: DateTimeParseException) {
        throw InvalidArgumentException("since must be a date like 2026-10-01 (got '$text')")
    }
}

/**
 * EXERCISE 1 (yours to write): turn "7d", "2w", "3m", "10 days", "2 weeks", "3 months" into the date
 * that many days/weeks/months before [today]. Return `null` for anything that isn't a relative form,
 * so [parseSince] can fall back to ISO dates.
 *
 * Hints: a `Regex` with two groups (number, unit); `when (unit)` over the unit; `today.minusDays(...)`,
 * `minusWeeks`, `minusMonths`. The tests are in `SinceExerciseTest` — remove its `@Disabled` line.
 */
@Suppress("UNUSED_PARAMETER")
fun parseRelativeSince(text: String, today: LocalDate): LocalDate? {
    // TODO(Exercise 1): implement relative dates.
    return null
}
