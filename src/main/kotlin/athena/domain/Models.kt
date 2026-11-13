package athena.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where an answer came from. Every passage, section and change the server returns carries one,
 * so an LLM can always say "according to the M.S. in ECE Student Handbook, §23 ...".
 */
@Serializable
data class Citation(
    /** e.g. "M.S. in ECE Student Handbook (MS-ECE_handbook-2026.pdf)" */
    val handbook: String,
    val programme: String,
    /** e.g. "§23 Returning from a Leave of Absence" */
    val section: String,
    /** Path of the page inside the OKB, e.g. "ece-ms/23-returning-from-a-leave-of-absence.md" */
    val path: String,
    val sourceDocument: String? = null,
    val lastUpdated: String? = null,
)

/** One retrieved passage: the best-matching chunk of a section. */
@Serializable
data class Passage(
    val sectionId: String,
    val title: String,
    val text: String,
    /** Reciprocal-rank-fusion score; only meaningful for ordering. */
    val score: Double,
    /** Which retrievers found it: "lexical", "semantic" or both. */
    val matchedBy: List<String>,
    val citation: Citation,
)

@Serializable
data class Section(
    val id: String,
    val title: String,
    val programme: String,
    val type: String? = null,
    val description: String? = null,
    /** Programmes the page applies to; empty = every programme. */
    val appliesTo: List<String> = emptyList(),
    val body: String,
    val citation: Citation,
)

@Serializable
data class Programme(
    val code: String,
    val name: String,
    val handbook: String? = null,
    val sections: Int,
)

/** One page touched by one commit in the OKB's git history ("Approve: Suspension (aice-beng/33-suspension.md)"). */
@Serializable
data class ChangeEntry(
    val commit: String,
    val date: String,
    val author: String,
    val action: String,
    val title: String? = null,
    val sectionId: String? = null,
    val path: String,
    val programme: String? = null,
    /** A = added, M = modified, D = deleted */
    val status: String,
)

@Serializable
enum class SearchMode {
    @SerialName("hybrid") HYBRID,
    @SerialName("lexical") LEXICAL,
    @SerialName("semantic") SEMANTIC;

    companion object {
        /** Case-insensitive parse; `null` for anything unknown. */
        fun parse(value: String?): SearchMode? =
            value?.let { v -> entries.firstOrNull { it.name.equals(v.trim(), ignoreCase = true) } }
    }
}

@Serializable
data class SearchResponse(
    val query: String,
    val programme: String? = null,
    val mode: SearchMode,
    val results: List<Passage>,
    /** Set when the server had to change how it searched, e.g. semantic search unavailable. */
    val note: String? = null,
)

/**
 * The result of looking a section up by id.
 *
 * A *sealed interface* lists every possible outcome, so a `when` over it must handle each one —
 * the compiler stops you forgetting the not-found case.
 */
sealed interface SectionLookup {
    data class Found(val section: Section) : SectionLookup
    data class NotFound(val id: String, val suggestions: List<String>) : SectionLookup
}

/** Thrown for bad input from a caller; shown to them as a normal error message, not a stack trace. */
class InvalidArgumentException(message: String) : IllegalArgumentException(message)
