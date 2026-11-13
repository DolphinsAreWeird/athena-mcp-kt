package athena.mcp

import athena.domain.ChangeEntry
import athena.domain.Citation
import athena.domain.Programme
import athena.domain.SearchResponse
import athena.domain.Section

/**
 * Plain-text renderings of results for the LLM. Each one ends with how to cite, because an answer
 * without its source is the failure this whole project exists to prevent.
 */
object Formatting {

    fun citationLine(c: Citation): String = buildString {
        append("${c.handbook} — ${c.section}")
        append(" [${c.path}")
        c.lastUpdated?.let { append(", updated $it") }
        append("]")
    }

    fun search(response: SearchResponse): String = buildString {
        val scope = response.programme?.let { " in $it" } ?: ""
        if (response.results.isEmpty()) {
            appendLine("No handbook passages matched \"${response.query}\"$scope.")
            appendLine("Try other words (the handbooks are in English), drop the programme filter, or call list_programmes.")
        } else {
            appendLine("${response.results.size} passages for \"${response.query}\"$scope (${response.mode.name.lowercase()} search):")
            response.results.forEachIndexed { i, p ->
                appendLine()
                appendLine("[${i + 1}] ${p.title}")
                appendLine("Source: ${citationLine(p.citation)}")
                appendLine("Section id: ${p.sectionId}")
                appendLine(p.text.trim().prependIndent("> "))
            }
            appendLine()
            appendLine("Cite each fact as [n] with its handbook and section. Call get_section with a section id to read the whole section.")
            appendLine("Pages under minutes/ are committee decisions; check their effective dates — they can override handbook text.")
        }
        response.note?.let { appendLine().appendLine("Note: $it") }
    }.trimEnd()

    fun section(s: Section): String = buildString {
        appendLine("# ${s.title}")
        appendLine("Source: ${citationLine(s.citation)}")
        appendLine("Section id: ${s.id} · programme: ${s.programme}" + (s.type?.let { " · type: $it" } ?: ""))
        if (s.appliesTo.isNotEmpty() && s.appliesTo != listOf(s.programme)) {
            appendLine("Applies to: ${s.appliesTo.joinToString()}")
        }
        appendLine()
        appendLine(s.body.trim())
    }.trimEnd()

    fun programmes(list: List<Programme>): String = buildString {
        appendLine("Programmes in the Athena knowledge base (use the code as the `programme` argument):")
        list.forEach { p ->
            appendLine("- ${p.code}: ${p.name} — ${p.sections} sections" + (p.handbook?.let { "; source: $it" } ?: ""))
        }
        append("Pages under 'general' apply to every programme and are included in programme-filtered searches.")
    }

    fun changes(list: List<ChangeEntry>, sinceLabel: String?): String = buildString {
        if (list.isEmpty()) {
            append("No approved changes" + (sinceLabel?.let { " since $it" } ?: "") + ".")
            return@buildString
        }
        appendLine("${list.size} changes to the knowledge base" + (sinceLabel?.let { " since $it" } ?: "") + ", newest first:")
        list.forEach { c ->
            val what = c.title ?: c.path
            val status = when (c.status) {
                "A" -> "added"
                "D" -> "deleted"
                else -> "changed"
            }
            appendLine("- ${c.date} · ${c.action} · $what ($status) — ${c.path} · by ${c.author} · commit ${c.commit}")
        }
        append("Each change was approved in Athena's review portal; cite the page path and date.")
    }
}
