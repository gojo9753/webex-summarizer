package com.webex.summarizer.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.webex.summarizer.model.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConversationStorage {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationStorage.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private final ObjectMapper objectMapper;
    private final String storageDir;
    
    public ConversationStorage(String storageDir) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.storageDir = storageDir;
        
        // Create storage directory if it doesn't exist
        createStorageDirectory();
    }
    
    private void createStorageDirectory() {
        File dir = new File(storageDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("Created storage directory: {}", storageDir);
            } else {
                logger.error("Failed to create storage directory: {}", storageDir);
            }
        }
    }
    
    public void saveConversation(Conversation conversation) throws IOException {
        String roomId = conversation.getRoom().getId();
        String roomName = sanitizeFileName(conversation.getRoom().getTitle());
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        
        String filename = String.format("%s_%s_%s.json", roomName, roomId, timestamp);
        Path filePath = Paths.get(storageDir, filename);
        
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), conversation);
        logger.info("Conversation saved to: {}", filePath);
    }
    
    public Conversation loadConversation(String filePath) throws IOException {
        return objectMapper.readValue(new File(filePath), Conversation.class);
    }
    
    public File[] listConversationFiles() {
        File dir = new File(storageDir);
        return dir.listFiles((d, name) -> name.endsWith(".json"));
    }
    
    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
    
    public void saveSummary(Conversation conversation, String summary) throws IOException {
        // Update the conversation with the summary
        conversation.setSummary(summary);
        
        // Find the existing file for this conversation
        File[] files = listConversationFiles();
        for (File file : files) {
            Conversation existingConversation = loadConversation(file.getAbsolutePath());
            if (existingConversation.getRoom().getId().equals(conversation.getRoom().getId())) {
                // Update the existing file with the new summary
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, conversation);
                logger.info("Updated conversation with summary: {}", file.getAbsolutePath());
                return;
            }
        }
        
        // If no existing file was found, save as a new file
        saveConversation(conversation);
    }
}