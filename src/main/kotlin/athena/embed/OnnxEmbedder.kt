package athena.embed

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Runs paraphrase-multilingual-MiniLM-L12-v2 in-process with ONNX Runtime — the same `.onnx` file
 * and the same tokenizer the Python ingest job used — so query vectors live in the same space as the
 * stored chunk vectors. No Python or model server is needed at query time.
 *
 * Pipeline: tokenize (HF tokenizers via DJL) -> transformer (ONNX) -> mean pooling -> L2 normalise.
 */
class OnnxEmbedder(
    modelDir: Path,
    private val maxTokens: Int = 128,
) : Embedder {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val modelFile: Path = findModel(modelDir)
    private val session: OrtSession = environment.createSession(modelFile.toString(), OrtSession.SessionOptions())
    private val tokenizer: HuggingFaceTokenizer = HuggingFaceTokenizer.newInstance(
        modelDir.resolve("tokenizer.json"),
        mapOf(
            "maxLength" to maxTokens.toString(),
            "truncation" to "true",
            "padding" to "false",
            "addSpecialTokens" to "true",
        ),
    )
    private val needsTokenTypes = "token_type_ids" in session.inputNames

    override val name: String = "onnx:${modelDir.name}/${modelFile.name}"

    // `by lazy`: computed the first time someone reads it, then cached.
    override val dimensions: Int by lazy { embed("dimension probe").size }

    override fun embed(text: String): FloatArray {
        val encoding = tokenizer.encode(text)
        val ids = encoding.ids
        val mask = encoding.attentionMask
        val inputs = buildMap {
            put("input_ids", OnnxTensor.createTensor(environment, arrayOf(ids)))
            put("attention_mask", OnnxTensor.createTensor(environment, arrayOf(mask)))
            if (needsTokenTypes) put("token_type_ids", OnnxTensor.createTensor(environment, arrayOf(encoding.typeIds)))
        }
        try {
            session.run(inputs).use { result ->
                @Suppress("UNCHECKED_CAST")
                val tokenVectors = (result[0].value as Array<Array<FloatArray>>)[0]
                return meanPool(tokenVectors, mask).l2Normalized()
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    override fun close() {
        session.close()
        tokenizer.close()
    }

    companion object {
        /** Prefer `model.onnx`; otherwise the only `.onnx` file in the folder (e.g. a quantised one). */
        fun findModel(dir: Path): Path {
            require(Files.isDirectory(dir)) { "Model folder not found: $dir (run `athena-ingest download-model`)" }
            require(Files.exists(dir.resolve("tokenizer.json"))) { "No tokenizer.json in $dir" }
            dir.resolve("model.onnx").takeIf { Files.exists(it) }?.let { return it }
            val candidates = dir.listDirectoryEntries().filter { it.extension == "onnx" }.sorted()
            return candidates.singleOrNull()
                ?: error("Expected one .onnx file in $dir, found ${candidates.map { it.name }}")
        }
    }
}
