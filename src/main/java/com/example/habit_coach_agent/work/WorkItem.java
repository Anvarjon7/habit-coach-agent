package com.example.habit_coach_agent.work;

import java.util.List;

public record WorkItem(
        String projectId, String description, WorkItemStatus status,
        List<String> participantAgentIds) {
    public WorkItem {
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId must not be blank");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        participantAgentIds = participantAgentIds == null ? List.of() : List.copyOf(participantAgentIds);
    }
}
