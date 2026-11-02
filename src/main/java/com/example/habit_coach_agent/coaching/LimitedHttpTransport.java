package com.example.habit_coach_agent.coaching;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class LimitedHttpTransport implements ProviderHttpTransport {
    private final HttpClient client;

    LimitedHttpTransport(Duration timeout) {
        client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public Response send(HttpRequest request, int maximumResponseBytes, Duration timeout) {
        CompletableFuture<HttpResponse<byte[]>> pending;
        try {
            pending = client.sendAsync(request, ignored -> new LimitedBodySubscriber(maximumResponseBytes));
        } catch (RuntimeException error) {
            throw new CoachProviderException(CoachProviderException.Reason.PROVIDER_ERROR);
        }
        try {
            HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return new Response(response.statusCode(), response.body());
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new CoachProviderException(CoachProviderException.Reason.TIMEOUT);
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new CoachProviderException(CoachProviderException.Reason.UNAVAILABLE);
        } catch (ExecutionException exception) {
            throw new CoachProviderException(CoachProviderException.Reason.PROVIDER_ERROR);
        }
    }

    /** Cancels before buffering a chunk that would exceed the bound, including chunked responses. */
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximumBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBodySubscriber(int maximumBytes) { this.maximumBytes = maximumBytes; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) {
            if (subscription != null) { value.cancel(); return; }
            subscription = value;
            value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maximumBytes - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new CoachProviderException(CoachProviderException.Reason.PROVIDER_ERROR));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
