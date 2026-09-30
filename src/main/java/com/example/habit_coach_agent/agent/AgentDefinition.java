package com.example.habit_coach_agent.agent;

import java.util.Set;

public record AgentDefinition(
        String id,
        String name,
        AgentRole role,
        String description,
        Set<String> capabilities) {
}
