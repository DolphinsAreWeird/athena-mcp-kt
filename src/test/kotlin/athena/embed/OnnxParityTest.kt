package athena.embed

import athena.config.AppConfig
import athena.testing.repoRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Kotlin server and the Python ingest job must embed text identically, or query vectors and
 * stored vectors live in different spaces and semantic search silently degrades. This compares the
 * Kotlin embedder with reference vectors written by `athena-ingest parity`.
 * Skipped when the model isn't downloaded (as in CI).
 */
class OnnxParityTest {
    @Serializable
    data class Case(val text: String, val vector: List<Float>)

    @Serializable
    data class Reference(val model: String, val cases: List<Case>)

    @Test
    fun `Kotlin embeddings match the Python ingest job`() {
        val modelDir = System.getenv("ATHENA_MODEL_DIR")?.let { Path.of(it) } ?: AppConfig.defaultModelDir()
        assumeTrue(Files.isDirectory(modelDir), "No model in $modelDir; run `athena-ingest download-model`")
        val reference = Json.decodeFromString<Reference>(repoRoot.resolve("fixtures/embedding-parity.json").readText())

        OnnxEmbedder(modelDir).use { embedder ->
            assertEquals(384, embedder.dimensions)
            for (case in reference.cases) {
                val similarity = cosine(embedder.embed(case.text), case.vector.toFloatArray())
                assertTrue(similarity > 0.999, "cosine $similarity for '${case.text}'")
            }
        }
    }
}
