package com.example.habit_coach_agent.telegram;

import tools.jackson.databind.JsonNode;

/** The small supported subset of Telegram's update; no raw payload is retained. */
public record TelegramUpdate(long updateId, long chatId, long userId, long messageId,
                             Long threadId, String chatType, String firstName,
                             String username, String text, boolean bot, String replyBotUsername,
                             String voiceFileId, int voiceDuration, long voiceSize) {
    public TelegramUpdate(long updateId,long chatId,long userId,long messageId,Long threadId,
            String chatType,String firstName,String username,String text,boolean bot) {
        this(updateId,chatId,userId,messageId,threadId,chatType,firstName,username,text,bot,"","",0,0);
    }
    public static TelegramUpdate from(JsonNode root) {
        if (!root.path("update_id").isIntegralNumber() || root.path("update_id").asLong() < 0)
            throw new IllegalArgumentException("An update_id is required");
        JsonNode message = root.path("message");
        JsonNode from = message.path("from");
        JsonNode chat = message.path("chat");
        Long thread = message.path("message_thread_id").isIntegralNumber()
                ? message.path("message_thread_id").asLong() : null;
        return new TelegramUpdate(root.path("update_id").asLong(), chat.path("id").asLong(),
                from.path("id").asLong(), message.path("message_id").asLong(), thread,
                chat.path("type").asText(""), from.path("first_name").asText("Participant"),
                from.path("username").asText(""), message.path("text").asText(""),
                from.path("is_bot").asBoolean(false),
                message.path("reply_to_message").path("from").path("is_bot").asBoolean(false)
                        ?message.path("reply_to_message").path("from").path("username").asText(""):"",
                message.path("voice").path("file_id").asText(""),
                message.path("voice").path("duration").asInt(0),
                message.path("voice").path("file_size").asLong(0));
    }

    public boolean supported() {
        return chatId != 0 && userId > 0 && messageId > 0 && !bot && (!text.isBlank() || !voiceFileId.isBlank())
                && (chatType.equals("private") || chatType.equals("group") || chatType.equals("supergroup"));
    }
}
