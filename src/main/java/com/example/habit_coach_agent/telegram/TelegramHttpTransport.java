package com.example.habit_coach_agent.telegram;

import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Fixed deadline and bounded response buffer, including chunked bodies. */
@Component
public class TelegramHttpTransport {
    public record Response(int status,byte[] body) { }
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public Response send(HttpRequest request,int maxBytes) {
        CompletableFuture<HttpResponse<byte[]>> call=client.sendAsync(request,ignored->new BoundedBody(maxBytes));
        try {
            var response=call.get(20,TimeUnit.SECONDS);
            return new Response(response.statusCode(),response.body());
        } catch(InterruptedException interrupted) {
            call.cancel(true); Thread.currentThread().interrupt(); throw new TelegramDeliveryException("INTERRUPTED",false,0);
        } catch(ExecutionException | TimeoutException failure) {
            call.cancel(true); throw new TelegramDeliveryException("NETWORK_FAILURE",false,0);
        }
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private Flow.Subscription subscription;
        BoundedBody(int max) { this.max=max; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription=value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> chunks) {
            for(ByteBuffer chunk:chunks) {
                if(chunk.remaining()>max-buffer.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("Response too large")); return;
                }
                byte[] bytes=new byte[chunk.remaining()]; chunk.get(bytes); buffer.writeBytes(bytes);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(buffer.toByteArray()); }
    }
}
