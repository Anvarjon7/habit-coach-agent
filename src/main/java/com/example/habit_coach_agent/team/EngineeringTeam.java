package com.example.habit_coach_agent.team;

import com.example.habit_coach_agent.agent.AgentDefinition;
import com.example.habit_coach_agent.agent.AgentRegistry;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class EngineeringTeam {
    private final AgentRegistry agentRegistry;
    public EngineeringTeam(AgentRegistry agentRegistry) { this.agentRegistry = agentRegistry; }
    public List<AgentDefinition> getMembers() { return agentRegistry.getAllAgents(); }
}
