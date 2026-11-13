# Learning Kotlin through this project

This is Khant's first Kotlin project. This file maps every Kotlin (and Ktor, coroutines, MCP) idea the
code uses: one line on what it is, and where to look. Read it next to the code. The exercises in
[EXERCISES.md](EXERCISES.md) are the parts to write by hand.

Quick orientation: [`Main.kt`](src/main/kotlin/athena/Main.kt) wires everything →
[`HandbookService`](src/main/kotlin/athena/search/HandbookService.kt) answers questions →
[`PostgresHandbookStore`](src/main/kotlin/athena/search/PostgresHandbookStore.kt) runs the SQL;
[`AthenaMcpServer.kt`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt) and
[`Routes.kt`](src/main/kotlin/athena/http/Routes.kt) are thin adapters for MCP and REST.

## The language

| Concept | In one line | Where |
|---|---|---|
| `data class` | A class for holding values: the compiler writes `equals`, `hashCode`, `toString`, `copy` and destructuring for you. | [`Citation`, `Passage`, `Section`](src/main/kotlin/athena/domain/Models.kt:11), [`AppConfig`](src/main/kotlin/athena/config/AppConfig.kt) |
| `val` vs `var` | `val` can't be reassigned (prefer it); `var` can. Almost everything here is `val`. | everywhere; the two `var`s in [`HandbookService.search`](src/main/kotlin/athena/search/HandbookService.kt:29) are deliberate |
| Null safety: `String?`, `?.`, `?:` | A type ending in `?` may be null and the compiler makes you handle it; `a?.b` is "b if a isn't null", `x ?: y` is "x, or y if x is null". | [`AppConfig.fromEnv`](src/main/kotlin/athena/config/AppConfig.kt:25), [`/search` params](src/main/kotlin/athena/http/Routes.kt:72) |
| Scope functions `let`, `apply`, `takeIf`, `also`, `run` | Small helpers that run a block on a value: `x?.let { }` only if not null, `apply { }` to configure an object, `takeIf { }` to keep a value only if a test passes. | [`fromEnv`](src/main/kotlin/athena/config/AppConfig.kt:25), [`createDataSource`](src/main/kotlin/athena/db/Database.kt) (`apply`), [`loadEmbedder`](src/main/kotlin/athena/Main.kt) (`run`) |
| Extension functions | Add a function to an existing type without touching it: `"postgresql://…".toDatabaseConfig()`. | [`String.toDatabaseConfig`](src/main/kotlin/athena/config/AppConfig.kt:59), [`DataSource.query`](src/main/kotlin/athena/db/Database.kt:38), [`FloatArray.l2Normalized`](src/main/kotlin/athena/embed/Embedder.kt:32), [`Server.registerHandbookTools`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt:55) |
| Extension properties | The same for properties: `request.args` is a never-null view of the tool arguments. | [`CallToolRequest.args`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt:214) |
| Lambdas with receiver (`T.() -> Unit`) | Inside the lambda, `this` is a `T`, so you call its methods directly. This is how Kotlin "DSLs" like `buildJsonObject { put(...) }` and Ktor's `routing { get(...) }` work. | [`query(bind = { setString(1, q) })`](src/main/kotlin/athena/db/Database.kt:38), [`buildJsonObject`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt:63), [`buildString`](src/main/kotlin/athena/mcp/Formatting.kt:15) |
| `sealed interface` + exhaustive `when` | A closed set of outcomes; a `when` over it must handle every case or it won't compile. | [`SectionLookup`](src/main/kotlin/athena/domain/Models.kt:101) is `Found` or `NotFound`, handled in [`get_section`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt:124) |
| `when` as an expression | A `switch` that returns a value and can match types (`is X ->`) or values. | [`main`'s command switch](src/main/kotlin/athena/Main.kt:48), [`Formatting.changes`](src/main/kotlin/athena/mcp/Formatting.kt) |
| `enum class` with a companion | Fixed set of named values; the `companion object` holds a static-like `parse`. | [`SearchMode`](src/main/kotlin/athena/domain/Models.kt:73) |
| `object` and `companion object` | `object` = a singleton; `companion object` = members called on the class itself (like Java `static`). `const val` = compile-time constant. | [`BuildInfo`](src/main/kotlin/athena/Main.kt:26), [`Formatting`](src/main/kotlin/athena/mcp/Formatting.kt:13), [`AppConfig.Companion`](src/main/kotlin/athena/config/AppConfig.kt:22), [`PostgresHandbookStore`'s SQL constants](src/main/kotlin/athena/search/PostgresHandbookStore.kt:153) |
| Interfaces for testability | Code depends on `HandbookStore` / `Embedder`, not on Postgres or ONNX, so tests swap in fakes. | [`HandbookStore`](src/main/kotlin/athena/search/HandbookStore.kt:34), [`Embedder`](src/main/kotlin/athena/embed/Embedder.kt:6), [`FakeHandbookStore`](src/test/kotlin/athena/testing/FakeHandbookStore.kt), [`HashingEmbedder`](src/test/kotlin/athena/testing/HashingEmbedder.kt) |
| Default and named arguments | Parameters can have defaults, and callers can name them: `search(query, programme = "ece-ms")`. Replaces Java's overloads and builders. | [`HandbookService.search`](src/main/kotlin/athena/search/HandbookService.kt:29), [`Server.addTool(...)`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt:55) |
| Destructuring | Unpack a data class or pair into variables: `for ((name, hits) in map)`, `{ (hit, score, matchedBy) -> }`. | [`Fusion.kt`](src/main/kotlin/athena/search/Fusion.kt:21), [`HandbookService`](src/main/kotlin/athena/search/HandbookService.kt:62) |
| Collections API | `map`, `filter`, `distinctBy`, `groupBy`, `associateBy`, `buildList`, `getOrPut`, `merge`: data transformations without loops. | [`reciprocalRankFusion`](src/main/kotlin/athena/search/Fusion.kt:15), [`bestPerSection`](src/main/kotlin/athena/search/Fusion.kt:36) |
| `by lazy` | Compute a value the first time it's read, then cache it. | [`OnnxEmbedder.dimensions`](src/main/kotlin/athena/embed/OnnxEmbedder.kt:40), [`FixtureOkb.pages`](src/test/kotlin/athena/testing/FixtureOkb.kt) |
| `use { }` | Closes an `AutoCloseable` when the block ends, even on exceptions (Java's try-with-resources). | [`DataSource.query`](src/main/kotlin/athena/db/Database.kt:38), [`withService`](src/main/kotlin/athena/Main.kt:65) |
| `require` / `check` / `error` | One-line preconditions that throw `IllegalArgumentException` / `IllegalStateException`. | [`OnnxEmbedder.findModel`](src/main/kotlin/athena/embed/OnnxEmbedder.kt:71), [`reciprocalRankFusion`](src/main/kotlin/athena/search/Fusion.kt:15) |
| `runCatching` | Turns "might throw" into a `Result` you can `onSuccess` / `onFailure` / `getOrNull`. | [`loadEmbedder`](src/main/kotlin/athena/Main.kt:83) (a broken model is not fatal) |
| `inline` + `reified` generics | Inside an inline function the type `T` is known at runtime, so `encodeToString(value)` finds `T`'s serializer. | [`respondJson<T>`](src/main/kotlin/athena/http/Routes.kt:103) |
| Raw strings `"""…"""` and templates `$x` | Multi-line strings without escaping (used for SQL) and `"$name"` / `"${expr}"` interpolation. | [`PostgresHandbookStore`](src/main/kotlin/athena/search/PostgresHandbookStore.kt:21) |
| Java interop | Kotlin calls Java libraries directly: JDBC, HikariCP, ONNX Runtime. A Java `get(int)` works with `[ ]`; Java keywords need backticks (``System.`in` ``). | [`OnnxEmbedder.embed`](src/main/kotlin/athena/embed/OnnxEmbedder.kt:55), [`serveStdio`](src/main/kotlin/athena/Main.kt) |

## Coroutines

| Concept | In one line | Where |
|---|---|---|
| `suspend fun` | A function that can pause without blocking its thread. Only callable from another suspend function or a coroutine. | every [`HandbookStore`](src/main/kotlin/athena/search/HandbookStore.kt:34) method |
| `withContext(Dispatchers.IO)` | Run blocking work (JDBC) on the IO thread pool; the caller just suspends. | [`DataSource.query`](src/main/kotlin/athena/db/Database.kt:38) |
| `Dispatchers.Default` | The CPU-sized pool, for CPU work like model inference. | [`embedQuery`](src/main/kotlin/athena/search/HandbookService.kt:94) |
| Structured concurrency: `coroutineScope { async { } }` | Run the lexical and semantic searches at the same time; if one fails the other is cancelled, and nothing outlives the block. | [`HandbookService.search`](src/main/kotlin/athena/search/HandbookService.kt:50) |
| `runBlocking` and `Job` | Bridge from normal code into coroutines (only at the edges, like `main`). A `Job` can be completed and awaited. | [`serveStdio`](src/main/kotlin/athena/Main.kt) |
| `cancelChildren()` | Cancel coroutines still running in a scope. Without it the stdio server never exited: the MCP SDK leaves helper coroutines in `runBlocking`'s scope. | [`Main.kt`](src/main/kotlin/athena/Main.kt:115) |
| Re-throw `CancellationException` | Cancellation is an exception; catch-all handlers must re-throw it or coroutines can't stop. | [`toolCall`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt:198), [`handled`](src/main/kotlin/athena/http/Routes.kt) |
| `runTest` vs `runBlocking` in tests | `runTest` skips delays with a virtual clock, which made the MCP client's request timeout fire instantly while the server worked on real threads. | [`McpToolsTest`](src/test/kotlin/athena/mcp/McpToolsTest.kt) |

## Libraries and frameworks

| Concept | In one line | Where |
|---|---|---|
| kotlinx.serialization | `@Serializable` generates JSON (de)serializers at compile time; `@SerialName` renames a field or enum value. | [`Models.kt`](src/main/kotlin/athena/domain/Models.kt:10), [`ApiJson`](src/main/kotlin/athena/http/Routes.kt) |
| Ktor application module | `fun Application.athenaModule()` installs plugins and routes; the same function runs in production and in `testApplication { }`. | [`athenaModule`](src/main/kotlin/athena/http/Routes.kt:49), [`RoutesTest`](src/test/kotlin/athena/http/RoutesTest.kt) |
| Ktor routing | `routing { get("/search") { call.request.queryParameters["q"] } }` | [`Routes.kt`](src/main/kotlin/athena/http/Routes.kt:72) |
| MCP Kotlin SDK: tools | `Server.addTool(name, description, inputSchema) { request -> CallToolResult(...) }`. Errors the model should see go back as `isError = true`, not exceptions. | [`registerHandbookTools`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt:55) |
| MCP transports | Same server object over stdio (`StdioServerTransport`) or Streamable HTTP (`mcpStreamableHttp`), and over `ChannelTransport` in tests. | [`serveStdio`](src/main/kotlin/athena/Main.kt), [`athenaModule`](src/main/kotlin/athena/http/Routes.kt:49), [`McpToolsTest`](src/test/kotlin/athena/mcp/McpToolsTest.kt) |
| stdio hygiene | In stdio mode stdout *is* the protocol. Logs go to stderr (logback.xml) and `System.setOut(System.err)` catches stray prints (the SDK's kotlin-logging prints a banner). | [`main`](src/main/kotlin/athena/Main.kt:45), [`logback.xml`](src/main/resources/logback.xml) |
| Gradle Kotlin DSL + version catalog | The build is Kotlin code; versions live in one TOML file. `application` gives `run` and `installDist`. | [`build.gradle.kts`](build.gradle.kts), [`libs.versions.toml`](gradle/libs.versions.toml) |
| Testing | `kotlin.test` assertions on JUnit 5; `@TestInstance(PER_CLASS)` for one DB per class; `assumeTrue` skips (not fails) without Docker; Testcontainers starts pgvector. | [`PostgresIntegrationTest`](src/test/kotlin/athena/search/PostgresIntegrationTest.kt) |

## Lessons from building it (interview notes)

- **The parity test caught a real bug.** The int8 ONNX model quantises activations with one scale
  per input tensor, so when Python embedded chunks in padded batches, every vector shifted a little
  (cosine 0.992–0.997 against the same text embedded alone). Kotlin embeds queries one at a time, so
  stored and query vectors were in slightly different spaces. Fix: embed one text at a time in Python
  too; [`OnnxParityTest`](src/test/kotlin/athena/embed/OnnxParityTest.kt) now guards it.
- **stdio is fragile.** A single `println` from any library breaks the MCP stream. A raw JSON-RPC
  smoke test (now also in CI) found the kotlin-logging banner.
- **`runBlocking` waits for every child.** The stdio server hung after the client disconnected until it
  closed the server and cancelled the leftover coroutines.
- **Natural-language questions need OR, not AND.** `plainto_tsquery` requires every word; a question
  like "How much notice must I give…" then matches nothing. The store builds an OR query over the
  question's stems and lets `ts_rank_cd` reward pages that match more of them.
