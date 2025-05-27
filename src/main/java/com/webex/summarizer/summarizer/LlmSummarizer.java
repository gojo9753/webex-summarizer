package com.webex.summarizer.summarizer;

import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class LlmSummarizer {
    
    private static final Logger logger = LoggerFactory.getLogger(LlmSummarizer.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final BedrockClient bedrockClient;
    
    public LlmSummarizer(String awsProfile, String awsRegion, String modelId) {
        this.bedrockClient = new BedrockClient(awsProfile, awsRegion, modelId);
        logger.info("LLM Summarizer initialized with AWS Bedrock (profile: {}, region: {}, model: {})",
                    awsProfile, awsRegion, modelId);
    }
    
    public String generateSummary(Conversation conversation) throws IOException {
        String conversationText = formatConversation(conversation);
        
        String prompt = "Please provide a concise summary of the following conversation from a Cisco WebEx room. " +
                "Focus on the key points, decisions made, action items, and significant information shared. " +
                "Structure your response to highlight the main topics discussed.\n\n" + conversationText;
        
        try {
            logger.info("Generating summary using AWS Bedrock...");
            String summary = bedrockClient.generateText(prompt);
            logger.info("Summary generated successfully");
            return summary;
        } catch (IOException e) {
            logger.error("Failed to generate summary: {}", e.getMessage());
            throw e;
        }
    }
    
    public List<Map<String, String>> listAvailableModels() {
        return bedrockClient.listAvailableModels();
    }
    
    public String getModelDetails(String modelId) {
        return bedrockClient.getModelDetails(modelId);
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