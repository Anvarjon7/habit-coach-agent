package com.example.habit_coach_agent.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

@Component
public class TelegramVoiceDownloader {
    private final String token;
    private final ObjectMapper mapper;
    private final TelegramHttpTransport transport;
    public TelegramVoiceDownloader(@Value("${telegram.bot-token:${TELEGRAM_BOT_TOKEN:}}") String token,ObjectMapper mapper,TelegramHttpTransport transport) {
        this.token=token; this.mapper=mapper; this.transport=transport;
    }
    public byte[] download(String fileId) {
        if(!token.matches("[0-9]+:[A-Za-z0-9_-]+") || fileId.isBlank() || fileId.length()>512)
            throw new TelegramDeliveryException("VOICE_NOT_CONFIGURED",true,0);
        try {
            var request=HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot"+token+"/getFile"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("file_id",fileId)))).build();
            var metadata=transport.send(request,32768);
            var response=mapper.readTree(metadata.body());
            if(metadata.status()!=200 || !response.path("ok").asBoolean(false)) throw new TelegramDeliveryException("VOICE_UNAVAILABLE",false,0);
            String path=response.path("result").path("file_path").asText("");
            long size=response.path("result").path("file_size").asLong(0);
            // Only Telegram's own relative OGG file paths; never follow a URL from payloads.
            if(!path.matches("[A-Za-z0-9_/-]+\\.oga") && !path.matches("[A-Za-z0-9_/-]+\\.ogg"))
                throw new TelegramDeliveryException("VOICE_FORMAT",true,0);
            if(path.contains("..") || size>5*1024*1024) throw new TelegramDeliveryException("VOICE_TOO_LARGE",true,0);
            var audioRequest=HttpRequest.newBuilder(URI.create("https://api.telegram.org/file/bot"+token+"/"+path)).timeout(Duration.ofSeconds(15)).GET().build();
            var audio=transport.send(audioRequest,5*1024*1024);
            byte[] bytes=audio.body();
            if(audio.status()!=200 || bytes.length<4 || bytes[0]!='O' || bytes[1]!='g' || bytes[2]!='g' || bytes[3]!='S')
                throw new TelegramDeliveryException("VOICE_FORMAT",true,0);
            return bytes;
        } catch(TelegramDeliveryException classified) { throw classified; }
        catch(RuntimeException failure) { throw new TelegramDeliveryException("VOICE_UNAVAILABLE",false,0); }
    }
}
