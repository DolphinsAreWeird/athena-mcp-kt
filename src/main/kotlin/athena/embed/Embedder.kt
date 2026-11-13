package athena.embed

import kotlin.math.sqrt

/** Turns text into a vector for semantic search. Must match what the ingest job used for the stored chunks. */
interface Embedder : AutoCloseable {
    val name: String
    val dimensions: Int
    fun embed(text: String): FloatArray
    override fun close() {}
}

/**
 * Sentence-transformers "mean pooling": average the token vectors, ignoring padding (mask = 0).
 * `tokens` is [sequence length][hidden size].
 */
fun meanPool(tokens: Array<FloatArray>, attentionMask: LongArray): FloatArray {
    require(tokens.size == attentionMask.size) { "tokens (${tokens.size}) and mask (${attentionMask.size}) differ in length" }
    val hidden = tokens.firstOrNull()?.size ?: return FloatArray(0)
    val sum = FloatArray(hidden)
    var count = 0
    for ((i, token) in tokens.withIndex()) {
        if (attentionMask[i] == 0L) continue
        for (d in 0 until hidden) sum[d] += token[d]
        count++
    }
    if (count > 0) for (d in 0 until hidden) sum[d] /= count
    return sum
}

/** Scales a vector to length 1, so cosine similarity becomes a plain dot product. */
fun FloatArray.l2Normalized(): FloatArray {
    val norm = sqrt(sumOf { (it * it).toDouble() }).toFloat()
    return if (norm == 0f) copyOf() else FloatArray(size) { this[it] / norm }
}

fun cosine(a: FloatArray, b: FloatArray): Double {
    require(a.size == b.size) { "vectors differ in length: ${a.size} vs ${b.size}" }
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
}
