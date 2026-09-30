package com.example.habit_coach_agent.config;

import com.example.habit_coach_agent.project.ProjectRegistryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProjectRegistryProperties.class)
public class ProjectRegistryConfig {}
