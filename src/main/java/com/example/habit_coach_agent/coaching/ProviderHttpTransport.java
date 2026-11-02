package com.example.habit_coach_agent.coaching;

import java.net.http.HttpRequest;
import java.time.Duration;

/** Package-private seam for deterministic offline tests. No configurable remote URLs. */
interface ProviderHttpTransport {
    record Response(int status, byte[] body) {}
    Response send(HttpRequest request, int maximumResponseBytes, Duration timeout);
}
