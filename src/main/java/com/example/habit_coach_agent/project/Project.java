package com.example.habit_coach_agent.project;

import java.util.List;

public record Project(
        String id, String name, String description, String repository,
        String defaultBranch, List<String> technologies) {}
