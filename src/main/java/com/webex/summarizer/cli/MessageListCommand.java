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

    @Option(names = {"--limit"}, description = "Maximum number of messages to display")
    private Integer limit;
    
    @Option(names = {"--save"}, description = "Save the downloaded conversation to file")
    private boolean saveToFile = false;

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
    
    private void displayMessages(Conversation conversation, Integer limit) {
        List<Message> messages = conversation.getMessages();
        
        // Apply limit if specified
        if (limit != null && limit > 0 && limit < messages.size()) {
            messages = messages.subList(0, limit);
        }
        
        // Print conversation header with styling
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         CONVERSATION MESSAGES                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println("Room: " + conversation.getRoom().getTitle());
        System.out.println("Messages: " + messages.size() + " of " + conversation.getMessages().size());
        System.out.println("Download Date: " + formatDateTime(conversation.getDownloadDate()));
        System.out.println("");
        
        String currentDate = null;
        
        // Group messages by date for better readability
        for (Message message : messages) {
            String messageDate = message.getCreated().format(DATE_FORMATTER);
            
            // Print date header when day changes
            if (currentDate == null || !currentDate.equals(messageDate)) {
                currentDate = messageDate;
                System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
                System.out.println("│ " + currentDate + "                                                          │");
                System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
            }
            
            // Show time and author with clear formatting
            System.out.println("\n[" + message.getCreated().format(TIME_FORMATTER) + "] " + 
                              formatSender(message.getPersonEmail()));
            
            // Handle multiline message text with proper indentation
            if (message.getText() != null) {
                String[] lines = message.getText().split("\n");
                for (String line : lines) {
                    System.out.println("    " + line);
                }
            }
            
            System.out.println("────────────────────────────────────────────────────────────────────────────────");
        }
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