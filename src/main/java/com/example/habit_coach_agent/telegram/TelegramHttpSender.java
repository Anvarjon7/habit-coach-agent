package com.example.habit_coach_agent.telegram;

import com.example.habit_coach_agent.coaching.CoachProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class TelegramHttpSender implements TelegramSender {
    private final String token;
    private final ObjectMapper mapper;
    private final TelegramHttpTransport transport;
    private final CoachProvider provider;
    private final TelegramStore store;
    private final TelegramAiUsage usage;
    public TelegramHttpSender(@Value("${telegram.bot-token:${TELEGRAM_BOT_TOKEN:}}") String token,
            ObjectMapper mapper,TelegramHttpTransport transport,CoachProvider provider,TelegramStore store,TelegramAiUsage usage) {
        this.token=token; this.mapper=mapper; this.transport=transport; this.provider=provider; this.store=store;
        this.usage=usage;
    }
    @Override
    public void send(TelegramOutgoing outgoing) {
        if(!store.sendAllowed(outgoing)) return;
        if(!token.matches("[0-9]+:[A-Za-z0-9_-]+")) throw new TelegramDeliveryException("TOKEN_NOT_CONFIGURED",true,0);
        // Audio is an AI-provider operation too; a format preference alone is not provider consent.
        if(outgoing.format().equals("audio") && outgoing.text().length()<=950 && audioAllowed(outgoing) && usage.reserveSpeech(outgoing)) {
            byte[] audio=null;
            try {
                audio=provider.speak(outgoing.text());
                if(audio.length>0 && audio.length<=5*1024*1024 && audioAllowed(outgoing)) {
                    if(!store.sendAllowed(outgoing)) return;
                    sendVoice(outgoing,audio); return;
                }
            } catch(RuntimeException unavailable) {
                if(Thread.currentThread().isInterrupted()) throw new TelegramDeliveryException("INTERRUPTED",false,0);
                // Full text is the fallback if synthesis or Telegram voice delivery is unavailable.
            } finally { if(audio!=null) java.util.Arrays.fill(audio,(byte)0); }
        }
        if(store.sendAllowed(outgoing)) sendText(outgoing);
    }
    private boolean audioAllowed(TelegramOutgoing outgoing) {
        return outgoing.recipientUserId()!=null && store.member(outgoing.chatId(),outgoing.recipientUserId())
                .filter(member->member.active() && member.aiConsent() && member.responseFormat().equals("audio")).isPresent();
    }
    private void sendText(TelegramOutgoing outgoing) {
        if(!store.sendAllowed(outgoing)) return;
        Map<String,Object> payload=new LinkedHashMap<>();
        payload.put("chat_id",outgoing.chatId()); payload.put("text",outgoing.text());
        if(outgoing.replyToMessageId()!=null) payload.put("reply_parameters",Map.of("message_id",outgoing.replyToMessageId(),"allow_sending_without_reply",true));
        if(outgoing.threadId()!=null) payload.put("message_thread_id",outgoing.threadId());
        execute("sendMessage",HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)),"application/json");
    }
    private void sendVoice(TelegramOutgoing outgoing,byte[] audio) {
        if(!store.sendAllowed(outgoing)) return;
        String boundary="coach"+UUID.randomUUID().toString().replace("-","");
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        field(body,boundary,"chat_id",String.valueOf(outgoing.chatId()));
        field(body,boundary,"caption",outgoing.text()+"\nAI-generated voice.");
        if(outgoing.replyToMessageId()!=null) field(body,boundary,"reply_parameters",mapper.writeValueAsString(Map.of("message_id",outgoing.replyToMessageId(),"allow_sending_without_reply",true)));
        if(outgoing.threadId()!=null) field(body,boundary,"message_thread_id",String.valueOf(outgoing.threadId()));
        body.writeBytes(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"voice\"; filename=\"coach.ogg\"\r\nContent-Type: audio/ogg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(audio); body.writeBytes(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] bytes=body.toByteArray();
        try { execute("sendVoice",HttpRequest.BodyPublishers.ofByteArray(bytes),"multipart/form-data; boundary="+boundary); }
        finally { java.util.Arrays.fill(bytes,(byte)0); }
    }
    private static void field(ByteArrayOutputStream body,String boundary,String name,String value) {
        body.writeBytes(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));
    }
    private void execute(String method,HttpRequest.BodyPublisher body,String contentType) {
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot"+token+"/"+method))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type",contentType).POST(body).build();
            var response=transport.send(request,32768);
            JsonNode result=mapper.readTree(response.body());
            if(response.status()==200 && result.path("ok").asBoolean(false)) return;
            int status=result.path("error_code").asInt(response.status());
            if(status==429) throw new TelegramDeliveryException("RATE_LIMIT",false,Math.max(1,Math.min(86400,result.path("parameters").path("retry_after").asInt(30))));
            throw new TelegramDeliveryException("TELEGRAM_"+status,status>=400 && status<500,0);
        } catch(TelegramDeliveryException classified) { throw classified; }
        catch(RuntimeException failure) { throw new TelegramDeliveryException("INVALID_TELEGRAM_RESPONSE",false,0); }
    }
}
