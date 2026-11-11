package com.example.habit_coach_agent.coaching;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.*;

/** Converts bounded 24 kHz mono signed little-endian PCM through pipes; no media files or shell. */
final class OpusEncoder {
    @FunctionalInterface interface ProcessStarter { Process start(ProcessBuilder builder) throws java.io.IOException; }
    private final ProcessStarter starter;
    OpusEncoder() { this(ProcessBuilder::start); }
    OpusEncoder(ProcessStarter starter) { this.starter = starter; }

    byte[] encode(byte[] pcm, String executable, int maximum, Duration timeout) {
        Process process = null;
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            process = starter.start(new ProcessBuilder(executable, "-hide_banner", "-loglevel", "error", "-nostdin",
                    "-f", "s16le", "-ar", "24000", "-ac", "1", "-i", "pipe:0", "-c:a", "libopus",
                    "-b:a", "32k", "-application", "voip", "-f", "ogg", "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD));
            Process running = process;
            var writer = new FutureTask<Void>(() -> {
                try (var input = running.getOutputStream()) { input.write(pcm); }
                return null;
            });
            var reader = new FutureTask<byte[]>(() -> {
                try (var output = running.getInputStream(); var bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = output.read(buffer)) != -1) {
                        if (bytes.size() + count > maximum) { running.destroyForcibly(); throw failure(); }
                        bytes.write(buffer, 0, count);
                    }
                    return bytes.toByteArray();
                }
            });
            Thread.ofVirtual().start(writer); Thread.ofVirtual().start(reader);
            writer.get(remaining(deadline), TimeUnit.NANOSECONDS);
            byte[] encoded = reader.get(remaining(deadline), TimeUnit.NANOSECONDS);
            if (!running.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) throw new TimeoutException();
            if (running.exitValue() != 0 || encoded.length == 0) throw failure();
            return encoded;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new CoachProviderException(CoachProviderException.Reason.TIMEOUT);
        } catch (TimeoutException timeoutError) {
            throw new CoachProviderException(CoachProviderException.Reason.TIMEOUT);
        } catch (Exception error) {
            throw failure();
        } finally {
            if (process != null) process.destroyForcibly();
        }
    }
    static long remaining(long deadline) throws TimeoutException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new TimeoutException();
        return remaining;
    }
    private static CoachProviderException failure() { return new CoachProviderException(CoachProviderException.Reason.PROVIDER_ERROR); }
}
