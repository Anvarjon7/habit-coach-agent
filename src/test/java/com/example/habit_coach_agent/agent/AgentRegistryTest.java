package com.example.habit_coach_agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentRegistryTest {

    @Test
    void exposesFrontendAgentForFutureProjects() {
        AgentRegistry registry = new AgentRegistry();

        AgentDefinition frontend = registry.getAgent("frontend");

        assertEquals(AgentRole.FRONTEND, frontend.role());
        assertTrue(frontend.capabilities().contains("react"));
        assertTrue(frontend.capabilities().contains("flutter"));
    }

    @Test
    void exposesCompleteProjectIndependentTeam() {
        AgentRegistry registry = new AgentRegistry();

        List<String> ids = registry.getAllAgents().stream()
                .map(AgentDefinition::id)
                .toList();

        assertEquals(List.of("zuck", "backend", "frontend", "qa", "platform", "product", "research", "ilon"), ids);
    }

    @Test
    void rejectsUnknownAgent() {
        AgentRegistry registry = new AgentRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.getAgent("does-not-exist"));

        assertEquals("Unknown agent: does-not-exist", exception.getMessage());
    }
}
