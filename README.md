# Habit Coach Agent

> An evidence-based habit formation and tracking platform designed specifically for teenagers and youth.

---

## 🎯 Purpose & Scope

The Habit Coach Agent provides structured routine building, streak tracking, and daily accountability for teenagers (ages 14–18). The core focus is establishing lifelong positive habits:
- 🏃 **Fitness & Athletics** (e.g. daily morning running)
- 📚 **Intellectual Growth** (e.g. daily 30-minute reading)
- 🌍 **Language Acquisition** (e.g. vocabulary drills and conversational practice)

---

## 🛠️ Tech Stack & Architecture

- **Language & Runtime:** Java 21
- **Framework:** Spring Boot 4.1.1
- **Data & Persistence:** Spring Data JPA, Hibernate, PostgreSQL 17
- **Validation:** Jakarta Validation
- **Containerization:** Docker Compose
- **Build & CI:** Maven Wrapper, GitHub Actions

### Domain Model

```text
┌─────────────────┐       1..*      ┌───────────────┐       1..*      ┌────────────────┐
│   Participant   │────────────────>│     Habit     │────────────────>│  HabitCheckIn  │
│-----------------│                 │---------------│                 │----------------│
│ id              │                 │ id            │                 │ id             │
│ firstName       │                 │ name          │                 │ date           │
│ telegramUsername│                 │ description   │                 │ value          │
│ active          │                 │ active        │                 │ completed      │
│                 │                 │ currentStreak │                 └────────────────┘
│                 │                 │ lastCheckIn   │
└─────────────────┘                 └───────────────┘
```

---

## ⚡ API Reference

### Participants

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/participants` | Register a new participant |
| `GET` | `/api/participants/{id}` | Fetch participant details |
| `GET` | `/api/participants` | List all active participants |

### Habits & Streaks

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/participants/{participantId}/habits` | Create a new habit for a participant |
| `GET` | `/api/participants/{participantId}/habits` | List habits with `currentStreak` and `lastCheckInDate` |
| `GET` | `/api/participants/{participantId}/habits/{habitId}` | Fetch habit by ID |
| `POST` | `/api/participants/{participantId}/habits/{habitId}/check-ins` | Record daily check-in and update consecutive streak |
| `GET` | `/api/participants/{participantId}/habits/{habitId}/check-ins` | List historical check-ins ordered by date |

#### Check-in Request Payload
```json
{
  "date": "2026-10-01",
  "value": 1
}
```

---

## 🚀 Running Locally

### 1. Start PostgreSQL (Docker)

```bash
docker compose up -d postgres
```

### 2. Configure Environment

Copy `.env.example` to `.env` or use default fallbacks:

```bash
DB_URL=jdbc:postgresql://localhost:5432/habit_coach
DB_USERNAME=habit
DB_PASSWORD=habit
```

### 3. Run Tests & Start Application

```bash
# Run unit & integration test suite
./mvnw clean test

# Start the Spring Boot server
./mvnw spring-boot:run
```

Server runs on port `8081` by default.

---

## 🤝 Engineering Team Governance

This codebase is managed and reviewed by the autonomous [Zuck Engineering Team Platform](https://github.com/Anvarjon7/engineering-team).
