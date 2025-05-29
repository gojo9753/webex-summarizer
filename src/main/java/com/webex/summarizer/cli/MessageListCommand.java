package com.webex.summarizer.cli;

import com.webex.summarizer.api.WebExMessageService;
import com.webex.summarizer.api.WebExRoomService;
import com.webex.summarizer.auth.WebExAuthenticator;
import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import com.webex.summarizer.storage.ConversationStorage;
import com.webex.summarizer.util.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list-messages", description = "List messages from a WebEx room or saved conversation", mixinStandardHelpOptions = true)
public class MessageListCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(MessageListCommand.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Option(names = {"-c", "--config"}, description = "Path to config file")
    private String configPath = "config.properties";

    @Option(names = {"-o", "--output-dir"}, description = "Output directory for downloaded conversations")
    private String outputDir;

    @Option(names = {"--token"}, description = "WebEx API token to use")
    private String token;

    @Option(names = {"--room"}, description = "WebEx room ID to list messages from")
    private String roomId;

    @Option(names = {"--file"}, description = "Path to a saved conversation file to display")
    private String filePath;

    @Option(names = {"--limit"}, description = "Maximum number of messages to display per page")
    private Integer limit = 1000;
    
    @Option(names = {"--page"}, description = "Page number to display (starting from 1)")
    private Integer page = 1;
    
    @Option(names = {"--save"}, description = "Save the downloaded conversation to file")
    private boolean saveToFile = false;
    
    @Option(names = {"--references"}, description = "Show reference IDs for messages", defaultValue = "true")
    private boolean showReferences = true;

    @Override
    public Integer call() throws Exception {
        try {
            ConfigLoader configLoader = new ConfigLoader(configPath);
            
            // Set output directory
            if (outputDir == null) {
                outputDir = configLoader.getProperty("storage.directory", "conversations");
            }
            
            ConversationStorage storage = new ConversationStorage(outputDir);
            
            Conversation conversation;
            
            if (filePath != null) {
                // Load conversation from file
                if (!Paths.get(filePath).toFile().exists()) {
                    System.err.println("File not found: " + filePath);
                    return 1;
                }
                
                System.out.println("Loading conversation from " + filePath);
                conversation = storage.loadConversation(filePath);
            } else if (roomId != null) {
                // Download conversation from WebEx
                // Get token from command line or config file
                if (token == null) {
                    token = configLoader.getProperty("webex.token");
                    if (token == null || token.isEmpty()) {
                        System.err.println("No WebEx token found. Please provide a token with --token or set webex.token in config.properties.");
                        return 1;
                    }
                }
                
                // Initialize authenticator and API services
                WebExAuthenticator authenticator = new WebExAuthenticator(token);
                WebExRoomService roomService = new WebExRoomService(authenticator);
                WebExMessageService messageService = new WebExMessageService(authenticator, roomService);
                
                System.out.println("Downloading conversation from room " + roomId + "...");
                conversation = messageService.downloadConversation(roomId);
                
                if (saveToFile) {
                    storage.saveConversation(conversation);
                    System.out.println("Conversation saved to file in " + outputDir);
                }
            } else {
                System.err.println("Please specify either a room ID (--room) or a file path (--file).");
                return 1;
            }
            
            // Display conversation messages
            displayMessages(conversation, limit);
            return 0;
            
        } catch (Exception e) {
            logger.error("Failed to list messages: {}", e.getMessage(), e);
            System.err.println("Failed to list messages: " + e.getMessage());
            return 1;
        }
    }
    
    private void displayMessages(Conversation conversation, Integer messagesPerPage) {
        List<Message> allMessages = conversation.getMessages();
        int totalMessages = allMessages.size();
        
        // Determine pagination parameters
        int pageSize = (messagesPerPage != null && messagesPerPage > 0) ? messagesPerPage : 1000;
        int totalPages = totalMessages > 0 ? (int) Math.ceil((double) totalMessages / pageSize) : 1;
        
        // Validate page number
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        
        // Calculate start and end indices for the current page
        int startIndex = (page - 1) * pageSize;
        
        // Validate indices to prevent IndexOutOfBoundsException
        if (startIndex < 0) startIndex = 0;
        if (startIndex >= totalMessages) startIndex = Math.max(0, totalMessages - pageSize);
        
        int endIndex = Math.min(startIndex + pageSize, totalMessages);
        
        // Get messages for the current page
        List<Message> pageMessages = totalMessages > 0 ? allMessages.subList(startIndex, endIndex) : List.of();
        
        // Print conversation header with styling
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         CONVERSATION MESSAGES                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println("Room: " + conversation.getRoom().getTitle());
        System.out.println("Messages: " + pageMessages.size() + " of " + totalMessages + 
                         " (Page " + page + " of " + totalPages + ")");
        System.out.println("Download Date: " + formatDateTime(conversation.getDownloadDate()));
        
        // Show pagination navigation help if there are multiple pages
        if (totalPages > 1) {
            System.out.println("");
            System.out.println("Navigation:");
            if (page > 1) {
                System.out.println("  Previous page: --page " + (page - 1));
            }
            if (page < totalPages) {
                System.out.println("  Next page: --page " + (page + 1));
            }
        }
        
        System.out.println("");
        
        String currentDate = null;
        
        // If no messages to display, show a message
        if (pageMessages.isEmpty()) {
            System.out.println("\nNo messages found in this conversation.");
            return;
        }
        
        // Group messages by date for better readability
        for (int i = 0; i < pageMessages.size(); i++) {
            Message message = pageMessages.get(i);
            
            // Calculate absolute message number across all pages
            int messageNumber = startIndex + i + 1;
            
            String messageDate = message.getCreated().format(DATE_FORMATTER);
            
            // Print date header when day changes
            if (currentDate == null || !currentDate.equals(messageDate)) {
                currentDate = messageDate;
                System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
                System.out.println("│ " + currentDate + "                                                          │");
                System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
            }
            
            // Show reference ID, time and author with clear formatting
            if (showReferences) {
                System.out.println("\n#" + formatMessageNumber(messageNumber) + " [" + 
                                  message.getCreated().format(TIME_FORMATTER) + "] " + 
                                  formatSender(message.getPersonEmail()));
            } else {
                System.out.println("\n[" + message.getCreated().format(TIME_FORMATTER) + "] " + 
                                  formatSender(message.getPersonEmail()));
            }
            
            // Handle multiline message text with proper indentation
            if (message.getText() != null) {
                String[] lines = message.getText().split("\n");
                for (String line : lines) {
                    System.out.println("    " + line);
                }
            }
            
            // Add the message ID as a reference
            if (showReferences) {
                System.out.println("    [ID: " + message.getId() + "]");
            }
            
            System.out.println("────────────────────────────────────────────────────────────────────────────────");
        }
        
        // Show pagination navigation help at the bottom too
        if (totalPages > 1) {
            System.out.println("\nPage " + page + " of " + totalPages);
            if (page > 1) {
                System.out.println("Previous page: --page " + (page - 1));
            }
            if (page < totalPages) {
                System.out.println("Next page: --page " + (page + 1));
            }
        }
    }
    
    /**
     * Format message number with leading zeros to ensure consistent width
     */
    private String formatMessageNumber(int number) {
        return String.format("%04d", number);
    }
    
    /**
     * Helper method to format email addresses for display
     */
    private String formatSender(String email) {
        if (email == null) {
            return "Unknown sender";
        }
        
        // Extract the name part of the email if possible
        if (email.contains("@")) {
            String name = email.substring(0, email.indexOf('@'));
            name = name.replace(".", " ");
            
            // Capitalize words
            StringBuilder formattedName = new StringBuilder();
            String[] words = name.split(" ");
            for (String word : words) {
                if (!word.isEmpty()) {
                    formattedName.append(Character.toUpperCase(word.charAt(0)))
                              .append(word.substring(1))
                              .append(" ");
                }
            }
            
            return formattedName.toString().trim();
        }
        
        return email;
    }
    
    /**
     * Helper method to format date-time values
     */
    private String formatDateTime(java.time.ZonedDateTime dateTime) {
        if (dateTime == null) {
            return "Unknown date";
        }
        
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}