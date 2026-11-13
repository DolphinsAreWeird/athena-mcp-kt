# AI disclosure

This project was designed, directed and tested by Khant Phyo Wai. The code was written with
Claude Code (Anthropic), working from Khant's brief.

**Khant's part:** the idea (a Kotlin MCP server as a new consumer of Athena, the CMKL handbook
knowledge base Khant built in Fall 2026), the requirements and scope, the choice of stack, the
review of the code and results, and the decisions on what ships.

**Claude Code's part:** writing the Kotlin service, the Python ingest job, the tests, CI and Docker
set-up, the retrieval eval script and first drafts of the documentation, under that direction.
Claude also drafted the gold page labels for the 30 Week 3 questions (and the 6 Thai rewrites),
which were then checked against the knowledge base.

**Learning:** this was Khant's first Kotlin code. [LEARNING.md](LEARNING.md) maps every Kotlin
concept the code uses, and [EXERCISES.md](EXERCISES.md) holds three pieces Khant writes by hand.

**Data:** the handbook content is not in this repository. The server reads a configured copy of
Athena's knowledge base; tests use a small synthetic fixture (`fixtures/okb-mini`). The questions
in `eval/questions.jsonl` come from the Athena team's Week 3 test.
