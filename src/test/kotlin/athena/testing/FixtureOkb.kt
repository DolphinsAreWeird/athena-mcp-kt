package athena.testing

import athena.domain.Citation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/** Repository root: Gradle passes it in, so tests work from any working directory. */
val repoRoot: Path = Path.of(System.getenv("ATHENA_REPO_ROOT") ?: ".").toAbsolutePath().normalize()

data class FixtureChunk(val ord: Int, val heading: String?, val text: String)

/** A page of the synthetic OKB in fixtures/okb-mini, loaded the same way the Python ingest does (simplified). */
data class FixturePage(
    val id: String,
    val path: String,
    val programme: String,
    val title: String,
    val type: String?,
    val description: String?,
    val sectionNo: String?,
    val sourceDocument: String?,
    val updatedOn: String?,
    val appliesTo: List<String>,
    val body: String,
) {
    val handbook: String
        get() = when {
            type == "minutes" -> "Committee minutes"
            programme == "general" && sourceDocument?.startsWith("http") == true -> "CMKL website ($sourceDocument)"
            programme == "general" -> "CMKL University ($sourceDocument)"
            else -> "$programme handbook ($sourceDocument)"
        }

    val citation: Citation
        get() = Citation(
            handbook = handbook,
            programme = programme,
            section = sectionNo?.let { "§$it $title" } ?: title,
            path = path,
            sourceDocument = sourceDocument,
            lastUpdated = updatedOn,
        )

    /** One chunk per paragraph, remembering the latest heading. Good enough for tests. */
    fun chunks(): List<FixtureChunk> {
        var heading: String? = null
        val out = mutableListOf<FixtureChunk>()
        for (block in body.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }) {
            if (block.startsWith("#")) {
                heading = block.trimStart('#').trim()
            } else {
                out += FixtureChunk(out.size, heading, block)
            }
        }
        return out
    }
}

object FixtureOkb {
    val root: Path = repoRoot.resolve("fixtures/okb-mini")

    val pages: List<FixturePage> by lazy {
        Files.walk(root).use { paths ->
            paths.filter { it.toString().endsWith(".md") }
                .map { it to it.relativeTo(root).invariantSeparatorsPathString }
                .filter { (_, rel) -> '/' in rel }
                .map { (file, rel) -> parse(file.readText(), rel) }
                .toList()
                .sortedBy { it.id }
        }
    }

    fun page(id: String) = pages.first { it.id == id }

    /** Tiny front-matter reader: `key: value`, quoted strings and `[a, b]` lists. */
    private fun parse(text: String, rel: String): FixturePage {
        val match = Regex("""\A---\n(.*?)\n---\n(.*)\z""", RegexOption.DOT_MATCHES_ALL).find(text)
            ?: error("No front matter in $rel")
        val meta = match.groupValues[1].lines()
            .mapNotNull { line -> line.split(":", limit = 2).takeIf { it.size == 2 } }
            .associate { (k, v) -> k.trim() to v.trim().removeSurrounding("\"") }
        val body = match.groupValues[2].lines().dropWhile { it.isBlank() }.let { lines ->
            if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines
        }.joinToString("\n").trim()
        val programme = meta["program"] ?: rel.substringBefore('/')
        val listed = meta["applies_to"]?.removeSurrounding("[", "]")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        return FixturePage(
            id = rel.removeSuffix(".md"),
            path = rel,
            programme = programme,
            title = meta.getValue("title"),
            type = meta["type"],
            description = meta["description"],
            sectionNo = meta["section"],
            sourceDocument = meta["source_document"],
            updatedOn = meta["timestamp"],
            appliesTo = when {
                listed != null -> if ("general" in listed) emptyList() else listed
                programme == "general" -> emptyList()
                else -> listOf(programme)
            },
            body = body,
        )
    }
}
