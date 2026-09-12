# Working conventions

- Commit messages follow [gitmoji](https://gitmoji.dev/) convention and stay short (one line, no body unless truly needed).
- Commit small, logically scoped patches — one concern per commit, not batched multi-step dumps.
- Code comments stay short and scoped to the line/block they annotate. Never use them to narrate the task, the fix, or project history — that belongs in commit messages or `docs/`.
- Project documentation (architecture, design rationale, planning docs) lives under `docs/`, not in code comments or ad-hoc root-level markdown files.
