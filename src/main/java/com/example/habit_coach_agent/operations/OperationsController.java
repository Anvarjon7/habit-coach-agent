package com.example.habit_coach_agent.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Operational counts only. Protected by InternalApiKeyFilter; contains no user content or secrets. */
@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    private final JdbcTemplate jdbc;
    public OperationsController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @GetMapping("/queues")
    public Map<String,Object> queues() {
        return Map.of("outgoing",counts("telegram_outbox"),"coaching",counts("telegram_conversations"));
    }
    private List<Map<String,Object>> counts(String trustedTable) {
        return jdbc.queryForList("SELECT status,COUNT(*) AS count,MIN(created_at) AS oldest_created_at FROM "+trustedTable+" GROUP BY status");
    }
}
