package com.webex.summarizer.summarizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LlmSummarizer {
    
    private static final Logger logger = LoggerFactory.getLogger(LlmSummarizer.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String llmApiEndpoint;
    private final String llmApiKey;
    private final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    public LlmSummarizer(String llmApiEndpoint, String llmApiKey) {
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.llmApiEndpoint = llmApiEndpoint;
        this.llmApiKey = llmApiKey;
    }
    
    public String generateSummary(Conversation conversation) throws IOException {
        String conversationText = formatConversation(conversation);
        
        // Prepare the request to the LLM API
        String prompt = "Please provide a concise summary of the following conversation from a Cisco WebEx room. " +
                "Focus on the key points, decisions made, action items, and significant information shared. " +
                "Structure your response to highlight the main topics discussed.\n\n" + conversationText;
        
        // Create request body for OpenAI API
        var messagesArray = objectMapper.createArrayNode();
        var userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messagesArray.add(userMessage);
        
        var rootNode = objectMapper.createObjectNode();
        rootNode.set("messages", messagesArray);
        rootNode.put("model", "gpt-3.5-turbo");
        rootNode.put("max_tokens", 500);
        rootNode.put("temperature", 0.7);
        
        String requestBody = objectMapper.writeValueAsString(rootNode);
        
        Request request = new Request.Builder()
                .url(llmApiEndpoint)
                .addHeader("Authorization", "Bearer " + llmApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, JSON))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LLM API request failed: " + response);
            }
            
            JsonNode responseJson = objectMapper.readTree(response.body().string());
            
            // Extract the summary text from the OpenAI response
            String summary = responseJson.path("choices").get(0).path("message").path("content").asText();
            
            return summary;
        }
    }
    
    private String formatConversation(Conversation conversation) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Room: ").append(conversation.getRoom().getTitle()).append("\n\n");
        
        List<Message> messages = conversation.getMessages();
        for (Message message : messages) {
            sb.append("Time: ").append(DATE_FORMATTER.format(message.getCreated())).append("\n");
            sb.append("From: ").append(message.getPersonEmail()).append("\n");
            sb.append("Message: ").append(message.getText()).append("\n\n");
        }
        
        return sb.toString();
    }
}