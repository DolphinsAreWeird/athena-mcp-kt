# Exercises

Three pieces left for Khant to write by hand, smallest first. Each has a stub in the code marked
`TODO(Exercise n)` and a test class that is `@Disabled` until the exercise is done:

1. Delete the `@Disabled(...)` line on the test class.
2. Run just that test: `./gradlew test --tests 'athena.exercises.SinceExerciseTest'` (it fails).
3. Write the code until it passes, then run everything: `./gradlew test`.
4. Ask Claude to review the attempt (paste the diff; ask for a review, not a rewrite).

Don't look up a finished solution first. The hints point at the Kotlin features to use, and
[LEARNING.md](LEARNING.md) shows where the codebase already uses them.

---

## Exercise 1 — relative dates for `recent_changes` (small: regex, `when`, null safety)

Today `recent_changes(since = "2026-10-01")` only takes ISO dates. Make it also accept
`7d`, `2w`, `3m`, `10 days`, `1 week`, `2 Weeks`, `3 months`.

- **Write:** `parseRelativeSince` in [`src/main/kotlin/athena/mcp/Since.kt`](src/main/kotlin/athena/mcp/Since.kt).
  Return the date that long before `today`, or `null` when the text isn't a relative form
  (so `parseSince` falls back to ISO dates and still rejects junk).
- **Test:** [`SinceExerciseTest`](src/test/kotlin/athena/exercises/SinceExerciseTest.kt).
- **Hints:** one `Regex("""(\d+)\s*(d|days?|w|weeks?|m|months?)""", RegexOption.IGNORE_CASE)` and
  `matchEntire`; destructure the groups with `val (n, unit) = match.destructured`; `when (unit.lowercase().first())`;
  `today.minusDays(...)`, `minusWeeks`, `minusMonths`.
- **Then:** update the `since` description in the tool's input schema
  ([`AthenaMcpServer.kt`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt)) so the model knows it can say "7d".

## Exercise 2 — `GET /sections/{id}` (medium: Ktor routing, sealed classes)

The website should be able to fetch one section, like the `get_section` MCP tool does.

- **Write:** a route in [`src/main/kotlin/athena/http/Routes.kt`](src/main/kotlin/athena/http/Routes.kt)
  (look for `EXERCISE 2`). Section ids contain slashes (`aice-beng/32-academic-probation`), so use a
  tailcard route: `get("/sections/{id...}")` and read it with `call.parameters.getAll("id")?.joinToString("/")`.
- Return `200` with the `Section` as JSON when found, and `404` with a JSON body that includes the
  suggestions when not found.
- **Test:** [`SectionsRouteExerciseTest`](src/test/kotlin/athena/exercises/SectionsRouteExerciseTest.kt).
- **Hints:** `service.section(id)` returns a `SectionLookup`; a `when (lookup) { is Found -> ...; is NotFound -> ... }`
  must cover both cases. Wrap the body in `call.handled { }` like the other routes. You'll need a small
  `@Serializable data class` for the 404 body.

## Exercise 3 — a `find_decisions` MCP tool (bigger: regex, data classes, a new tool end to end)

Athena's Week 3 test found that the model got every "a committee decision overrides the handbook"
question right once it saw the decision register. Committee decisions are lines in the minutes like:

```
Decision GSC-2026-08-D2 (M.S. in ECE; effective Spring 2027): The notice to return ... is reduced ...
```

The retrieval eval shows why a dedicated tool helps: question 6's answer exists only as a finance
committee decision, and general tuition pages outrank it.

- **Part 1 — parse.** Write `parseDecisions(markdown)` in
  [`src/main/kotlin/athena/mcp/Decisions.kt`](src/main/kotlin/athena/mcp/Decisions.kt): one `Decision`
  per decision line (code, programmes, effective, text); other lines ignored.
- **Part 2 — the tool.** Register `find_decisions(topic, programme?)` in `registerHandbookTools`
  ([`AthenaMcpServer.kt`](src/main/kotlin/athena/mcp/AthenaMcpServer.kt), look for `EXERCISE 3`).
  It returns the decisions about the topic, each with its minutes page as the citation.
  You decide how to get the minutes pages. Two reasonable designs:
  - reuse `service.search(topic, programme)`, keep results whose `sectionId` starts with `minutes/`,
    fetch each with `service.section(id)`, parse, and keep decisions mentioning the topic; or
  - add `suspend fun minutes(): List<Section>` to `HandbookStore` (then implement it in
    `PostgresHandbookStore` with SQL **and** in the test fake `FakeHandbookStore`) and filter in Kotlin.
  Be ready to explain the trade-off in an interview (one SQL query vs. reusing ranked search).
- **Test:** [`DecisionsExerciseTest`](src/test/kotlin/athena/exercises/DecisionsExerciseTest.kt). Also update
  `McpToolsTest`'s "lists the four tools" test, which will now see five.
- **Hints:** a `Regex` with groups for the code, the parenthesised part and the text;
  `markdown.lineSequence().mapNotNull { regex.find(it) }`; split the parenthesised part on `"; "`,
  then the programmes on `", "`.
- **Stretch:** add the new tool to the Skill ([`skills/cmkl-handbook/SKILL.md`](skills/cmkl-handbook/SKILL.md))
  and rerun the eval to see if answering question 6 gets easier.
