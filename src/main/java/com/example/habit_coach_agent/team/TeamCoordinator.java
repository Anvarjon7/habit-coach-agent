package com.example.habit_coach_agent.team;

import com.example.habit_coach_agent.agent.AgentDefinition;
import com.example.habit_coach_agent.project.CurrentProjectContext;
import com.example.habit_coach_agent.project.Project;
import com.example.habit_coach_agent.work.WorkItem;
import com.example.habit_coach_agent.work.WorkItemStatus;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TeamCoordinator {
    private final EngineeringTeam engineeringTeam;
    private final CurrentProjectContext projectContext;

    public TeamCoordinator(EngineeringTeam engineeringTeam, CurrentProjectContext projectContext) {
        this.engineeringTeam = engineeringTeam;
        this.projectContext = projectContext;
    }

    public Project getCurrentProject() { return projectContext.getCurrentProject(); }
    public List<AgentDefinition> getTeam() { return engineeringTeam.getMembers(); }

    public TeamCoordinationResult coordinate(String request) {
        if (request == null || request.isBlank()) throw new IllegalArgumentException("request must not be blank");

        Project project = getCurrentProject();
        Set<String> roles = determineRelevantRoles(request);
        List<AgentDefinition> participants = new ArrayList<>();

        for (AgentDefinition agent : engineeringTeam.getMembers()) {
            if (roles.contains(agent.role().name())) participants.add(agent);
        }

        WorkItem workItem = new WorkItem(
                project.id(), request.trim(), WorkItemStatus.DISCUSSION,
                participants.stream().map(AgentDefinition::id).toList());

        return new TeamCoordinationResult(project, participants, workItem);
    }

    private Set<String> determineRelevantRoles(String request) {
        String text = request.toLowerCase(Locale.ROOT);
        Set<String> roles = new LinkedHashSet<>();
        roles.add("TEAM_LEAD");
        if (containsAny(text, "ui", "frontend", "web", "mobile", "screen", "react", "flutter")) roles.add("FRONTEND");
        if (containsAny(text, "api", "backend", "java", "spring", "database", "postgres", "sql", "endpoint")) roles.add("BACKEND");
        if (containsAny(text, "test", "bug", "qa", "regression", "acceptance")) roles.add("QA");
        if (containsAny(text, "docker", "deploy", "deployment", "ci", "cd", "infrastructure", "security", "production")) roles.add("PLATFORM");
        if (containsAny(text, "feature", "user", "requirement", "behavior", "product", "leaderboard")) roles.add("PRODUCT");
        if (containsAny(text, "research", "compare", "library", "documentation", "alternative", "investigate")) roles.add("RESEARCH");
        return roles;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
}
