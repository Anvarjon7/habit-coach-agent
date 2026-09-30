package com.example.habit_coach_agent.project;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CurrentProjectContext {
    private final ProjectRegistry projectRegistry;
    private String currentProjectId;

    public CurrentProjectContext(ProjectRegistry projectRegistry,
                                 @Value("${project.context.current-project-id}") String currentProjectId) {
        this.projectRegistry = projectRegistry;
        setCurrentProject(currentProjectId);
    }
    public Project getCurrentProject() { return projectRegistry.getProject(currentProjectId); }
    public void setCurrentProject(String projectId) {
        projectRegistry.getProject(projectId);
        this.currentProjectId = projectId;
    }
}
