package com.example.habit_coach_agent.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import jakarta.servlet.ServletInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class TelegramIngressFilterTest {
    final String secret="test_webhook_secret_long_enough_12345";
    @Test void wrongSecretIsRejectedWithoutReadingTheBody() throws Exception {
        var request=new MockHttpServletRequest("POST","/telegram/webhook") {
            @Override public ServletInputStream getInputStream() { throw new AssertionError("Unauthenticated body was read"); }
        };
        var response=new MockHttpServletResponse();
        new TelegramIngressFilter(secret).doFilter(request,response,(a,b)->fail("Must not reach MVC"));
        assertEquals(401,response.getStatus());
    }
    @Test void unknownLengthOversizedBodyIsRejectedBeforeMvc() throws Exception {
        var request=new MockHttpServletRequest("POST","/telegram/webhook") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.addHeader("X-Telegram-Bot-Api-Secret-Token",secret);
        request.setContent(new byte[65537]);
        var response=new MockHttpServletResponse();
        new TelegramIngressFilter(secret).doFilter(request,response,(a,b)->fail("Must not reach MVC"));
        assertEquals(413,response.getStatus());
    }
    @Test void validPayloadIsStillReadableByMvc() throws Exception {
        var request=new MockHttpServletRequest("POST","/telegram/webhook");
        request.addHeader("X-Telegram-Bot-Api-Secret-Token",secret);
        request.setContent("{\"update_id\":1}".getBytes(StandardCharsets.UTF_8));
        var response=new MockHttpServletResponse();
        var called=new AtomicBoolean();
        new TelegramIngressFilter(secret).doFilter(request,response,(a,b)->{
            assertEquals("{\"update_id\":1}",new String(a.getInputStream().readAllBytes(),StandardCharsets.UTF_8)); called.set(true);
        });
        assertTrue(called.get());
    }
}
