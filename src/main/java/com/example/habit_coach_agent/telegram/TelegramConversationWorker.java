package com.example.habit_coach_agent.telegram;

import com.example.habit_coach_agent.coaching.CoachProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="telegram.enabled",havingValue="true")
public class TelegramConversationWorker {
    private final TelegramConversations queue;
    private final CoachProvider provider;
    private final TelegramVoiceDownloader voices;
    public TelegramConversationWorker(TelegramConversations queue,CoachProvider provider,TelegramVoiceDownloader voices) {
        this.queue=queue; this.provider=provider; this.voices=voices;
    }
    @Scheduled(fixedDelayString="${telegram.conversation-poll-ms:1000}")
    public void processOne() {
        queue.claim().ifPresent(job->{
            try {
                String reply=TelegramJobDeadline.run(()->{
                    if(!queue.permitted(job)) return "";
                    String text=job.text();
                    if(job.voiceFileId()!=null && !job.voiceFileId().isBlank()) {
                        byte[] audio=voices.download(job.voiceFileId());
                        try {
                            if(!queue.permitted(job)) return "";
                            text=provider.transcribe(audio);
                        }
                        finally { java.util.Arrays.fill(audio,(byte)0); }
                    }
                    if(!queue.permitted(job)) return "";
                    return provider.reply(text,job.context());
                },90);
                queue.complete(job,reply);
            } catch(Exception unavailable) {
                if(unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
                queue.complete(job,"I couldn't process that coaching request right now. Try text again or use /help. Your progress has not changed.");
            }
        });
    }
}
