package com.example.habit_coach_agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentRegistryTest {

    @Test
    void exposesFrontendAgentForFutureProjects() {
        AgentRegistry registry = new AgentRegistry(new AgentRegistryProperties());

        AgentDefinition frontend = registry.getAgent("frontend");

        assertEquals(AgentRole.FRONTEND, frontend.role());
        assertTrue(frontend.capabilities().contains("react"));
        assertTrue(frontend.capabilities().contains("flutter"));
    }

    @Test
    void returnsOnlyAgentsAssignedToProject() {
        AgentRegistryProperties properties = new AgentRegistryProperties();
        properties.setProjects(java.util.Map.of(
                "web-project", List.of("zuck", "frontend", "qa")));

        AgentRegistry registry = new AgentRegistry(properties);

        List<AgentDefinition> agents = registry.getActiveAgents("web-project");

        assertEquals(List.of("zuck", "frontend", "qa"),
                agents.stream().map(AgentDefinition::id).toList());
        assertTrue(registry.isActive("web-project", "frontend"));
        assertFalse(registry.isActive("web-project", "backend"));
    }

    @Test
    void rejectsUnknownAgent() {
        AgentRegistry registry = new AgentRegistry(new AgentRegistryProperties());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.getAgent("does-not-exist"));

        assertEquals("Unknown agent: does-not-exist", exception.getMessage());
    }

    @Test
    void rejectsUnknownProject() {
        AgentRegistry registry = new AgentRegistry(new AgentRegistryProperties());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.getActiveAgents("does-not-exist"));

        assertEquals("Unknown project: does-not-exist", exception.getMessage());
    }
}
