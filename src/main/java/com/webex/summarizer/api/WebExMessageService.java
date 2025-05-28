package com.webex.summarizer.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.webex.summarizer.auth.WebExAuthenticator;
import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import com.webex.summarizer.model.Room;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebExMessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebExMessageService.class);
    private static final String API_BASE_URL = "https://webexapis.com/v1";
    private static final int MAX_MESSAGES_PER_REQUEST = 1000;
    
    private final WebExAuthenticator authenticator;
    private final WebExRoomService roomService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public WebExMessageService(WebExAuthenticator authenticator, WebExRoomService roomService) {
        this.authenticator = authenticator;
        this.roomService = roomService;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public Conversation downloadConversation(String roomId) throws IOException {
        if (!authenticator.isAuthenticated()) {
            throw new IllegalStateException("Not authenticated");
        }
        
        Room room = roomService.getRoom(roomId);
        List<Message> allMessages = new ArrayList<>();
        String oldestMessageId = null;
        boolean hasMore = true;
        
        while (hasMore) {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(API_BASE_URL + "/messages").newBuilder()
                    .addQueryParameter("roomId", roomId)
                    .addQueryParameter("max", String.valueOf(MAX_MESSAGES_PER_REQUEST));

            // Add beforeMessage parameter for pagination if we have an oldest message
            if (oldestMessageId != null) {
                urlBuilder.addQueryParameter("beforeMessage", oldestMessageId);
            }
            
            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "Bearer " + authenticator.getAccessToken())
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                
                String responseBody = response.body().string();
                JsonNode rootNode = objectMapper.readTree(responseBody);
                JsonNode itemsNode = rootNode.path("items");
                
                List<Message> messages = new ArrayList<>();
                for (JsonNode itemNode : itemsNode) {
                    Message message = objectMapper.treeToValue(itemNode, Message.class);
                    messages.add(message);
                }
                
                int messagesReceived = messages.size();
                
                // Check if we got fewer messages than requested (indicating we're at the end)
                // or if we got no messages
                if (messagesReceived == 0 || messagesReceived < MAX_MESSAGES_PER_REQUEST) {
                    hasMore = false;
                    logger.info("Reached end of messages with {} messages in this batch", messagesReceived);
                } else if (!messages.isEmpty()) {
                    // Get the ID of the oldest message for the next pagination request
                    // Assuming messages are sorted by creation time in descending order
                    oldestMessageId = messages.get(messages.size() - 1).getId();
                    logger.info("Downloaded batch of {} messages, continuing with beforeMessage={}", 
                              messagesReceived, oldestMessageId);
                }
                
                allMessages.addAll(messages);
                logger.info("Downloaded {} messages so far", allMessages.size());
            }
        }
        
        Conversation conversation = new Conversation();
        conversation.setRoom(room);
        conversation.setMessages(allMessages);
        return conversation;
    }
}