package com.webex.summarizer.search;

import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for searching through WebEx conversations and retrieving relevant messages
 */
public class ConversationSearch {

    private static final Logger logger = LoggerFactory.getLogger(ConversationSearch.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Search for messages in a conversation that match the given query
     * 
     * @param conversation The conversation to search through
     * @param query The search query
     * @return A list of messages that match the query
     */
    public List<Message> searchMessages(Conversation conversation, String query) {
        if (conversation == null || conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
            logger.warn("No messages found in conversation");
            return new ArrayList<>();
        }
        
        String queryLower = query.toLowerCase();
        List<Message> matchingMessages = conversation.getMessages().stream()
                .filter(message -> {
                    if (message.getText() == null) return false;
                    return message.getText().toLowerCase().contains(queryLower);
                })
                .collect(Collectors.toList());
        
        logger.info("Found {} messages matching query: '{}'", matchingMessages.size(), query);
        return matchingMessages;
    }
    
    /**
     * Search for messages in a conversation within a specific date range
     * 
     * @param conversation The conversation to search through
     * @param query The search query
     * @param startDate The start date for filtering messages (inclusive)
     * @param endDate The end date for filtering messages (inclusive)
     * @return A list of messages that match the query within the date range
     */
    public List<Message> searchMessages(Conversation conversation, String query, ZonedDateTime startDate, ZonedDateTime endDate) {
        if (conversation == null || conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
            logger.warn("No messages found in conversation");
            return new ArrayList<>();
        }
        
        String queryLower = query.toLowerCase();
        List<Message> matchingMessages = conversation.getMessages().stream()
                .filter(message -> {
                    if (message.getText() == null) return false;
                    
                    ZonedDateTime created = message.getCreated();
                    boolean afterStartDate = startDate == null || !created.isBefore(startDate);
                    boolean beforeEndDate = endDate == null || !created.isAfter(endDate);
                    
                    return message.getText().toLowerCase().contains(queryLower) && 
                           afterStartDate && beforeEndDate;
                })
                .collect(Collectors.toList());
        
        logger.info("Found {} messages matching query: '{}' within date range", matchingMessages.size(), query);
        return matchingMessages;
    }
    
    /**
     * Get messages surrounding a specific message to provide context
     * 
     * @param conversation The conversation containing the messages
     * @param message The message to find context for
     * @param contextSize Number of messages to include before and after (each)
     * @return A list of messages including the target message and surrounding context
     */
    public List<Message> getMessageContext(Conversation conversation, Message message, int contextSize) {
        List<Message> allMessages = conversation.getMessages();
        List<Message> contextMessages = new ArrayList<>();
        
        int messageIndex = -1;
        for (int i = 0; i < allMessages.size(); i++) {
            if (allMessages.get(i).getId().equals(message.getId())) {
                messageIndex = i;
                break;
            }
        }
        
        if (messageIndex == -1) {
            logger.warn("Message not found in conversation");
            contextMessages.add(message);
            return contextMessages;
        }
        
        // Get messages before the target message
        int startIndex = Math.max(0, messageIndex - contextSize);
        for (int i = startIndex; i < messageIndex; i++) {
            contextMessages.add(allMessages.get(i));
        }
        
        // Add the target message
        contextMessages.add(allMessages.get(messageIndex));
        
        // Get messages after the target message
        int endIndex = Math.min(allMessages.size() - 1, messageIndex + contextSize);
        for (int i = messageIndex + 1; i <= endIndex; i++) {
            contextMessages.add(allMessages.get(i));
        }
        
        return contextMessages;
    }
    
    /**
     * Format search results for display
     * 
     * @param messages The list of messages to format
     * @return A formatted string representation of the messages
     */
    public String formatSearchResults(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            
            sb.append("─────────────────────────────────────────────\n");
            sb.append("Message ").append(i + 1).append(" of ").append(messages.size()).append("\n");
            sb.append("Time: ").append(DATE_FORMATTER.format(message.getCreated())).append("\n");
            sb.append("From: ").append(message.getPersonEmail()).append("\n");
            sb.append("Content: ").append(message.getText()).append("\n\n");
        }
        
        return sb.toString();
    }
}