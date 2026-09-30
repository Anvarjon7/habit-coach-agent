package com.example.habit_coach_agent.config;

import com.example.habit_coach_agent.agent.AgentRegistryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentRegistryProperties.class)
public class AgentRegistryConfig {
}
