package com.example.habit_coach_agent.team;

import com.example.habit_coach_agent.agent.AgentRegistry;
import com.example.habit_coach_agent.project.CurrentProjectContext;
import com.example.habit_coach_agent.project.Project;
import com.example.habit_coach_agent.project.ProjectRegistry;
import com.example.habit_coach_agent.project.ProjectRegistryProperties;
import com.example.habit_coach_agent.work.WorkItemStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TeamCoordinatorTest {
    @Test
    void coordinatesRequestUsingTeamIndependentOfProject() {
        Project project = new Project(
                "habit-coach-agent", "Habit Coach Agent", "Habit coaching platform",
                "Anvarjon7/habit-coach-agent", "main",
                List.of("Java", "Spring Boot", "PostgreSQL"));

        ProjectRegistryProperties projectProperties = new ProjectRegistryProperties();
        projectProperties.setProjects(Map.of(project.id(), project));
        ProjectRegistry projectRegistry = new ProjectRegistry(projectProperties);
        CurrentProjectContext context = new CurrentProjectContext(projectRegistry, project.id());

        AgentRegistry registry = new AgentRegistry();

        TeamCoordinator coordinator = new TeamCoordinator(new EngineeringTeam(registry), context);
        TeamCoordinationResult result = coordinator.coordinate(
                "Add a Java backend API and tests for weekly leaderboard");

        assertEquals(project.id(), result.project().id());
        assertEquals(
                List.of("zuck", "backend", "qa", "product"),
                result.participants().stream().map(agent -> agent.id()).toList());
        assertEquals(WorkItemStatus.DISCUSSION, result.workItem().status());
    }
}
