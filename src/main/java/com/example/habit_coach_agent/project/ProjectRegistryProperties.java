package com.example.habit_coach_agent.project;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "project.registry")
public class ProjectRegistryProperties {
    private Map<String, Project> projects = new LinkedHashMap<>();
    public Map<String, Project> getProjects() { return projects; }
    public void setProjects(Map<String, Project> projects) { this.projects = projects; }
}
