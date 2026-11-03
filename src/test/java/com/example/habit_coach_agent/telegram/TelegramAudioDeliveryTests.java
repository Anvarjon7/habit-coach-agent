package com.example.habit_coach_agent.telegram;

import com.example.habit_coach_agent.coaching.CoachProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TelegramAudioDeliveryTests {
    TelegramHttpTransport transport=mock(TelegramHttpTransport.class);
    CoachProvider provider=mock(CoachProvider.class);
    TelegramStore store=mock(TelegramStore.class);
    TelegramAiUsage usage=mock(TelegramAiUsage.class);
    TelegramOutgoing outgoing=new TelegramOutgoing(1,-1,2L,7L,null,"One small step today.","audio",1,"lease");
    TelegramHttpSender sender=new TelegramHttpSender("123:test_token",new ObjectMapper(),transport,provider,store,usage);
    @BeforeEach void ready() {
        when(store.sendAllowed(any())).thenReturn(true);
        when(store.member(-1,7)).thenReturn(Optional.of(new TelegramMember(1,-1,7,1,"Person",true,false,"audio","UTC","",1,true)));
        when(usage.reserveSpeech(any())).thenReturn(true);
        when(transport.send(any(),anyInt())).thenReturn(new TelegramHttpTransport.Response(200,"{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void preferredAudioUsesVoiceWithWrittenCaption() {
        when(provider.speak(outgoing.text())).thenReturn("OggSsample".getBytes());
        sender.send(outgoing);
        verify(transport).send(argThat(request->request.uri().getPath().endsWith("/sendVoice") && request.headers().firstValue("Content-Type").orElse("").startsWith("multipart/form-data")),eq(32768));
        verify(usage).reserveSpeech(outgoing);
    }
    @Test void unavailableAudioFallsBackToText() {
        when(provider.speak(anyString())).thenThrow(new IllegalStateException("unavailable"));
        sender.send(outgoing);
        verify(transport).send(argThat(request->request.uri().getPath().endsWith("/sendMessage")),eq(32768));
    }
    @Test void exhaustedSpeechQuotaFallsBackWithoutProviderCall() {
        when(usage.reserveSpeech(any())).thenReturn(false);
        sender.send(outgoing); verifyNoInteractions(provider);
        verify(transport).send(argThat(request->request.uri().getPath().endsWith("/sendMessage")),eq(32768));
    }
    @Test void cancelledAfterSynthesisNeverFallsThroughToOldText() {
        when(provider.speak(anyString())).thenAnswer(ignored->{ when(store.sendAllowed(any())).thenReturn(false); return "OggSsample".getBytes(); });
        sender.send(outgoing); verifyNoInteractions(transport);
    }
}
