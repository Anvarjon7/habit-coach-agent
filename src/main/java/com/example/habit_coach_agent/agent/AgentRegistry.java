package com.example.habit_coach_agent.agent;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AgentRegistry {

    private final Map<String, AgentDefinition> agents;
    public AgentRegistry() {
        this.agents = createAgents();
    }

    public AgentDefinition getAgent(String agentId) {
        AgentDefinition agent = agents.get(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent: " + agentId);
        }
        return agent;
    }

    public List<AgentDefinition> getAllAgents() {
        return List.copyOf(agents.values());
    }

    private Map<String, AgentDefinition> createAgents() {
        Map<String, AgentDefinition> definitions = new LinkedHashMap<>();

        register(definitions, new AgentDefinition(
                "zuck", "Zuck", AgentRole.TEAM_LEAD,
                "Coordinates engineering work, decomposes requests, and tracks delivery.",
                Set.of("planning", "task-decomposition", "coordination", "status")));

        register(definitions, new AgentDefinition(
                "backend", "Mr.500", AgentRole.BACKEND,
                "Owns backend services, APIs, domain logic, databases, and backend tests.",
                Set.of("java", "spring-boot", "postgresql", "rest-api", "backend-tests")));

        register(definitions, new AgentDefinition(
                "frontend", "Pixel", AgentRole.FRONTEND,
                "Owns web and mobile UI, frontend architecture, API integration, and frontend tests.",
                Set.of("react", "nextjs", "typescript", "flutter", "api-integration", "frontend-tests")));

        register(definitions, new AgentDefinition(
                "qa", "Sherlock", AgentRole.QA,
                "Owns acceptance criteria, test strategy, edge cases, and regression checks.",
                Set.of("test-planning", "api-testing", "edge-cases", "regression")));

        register(definitions, new AgentDefinition(
                "platform", "Atlas", AgentRole.PLATFORM,
                "Owns infrastructure, Docker, CI/CD, configuration, security, and operations.",
                Set.of("docker", "ci-cd", "configuration", "security", "operations")));

        register(definitions, new AgentDefinition(
                "product", "Mira", AgentRole.PRODUCT,
                "Clarifies requirements, user value, scope, and product behavior.",
                Set.of("requirements", "acceptance-criteria", "product-design")));

        register(definitions, new AgentDefinition(
                "research", "X", AgentRole.RESEARCH,
                "Researches technical options, libraries, APIs, documentation, and alternatives.",
                Set.of("technical-research", "documentation", "architecture-research")));

        register(definitions, new AgentDefinition(
                "ilon", "Ilon", AgentRole.REVIEWER,
                "Independently reviews implementation quality, architecture, tests, and scope.",
                Set.of("code-review", "architecture-review", "quality")));

        return Collections.unmodifiableMap(definitions);
    }

    private void register(Map<String, AgentDefinition> definitions, AgentDefinition definition) {
        definitions.put(definition.id(), definition);
    }
}
