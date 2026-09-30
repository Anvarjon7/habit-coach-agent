package com.example.habit_coach_agent.team;

import com.example.habit_coach_agent.agent.AgentDefinition;
import com.example.habit_coach_agent.project.Project;
import com.example.habit_coach_agent.work.WorkItem;
import java.util.List;

public record TeamCoordinationResult(
        Project project, List<AgentDefinition> participants, WorkItem workItem) {}
