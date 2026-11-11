package com.example.habit_coach_agent.coaching;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpeechLifecycleTests {
    private CoachingProperties config() {
        var config = new CoachingProperties();
        config.setSpeechModel("configured-model"); config.setApiKey("test-key");
        config.setTimeout(Duration.ofMillis(100)); return config;
    }

    @Test void stalledHandshakeTimesOutAndCancelsConnection() {
        var connection = new CompletableFuture<WebSocket>();
        var speech = new RealtimeSpeechGenerator(config(), JsonMapper.builder().build(), (uri,key,timeout,listener) -> {
            assertEquals("wss://api.openai.com/v1/realtime?model=configured-model",uri.toString());
            return connection;
        });
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var error = assertThrows(CoachProviderException.class, () -> speech.speak("Try one page."));
            assertEquals(CoachProviderException.Reason.TIMEOUT,error.getReason());
        });
        assertTrue(connection.isCancelled());
    }

    @Test void stalledSessionAbortsSocketWithoutReturningPartialAudio() {
        var socket = mock(WebSocket.class);
        var speech = new RealtimeSpeechGenerator(config(), JsonMapper.builder().build(),
                (uri,key,timeout,listener) -> CompletableFuture.completedFuture(socket));
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var error = assertThrows(CoachProviderException.class, () -> speech.speak("Try one page."));
            assertEquals(CoachProviderException.Reason.TIMEOUT,error.getReason());
        });
        verify(socket,atLeastOnce()).abort();
    }

    @Test void hungEncoderIsKilledAtDeadline() {
        var stopped = new CountDownLatch(1);
        var process = mock(Process.class);
        when(process.getOutputStream()).thenReturn(OutputStream.nullOutputStream());
        when(process.getInputStream()).thenReturn(new InputStream() {
            @Override public int read() throws IOException {
                try { if(!stopped.await(3,TimeUnit.SECONDS)) throw new IOException("not stopped"); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(); }
                return -1;
            }
        });
        when(process.destroyForcibly()).thenAnswer(invocation -> { stopped.countDown(); return process; });
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var error=assertThrows(CoachProviderException.class, () -> new OpusEncoder(builder -> process)
                    .encode(new byte[32],"ffmpeg",1024,Duration.ofMillis(100)));
            assertEquals(CoachProviderException.Reason.TIMEOUT,error.getReason());
        });
        assertEquals(0,stopped.getCount());
    }

    @Test void oversizedEncoderOutputIsRejectedAndProcessKilled() {
        var process=mock(Process.class);
        when(process.getOutputStream()).thenReturn(OutputStream.nullOutputStream());
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[33]));
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(CoachProviderException.class,
                () -> new OpusEncoder(builder -> process).encode(new byte[32],"ffmpeg",32,Duration.ofSeconds(1))));
        verify(process,atLeastOnce()).destroyForcibly();
    }
}
