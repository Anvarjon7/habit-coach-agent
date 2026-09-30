# Engineering Agent Operating Model

## Purpose

This repository uses multiple AI agents as isolated engineering coworkers. The goal is parallel development without sacrificing reviewability, testability, or human control.

## Roles

### Team Lead
- Decomposes product requests into small GitHub Issues.
- Identifies dependencies and assigns work to the appropriate agent.
- Prevents overlapping ownership of the same files or domain.
- Tracks PRs, CI, review feedback, and blockers.
- Does not bypass review or merge to `main).

### Backend Core
Owns:
- Participants
- Habits
- REST controllers/services/repositories
- Validation and backend tests

### Backend Product
Owns:
- Habit progress and check-ins
- Streaks and competition
- Coaching/business logic
- Agent-facing domain behavior

### Platform
Owns:
- Docker/Compose
- CI/CD
- Configuration and environments
- Security and operational tooling

### Reviewer / QA
- Reviews PRs independently from the implementing agent.
- Checks correctness, tests, API behavior, edge cases, architecture, and scope.
- Requests changes when needed.
- Does not modify the feature branch while reviewing it.

## Rules

1. One GitHub Issue should normally produce one focused PR.
2. Agents work only on feature/chore branches, never directly on `main`.
3. Every PR must pass CI before merge.
4. The implementing agent must add or update tests for behavior changes.
5. Keep PRs small and independently reviewable.
6. Do not mix unrelated refactoring into feature PRs.
7. Never commit secrets, credentials, local `.env` files, or production data.
8. Database/schema changes require explicit review.
9. Agents must report changed files, tests run, and known limitations in the PR.
10. The human maintainer has final merge authority.

## Branch naming

- `feature/<short-description>`
- `fix/<short-description>`
- `chore/<short-description>`
- `refactor/<short-description>`
- `test/<short-description>`

## Standard lifecycle

Issue -> assignment -> isolated branch -> implementation -> tests -> PR -> independent review -> CI -> human review -> merge.
