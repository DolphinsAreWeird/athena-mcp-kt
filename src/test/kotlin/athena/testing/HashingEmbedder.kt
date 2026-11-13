package athena.testing

import athena.embed.Embedder
import athena.embed.l2Normalized

/**
 * A deterministic stand-in for the real model: each word adds ±1 to a slot chosen by its hash.
 * Similar wording -> similar vectors, which is all the database tests need, with no 120 MB download.
 */
class HashingEmbedder(override val dimensions: Int = 384) : Embedder {
    override val name = "hashing-test-embedder"

    override fun embed(text: String): FloatArray {
        val vector = FloatArray(dimensions)
        Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase()).forEach { match ->
            val word = match.value.removeSuffix("s")
            val hash = word.hashCode()
            vector[Math.floorMod(hash, dimensions)] += if ((hash ushr 16) % 2 == 0) 1f else -1f
        }
        return vector.l2Normalized()
    }
}
