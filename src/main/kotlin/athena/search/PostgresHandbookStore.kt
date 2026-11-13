package athena.search

import athena.db.query
import athena.db.stringList
import athena.db.toPgVector
import athena.domain.ChangeEntry
import athena.domain.Citation
import athena.domain.Programme
import athena.domain.Section
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import javax.sql.DataSource

/** [HandbookStore] backed by Postgres full-text search + pgvector. Schema: `db/schema.sql`. */
class PostgresHandbookStore(private val db: DataSource) : HandbookStore {

    override suspend fun lexicalSearch(query: String, programme: String?, limit: Int): List<ChunkHit> =
        db.query(
            """
            WITH q AS (${orQuerySql("?")})
            SELECT $CHUNK_COLUMNS, ts_rank_cd(c.tsv, q.query, 32) AS score
            FROM chunks c JOIN sections s ON s.id = c.section_id, q
            WHERE q.query IS NOT NULL AND c.tsv @@ q.query AND $PROGRAMME_FILTER
            ORDER BY score DESC, c.id
            LIMIT ?
            """,
            bind = {
                setString(1, query)
                bindProgramme(2, programme)
                setInt(4, limit)
            },
            map = { toChunkHit() },
        )

    override suspend fun semanticSearch(vector: FloatArray, programme: String?, limit: Int): List<ChunkHit> {
        val literal = vector.toPgVector()
        return db.query(
            """
            SELECT $CHUNK_COLUMNS, 1 - (c.embedding <=> ?::vector) AS score
            FROM chunks c JOIN sections s ON s.id = c.section_id
            WHERE c.embedding IS NOT NULL AND $PROGRAMME_FILTER
            ORDER BY c.embedding <=> ?::vector, c.id
            LIMIT ?
            """,
            bind = {
                setString(1, literal)
                bindProgramme(2, programme)
                setString(4, literal)
                setInt(5, limit)
            },
            map = { toChunkHit() },
        )
    }

    override suspend fun section(id: String): Section? =
        db.query(
            "SELECT $SECTION_COLUMNS, s.type, s.description, s.applies_to, s.body FROM sections s WHERE s.id = ?",
            bind = { setString(1, id.removeSuffix(".md")) },
            map = {
                Section(
                    id = getString("id"),
                    title = getString("title"),
                    programme = getString("programme"),
                    type = getString("type"),
                    description = getString("description"),
                    appliesTo = stringList("applies_to"),
                    body = getString("body"),
                    citation = toCitation(),
                )
            },
        ).firstOrNull()

    override suspend fun similarSectionIds(id: String, limit: Int): List<String> =
        db.query(
            """
            WITH q AS (${orQuerySql("regexp_replace(?, '[^[:alnum:]]+', ' ', 'g')")})
            SELECT s.id
            FROM sections s, q
            WHERE q.query IS NOT NULL
              AND to_tsvector('english', s.title || ' ' || replace(s.id, '-', ' ')) @@ q.query
            ORDER BY ts_rank_cd(to_tsvector('english', s.title || ' ' || replace(s.id, '-', ' ')), q.query) DESC, s.id
            LIMIT ?
            """,
            bind = {
                setString(1, id)
                setInt(2, limit)
            },
            map = { getString("id") },
        )

    override suspend fun programmes(): List<Programme> =
        db.query(
            """
            SELECT p.code, p.name, p.handbook, count(s.id) AS sections
            FROM programmes p LEFT JOIN sections s ON s.programme = p.code
            GROUP BY p.code, p.name, p.handbook
            ORDER BY p.code
            """,
            map = {
                Programme(getString("code"), getString("name"), getString("handbook"), getInt("sections"))
            },
        )

    override suspend fun changes(since: LocalDate?, programme: String?, limit: Int): List<ChangeEntry> =
        db.query(
            """
            SELECT commit, to_char(committed_at, 'YYYY-MM-DD') AS day, author, action, title, path, section_id,
                   programme, status
            FROM changes
            WHERE (?::date IS NULL OR committed_at >= ?::date)
              AND (?::text IS NULL OR programme = ?)
            ORDER BY committed_at DESC, path
            LIMIT ?
            """,
            bind = {
                val day = since?.toString()
                if (day == null) setNull(1, Types.VARCHAR) else setString(1, day)
                if (day == null) setNull(2, Types.VARCHAR) else setString(2, day)
                if (programme == null) setNull(3, Types.VARCHAR) else setString(3, programme)
                if (programme == null) setNull(4, Types.VARCHAR) else setString(4, programme)
                setInt(5, limit)
            },
            map = {
                ChangeEntry(
                    commit = getString("commit"),
                    date = getString("day"),
                    author = getString("author"),
                    action = getString("action"),
                    title = getString("title"),
                    sectionId = getString("section_id"),
                    path = getString("path"),
                    programme = getString("programme"),
                    status = getString("status"),
                )
            },
        )

    override suspend fun stats(): StoreStats =
        db.query(
            """
            SELECT (SELECT count(*) FROM sections) AS sections,
                   (SELECT count(*) FROM chunks) AS chunks,
                   (SELECT count(*) FROM chunks WHERE embedding IS NOT NULL) AS embedded,
                   (SELECT okb_commit FROM ingest_runs ORDER BY id DESC LIMIT 1) AS okb_commit,
                   (SELECT to_char(finished_at, 'YYYY-MM-DD"T"HH24:MI:SSOF') FROM ingest_runs ORDER BY id DESC LIMIT 1) AS last_ingest
            """,
            map = {
                StoreStats(getInt("sections"), getInt("chunks"), getInt("embedded"), getString("okb_commit"), getString("last_ingest"))
            },
        ).first()

    private companion object {
        const val SECTION_COLUMNS =
            "s.id, s.title, s.programme, s.handbook, s.section_no, s.path, s.source_document, " +
                "to_char(s.updated_on, 'YYYY-MM-DD') AS updated_on"

        const val CHUNK_COLUMNS =
            "c.id AS chunk_id, c.text, s.id, s.title, s.programme, s.handbook, s.section_no, s.path, " +
                "s.source_document, to_char(s.updated_on, 'YYYY-MM-DD') AS updated_on"

        /**
         * A programme filter that keeps the programme's own pages, pages that list it in `applies_to`,
         * and university-wide pages (empty `applies_to`). Takes the programme as two parameters.
         */
        const val PROGRAMME_FILTER = "(?::text IS NULL OR cardinality(s.applies_to) = 0 OR ? = ANY (s.applies_to))"

        /**
         * Turns a natural-language question into an OR full-text query over its stemmed words,
         * so "How much notice before returning from leave?" matches pages with *some* of the words
         * (plainto_tsquery would require *all* of them). Stop words are dropped by the 'english' parser.
         */
        fun orQuerySql(param: String) =
            """
            SELECT to_tsquery('simple', string_agg(quote_literal(lexeme), ' | ')) AS query
            FROM unnest(tsvector_to_array(to_tsvector('english', $param))) AS lexeme
            """

        fun java.sql.PreparedStatement.bindProgramme(index: Int, programme: String?) {
            if (programme == null) {
                setNull(index, Types.VARCHAR)
                setNull(index + 1, Types.VARCHAR)
            } else {
                setString(index, programme)
                setString(index + 1, programme)
            }
        }

        fun ResultSet.toCitation(): Citation {
            val title = getString("title")
            val number = getString("section_no")
            return Citation(
                handbook = getString("handbook"),
                programme = getString("programme"),
                // Some handbook titles already start with their number ("8.3 Returning from ..."): don't repeat it.
                section = if (number.isNullOrBlank() || title.startsWith(number)) title else "§$number $title",
                path = getString("path"),
                sourceDocument = getString("source_document"),
                lastUpdated = getString("updated_on"),
            )
        }

        fun ResultSet.toChunkHit() = ChunkHit(
            chunkId = getLong("chunk_id"),
            sectionId = getString("id"),
            title = getString("title"),
            text = getString("text"),
            score = getDouble("score"),
            citation = toCitation(),
        )
    }
}
