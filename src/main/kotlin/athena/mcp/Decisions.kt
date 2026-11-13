package athena.mcp

import kotlinx.serialization.Serializable

/**
 * A committee decision, as written in approved minutes:
 *
 * `Decision GSC-2026-08-D2 (M.S. in ECE; effective Spring 2027): The notice to return ... is reduced ...`
 *
 * Athena's Week 3 test found that giving the model the decision register is what got every
 * "a decision overrides the handbook" question right — so a tool that lists decisions is worth having.
 */
@Serializable
data class Decision(
    /** e.g. "GSC-2026-08-D2" */
    val code: String,
    /** e.g. ["M.S. in ECE"] — the text before ';' split on ", " */
    val programmes: List<String>,
    /** e.g. "effective Spring 2027" or "effective immediately" */
    val effective: String,
    /** The decision text after the colon */
    val text: String,
)

/**
 * EXERCISE 3, part 1 (yours to write): find every decision line in a minutes page's markdown.
 *
 * A decision line starts with `Decision `, then the code, then `(programmes; effective ...)`, a colon
 * and the text. Ignore every other line. Hints: one `Regex` with named or numbered groups,
 * `lineSequence()`, `mapNotNull`. Tests: `DecisionsExerciseTest` — remove its `@Disabled` line.
 */
@Suppress("UNUSED_PARAMETER")
fun parseDecisions(markdown: String): List<Decision> {
    // TODO(Exercise 3): parse decision lines.
    return emptyList()
}
