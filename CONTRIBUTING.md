# Contributing

## Development workflow

1. Start from an up-to-date `main`.
2. Create one branch for one issue.
3. Make the smallest change that satisfies the issue.
4. Add or update tests for changed behavior.
5. Run the full Maven test suite.
6. Open a pull request against `main`.
7. Wait for CI and independent review.
8. Address review comments.
9. The human maintainer performs the final review and merge.

## Pull requests

PRs should explain:
- What changed
- Why it changed
- How it was tested
- Any known limitations
- Whether database/API/configuration behavior changed

Do not merge work that has failing required checks or unresolved review concerns.

## Agent collaboration

AI agents are treated as contributors with explicit ownership. They should not silently take over another agent's domain or modify unrelated files.

GitHub Issues are the source of truth for work. Telegram or other chat channels may be used for notifications and coordination, but important decisions and implementation requirements must be reflected in GitHub.
