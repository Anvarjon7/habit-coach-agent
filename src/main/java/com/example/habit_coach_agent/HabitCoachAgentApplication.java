package com.example.habit_coach_agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HabitCoachAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HabitCoachAgentApplication.class, args);
    }

}
