package com.example.habit_coach_agent.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.habit_coach_agent.engagement.EngagementDeliveryGuard;

@Component
@ConditionalOnProperty(name="telegram.enabled",havingValue="true")
public class TelegramOutboxWorker {
    private final TelegramOutbox outbox;
    private final TelegramSender sender;
    private final EngagementDeliveryGuard guard;
    public TelegramOutboxWorker(TelegramOutbox outbox,TelegramSender sender,EngagementDeliveryGuard guard) { this.outbox=outbox; this.sender=sender; this.guard=guard; }

    @Scheduled(fixedDelayString="${telegram.outbox-poll-ms:1000}")
    public void deliverOne() {
        outbox.claim().ifPresent(outgoing -> {
            if(!guard.allow(outgoing)) return;
            try { TelegramJobDeadline.run(()-> { sender.send(outgoing); return true; },75); outbox.delivered(outgoing); }
            catch(TelegramDeliveryException failure) { outbox.failed(outgoing,failure); }
            catch(Exception failure) {
                if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
                if(failure.getCause() instanceof TelegramDeliveryException classified) outbox.failed(outgoing,classified);
                else outbox.failed(outgoing,new TelegramDeliveryException("DELIVERY_FAILURE",false,0));
            }
        });
    }
}
