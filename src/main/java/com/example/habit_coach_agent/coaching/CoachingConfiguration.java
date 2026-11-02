package com.example.habit_coach_agent.coaching;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(CoachingProperties.class)
public class CoachingConfiguration {
    @Bean
    public CoachProvider coachProvider(CoachingProperties properties, JsonMapper mapper) {
        properties.validate();
        return properties.isEnabled() ? new OpenAiCoachProvider(properties, mapper)
                : new CatalogCoachProvider(properties);
    }
}
