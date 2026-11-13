---
name: cmkl-handbook
description: Answer questions about CMKL University's rules, programmes, courses, faculty and procedures (leave of absence, probation, grades, tuition and fees, thesis, internships, admissions, the B.Eng. in AiCE, M.S. in AiCE and M.S. in ECE) from Athena's reviewed handbook knowledge base, with a citation for every fact. Use whenever a question is about CMKL policy or "what does the handbook say", in English or Thai, and when someone asks what changed in the handbooks recently. Needs the athena-handbook MCP server (tools search_handbook, get_section, list_programmes, recent_changes).
---

# CMKL handbook answers

Athena's knowledge base is the reviewed, single source of truth for CMKL's student handbooks.
The `athena-handbook` MCP server searches it. Answer **only** from what its tools return, and cite
every fact. Never answer CMKL policy questions from memory or from the web: rules differ by
programme and change by committee decision.

## Workflow

1. **Work out the programme.** B.Eng. in AiCE = `aice-beng`, M.S. in AiCE = `aice-ms`,
   M.S. in ECE = `ece-ms`. If the student names one, pass it as `programme`; it keeps their
   programme's pages plus university-wide ones. If it's unclear and the rule could differ by
   programme, search without the filter and say which programme each answer is for, or ask.
   `list_programmes` gives every code.
2. **Search.** `search_handbook(query, programme?)`. Use the student's words; add the handbook's
   likely terms if the first search misses ("leave of absence" for "taking a break", "QPA" for GPA
   in the B.Eng.). Thai questions work as they are (the search understands meaning across
   languages); answer in the language the student used.
3. **Read before you answer.** If the passage is cut off or the answer depends on details
   (conditions, deadlines, exceptions), call `get_section(id)` for the whole section.
4. **Check for committee decisions.** Results from `minutes/...` are approved committee decisions.
   They can change a handbook rule from an effective date. If a decision and a handbook page
   disagree, give the rule in force today, then the change and when it takes effect. Watch for
   decisions "not yet in force".
5. **Answer with citations.** Short answer first, then the details. Cite each fact as
   *Handbook — Section* with the page path, e.g.
   "… (M.S. in ECE Student Handbook — Returning from a Leave of Absence,
   `ece-ms/19-returning-from-a-leave-of-absence.md`)."
6. **Say when it isn't there.** If no passage answers the question, say the handbooks don't cover
   it and suggest who to ask (the CUBE office or the programme director). Don't guess.

## "What changed?"

Use `recent_changes(since?, programme?)` for questions like "what changed in the handbook this
month?" or "is this page up to date?". Each change is an approved edit with its date, author and
page; cite the page path and date.

## Tool reference

| Tool | Use it for | Key arguments |
|---|---|---|
| `search_handbook` | Any policy question | `query` (required), `programme`, `top_k` (default 5), `mode` (`hybrid` default; `lexical` for exact codes like "CMKL 41-501") |
| `get_section` | Full text of one section | `id` from a search result, e.g. `aice-ms/08-3-returning-from-a-leave-of-absence` |
| `list_programmes` | Programme codes and names | — |
| `recent_changes` | Recent approved edits | `since` (YYYY-MM-DD), `programme`, `limit` |

## Setup (if the tools are missing)

The server is in the `athena-mcp-kt` repository. With the database loaded (see its README):

```bash
claude mcp add athena -e JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
  -e DATABASE_URL=postgresql://localhost:5432/athena \
  -- /path/to/athena-mcp-kt/build/install/athena-mcp/bin/athena-mcp stdio
```

or, if the HTTP server is running: `claude mcp add --transport http athena http://localhost:8080/mcp`.
