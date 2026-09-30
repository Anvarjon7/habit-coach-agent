package com.example.habit_coach_agent.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "agent.registry")
public class AgentRegistryProperties {

    private Map<String, List<String>> projects = new LinkedHashMap<>();

    public Map<String, List<String>> getProjects() {
        return projects;
    }

    public void setProjects(Map<String, List<String>> projects) {
        this.projects = projects;
    }
}
