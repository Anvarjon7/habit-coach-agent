package com.example.habit_coach_agent.telegram;

import com.example.habit_coach_agent.coaching.CoachProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.sql.Timestamp;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"telegram.enabled=false","telegram.bot-username=family_coach_bot"})
class TelegramConversationTests {
    @Autowired TelegramCommandService commands;
    @Autowired TelegramStore store;
    @Autowired TelegramConversations queue;
    @Autowired TelegramAiUsage usage;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CoachProvider provider;
    @MockitoBean TelegramVoiceDownloader voices;
    TelegramConversationWorker worker;

    @BeforeEach void clear() {
        jdbc.update("DELETE FROM coach_challenges"); jdbc.update("DELETE FROM coach_engagement");
        jdbc.update("DELETE FROM telegram_conversations"); jdbc.update("DELETE FROM telegram_ai_daily_totals");
        jdbc.update("DELETE FROM telegram_ai_speech_usage");
        jdbc.update("DELETE FROM telegram_outbox"); jdbc.update("DELETE FROM telegram_updates");
        jdbc.update("DELETE FROM habit_check_ins"); jdbc.update("DELETE FROM telegram_habit_links");
        jdbc.update("DELETE FROM habits"); jdbc.update("DELETE FROM telegram_memberships"); jdbc.update("DELETE FROM participants");
        worker=new TelegramConversationWorker(queue,provider,voices);
    }
    private TelegramUpdate update(long id,long chat,long user,String text) {
        return new TelegramUpdate(id,chat,user,id,null,"supergroup","Person"+user,"",text,false);
    }
    private void send(long id,long chat,long user,String text) { commands.accept(update(id,chat,user,text)); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    private String reply() { return jdbc.queryForObject("SELECT response_text FROM telegram_outbox ORDER BY id DESC LIMIT 1",String.class); }
    @Test void onlyAddressedConsentingMembersAreQueued() {
        send(1,-1,7,"/join"); send(2,-1,7,"@family_coach_bot motivate me");
        assertEquals(0,count("telegram_conversations")); assertTrue(reply().contains("/ai on"));
        send(3,-1,7,"/ai on"); send(4,-1,7,"hello brothers");
        send(5,-1,7,"@family_coach_bot motivate me");
        send(5,-1,7,"@family_coach_bot motivate me");
        assertEquals(1,count("telegram_conversations"));
        verifyNoInteractions(provider,voices);
    }
    @Test void ownContextOnlyAndNoProviderProgressWrites() {
        send(1,-1,7,"/join"); send(2,-1,7,"/habit My reading"); send(3,-1,7,"/ai on");
        send(4,-1,8,"/join"); send(5,-1,8,"/habit Other private goal");
        send(6,-2,7,"/join"); send(7,-2,7,"/habit Other group goal");
        send(8,-1,7,"@family_coach_bot I read five pages");
        when(provider.reply(anyString(),anyString())).thenReturn("Please confirm your total using /done HABIT_ID TOTAL.");
        worker.processOne();
        verify(provider).reply(contains("five pages"),argThat(context->context.contains("My reading") && !context.contains("Other private goal") && !context.contains("Other group goal")));
        assertEquals(0,count("habit_check_ins")); assertTrue(reply().contains("/done"));
        assertNull(jdbc.queryForObject("SELECT message_text FROM telegram_conversations",String.class));
    }
    @Test void voiceTranscriptionIsNotAnExecutableCommand() {
        send(1,-1,7,"/join"); send(2,-1,7,"/ai on");
        commands.accept(new TelegramUpdate(3,-1,7,3,null,"supergroup","Person","","",false,"family_coach_bot","voice_file",10,10));
        when(voices.download("voice_file")).thenReturn("OggSsample".getBytes());
        when(provider.transcribe(any())).thenReturn("/done 1 999");
        when(provider.reply(anyString(),anyString())).thenReturn("To record progress, send /done with your habit ID yourself.");
        worker.processOne();
        verify(provider).transcribe(any()); verify(provider).reply(eq("/done 1 999"),anyString());
        assertEquals(0,count("habit_check_ins")); assertTrue(reply().contains("yourself"));
    }
    @Test void consentRevocationCancelsInFlightPublication() {
        send(1,-1,7,"/join"); send(2,-1,7,"/ai on"); send(3,-1,7,"@family_coach_bot help");
        TelegramConversationJob job=queue.claim().orElseThrow();
        send(4,-1,7,"/ai off"); int replies=count("telegram_outbox");
        queue.complete(job,"Must never publish");
        assertEquals(replies,count("telegram_outbox")); assertFalse(reply().contains("Must never"));
    }
    @Test void forgetDeletesOnlyOwnChatAndPreservesAnonymousUsageCounter() {
        send(1,-1,7,"/join"); send(2,-1,7,"/habit My goal"); send(3,-1,7,"/ai on");
        send(4,-1,7,"@family_coach_bot help"); send(5,-1,8,"/join"); send(6,-1,8,"/habit Other goal");
        send(7,-1,7,"/forget confirm");
        assertTrue(store.member(-1,7).isEmpty()); assertTrue(store.member(-1,8).isPresent());
        assertEquals(1,count("habits")); assertEquals(0,count("telegram_conversations"));
        assertEquals(1,jdbc.queryForObject("SELECT accepted_requests FROM telegram_ai_daily_totals",Integer.class));
        assertTrue(reply().contains("deleted"));
    }
    @Test void quotasCountFailedRequests() {
        send(1,-1,7,"/join"); send(2,-1,7,"/ai on");
        long member=store.member(-1,7).orElseThrow().id();
        for(int i=0;i<20;i++) jdbc.update("INSERT INTO telegram_conversations(membership_id,message_id,message_text,input_kind,status,created_at) VALUES (?,?,NULL,'TEXT','FAILED',?)",member,100+i,Timestamp.from(Instant.now().minusSeconds(60)));
        send(3,-1,7,"@family_coach_bot help");
        assertEquals(20,count("telegram_conversations")); assertTrue(reply().contains("allowance"));
    }
    @Test void consentRevokedDuringVoiceDownloadPreventsTranscription() {
        send(1,-1,7,"/join"); send(2,-1,7,"/ai on");
        commands.accept(new TelegramUpdate(3,-1,7,3,null,"supergroup","Person","","",false,"family_coach_bot","voice_file",10,10));
        when(voices.download("voice_file")).thenAnswer(ignored->{ send(4,-1,7,"/ai off"); return "OggSsample".getBytes(); });
        worker.processOne(); verifyNoInteractions(provider);
    }
    @Test void cancelledJobDoesNotResumeAfterConsentIsReenabled() {
        send(1,-1,7,"/join"); send(2,-1,7,"/ai on");
        commands.accept(new TelegramUpdate(3,-1,7,3,null,"supergroup","Person","","",false,"family_coach_bot","voice_file",10,10));
        when(voices.download("voice_file")).thenAnswer(ignored->{
            send(4,-1,7,"/ai off"); send(5,-1,7,"/ai on"); return "OggSsample".getBytes();
        });
        worker.processOne(); verifyNoInteractions(provider);
    }
    @Test void synthesisQuotaSurvivesDeletingAndRejoiningProfile() {
        send(1,-1,7,"/join"); send(2,-1,7,"/ai on");
        TelegramOutgoing outgoing=new TelegramOutgoing(1,-1,2L,7L,null,"hello","audio",1,"lease");
        for(int i=0;i<20;i++) assertTrue(usage.reserveSpeech(outgoing));
        assertFalse(usage.reserveSpeech(outgoing));
        send(3,-1,7,"/forget confirm"); send(4,-1,7,"/join"); send(5,-1,7,"/ai on");
        assertFalse(usage.reserveSpeech(outgoing));
        assertEquals(20,jdbc.queryForObject("SELECT accepted_requests FROM telegram_ai_daily_totals",Integer.class));
    }
    @Test void optingOutCancelsAlreadyQueuedSummary() {
        send(1,-1,7,"/join"); send(2,-1,7,"/habit Private goal"); send(3,-1,7,"/share on");
        send(4,-1,8,"/join"); send(5,-1,8,"/summary"); send(6,-1,7,"/share off");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM telegram_outbox WHERE message_kind='SUMMARY' AND status IN ('PENDING','SENDING')",Integer.class));
    }
}
