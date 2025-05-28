package com.webex.summarizer.cli;

import com.webex.summarizer.api.WebExMessageService;
import com.webex.summarizer.api.WebExRoomService;
import com.webex.summarizer.auth.WebExAuthenticator;
import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import com.webex.summarizer.model.Room;
import com.webex.summarizer.storage.ConversationStorage;
import com.webex.summarizer.summarizer.LlmSummarizer;
import com.webex.summarizer.util.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(name = "webex-summarizer", 
        description = "Download and summarize WebEx conversations",
        mixinStandardHelpOptions = true,
        version = "1.0",
        subcommands = {
            ModelListCommand.class,
            RoomListCommand.class,
            MessageListCommand.class,
            SummaryCommand.class
        })
public class WebExSummarizerCli implements Callable<Integer> {
    
    private static final Logger logger = LoggerFactory.getLogger(WebExSummarizerCli.class);
    
    @Option(names = {"-c", "--config"}, description = "Path to config file")
    private String configPath = "config.properties";
    
    @Option(names = {"-o", "--output-dir"}, description = "Output directory for downloaded conversations")
    private String outputDir;
    
    @Option(names = {"-a", "--auth"}, description = "Perform WebEx authentication")
    private boolean auth = false;
    
    @Option(names = {"--token"}, description = "Set WebEx API token directly")
    private String token;
    
    @Option(names = {"-l", "--list-rooms"}, description = "List available WebEx rooms")
    private boolean listRooms = false;
    
    @Option(names = {"-r", "--room"}, description = "WebEx room ID to download")
    private String roomId;
    
    @Option(names = {"-s", "--summarize"}, description = "Generate summary for the downloaded conversation")
    private boolean summarize = false;
    
@Option(names = {"--read"}, description = "Read conversation without summarizing")
    private boolean readOnly = false;
    
    @Option(names = {"--list-files"}, description = "List downloaded conversation files")
    private boolean listFiles = false;
    
    @Parameters(arity = "0..1", description = "Path to specific conversation file to summarize")
    private String filePath;
    
    private ConfigLoader configLoader;
    private WebExAuthenticator authenticator;
    private WebExRoomService roomService;
    private WebExMessageService messageService;
    private ConversationStorage storage;
    private LlmSummarizer summarizer;
    
    @Option(names = {"-p", "--aws-profile"}, description = "AWS profile to use")
    private String awsProfile;
    
    @Option(names = {"--region"}, description = "AWS region to use")
    private String awsRegion;
    
    @Option(names = {"-m", "--model"}, description = "AWS Bedrock model ID to use")
    private String modelId;
    
    @Override
    public Integer call() {
        try {
            // Initialize components
            configLoader = new ConfigLoader(configPath);
            
            if (outputDir == null) {
                outputDir = configLoader.getProperty("storage.directory", "conversations");
            }
            
            storage = new ConversationStorage(outputDir);
            
            if (auth) {
                performAuthentication();
            } else if (token != null) {
                saveDirectToken();
            } else if (listFiles) {
                listConversationFiles();
            } else if (listRooms) {
                initApiClients();
                listAvailableRooms();
            } else if (roomId != null) {
                initApiClients();
                downloadRoomConversation(roomId);
            } else if (filePath != null && summarize) {
                initApiClients();
                summarizeExistingConversation(filePath);
            } else if (filePath != null && readOnly) {
                readConversation(filePath);
            } else {
                System.out.println("No command specified. Use --help for usage information.");
            }
            return 0;
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
    
    private void initApiClients() throws IOException {
        // Load token directly from config
        String token = configLoader.getProperty("webex.token");
        if (token == null || token.isEmpty()) {
            throw new IOException("No WebEx token found. Please set webex.token in config.properties.");
        }
        
        // Initialize authenticator with token
        authenticator = new WebExAuthenticator(token);
        
        // Initialize API services
        roomService = new WebExRoomService(authenticator);
        messageService = new WebExMessageService(authenticator, roomService);
        
        // Initialize summarizer if needed
        if (summarize) {
            // Use AWS profile from command line or config file
            String profile = awsProfile != null ? awsProfile : configLoader.getProperty("aws.profile", "default");
            if ("rivendel".equals(profile)) {
                logger.info("Using 'rivendel' AWS profile as specified");
            }
            
            // Use AWS region from command line or config file
            String region = awsRegion != null ? awsRegion : configLoader.getProperty("aws.region", "us-east-1");
            
            // Use model ID from command line or config file
            String model = modelId != null ? modelId : configLoader.getProperty("aws.bedrock.model", "anthropic.claude-v2");
            
            summarizer = new LlmSummarizer(profile, region, model);
            logger.info("LLM Summarizer initialized with AWS Bedrock (profile: {}, region: {}, model: {})",
                        profile, region, model);
        }
    }
    
    private void performAuthentication() {
        try {
            System.out.println("Enter your WebEx token:");
            Scanner scanner = new Scanner(System.in);
            String token = scanner.nextLine().trim();
            
            if (token == null || token.isEmpty()) {
                System.err.println("Error: Token cannot be empty");
                return;
            }
            
            // Save the token in the config
            configLoader.setProperty("webex.token", token);
            configLoader.saveProperties();
            
            System.out.println("Token saved successfully.");
        } catch (Exception e) {
            logger.error("Authentication error: {}", e.getMessage(), e);
            System.err.println("Authentication error: " + e.getMessage());
        }
    }
    
    private void saveDirectToken() {
        try {
            configLoader.setProperty("webex.token", token);
            configLoader.saveProperties();
            System.out.println("Token saved successfully.");
        } catch (Exception e) {
            logger.error("Error saving token: {}", e.getMessage(), e);
            System.err.println("Error saving token: " + e.getMessage());
        }
    }
    
    private void listAvailableRooms() {
        try {
            List<Room> rooms = roomService.listRooms();
            System.out.println("Available WebEx rooms:");
            for (Room room : rooms) {
                System.out.printf("ID: %s, Title: %s, Type: %s%n",
                        room.getId(), room.getTitle(), room.getType());
            }
        } catch (IOException e) {
            logger.error("Failed to list rooms: {}", e.getMessage(), e);
            System.err.println("Failed to list rooms: " + e.getMessage());
        }
    }
    
    private void downloadRoomConversation(String roomId) {
        try {
            System.out.println("Downloading conversation from room " + roomId + "...");
            Conversation conversation = messageService.downloadConversation(roomId);
            
            storage.saveConversation(conversation);
            System.out.println("Conversation downloaded successfully with " + 
                    conversation.getMessages().size() + " messages.");
            
            if (summarize) {
                System.out.println("Generating summary...");
                String summary = summarizer.generateSummary(conversation);
                storage.saveSummary(conversation, summary);
                
                // Format and display the summary using the enhanced formatter
                displayFormattedSummary(summary);
            }
        } catch (IOException e) {
            logger.error("Failed to download conversation: {}", e.getMessage(), e);
            System.err.println("Failed to download conversation: " + e.getMessage());
        }
    }
    
    private void listConversationFiles() {
        File[] files = storage.listConversationFiles();
        if (files.length == 0) {
            System.out.println("No conversation files found in " + outputDir);
            return;
        }
        
        System.out.println("Available conversation files:");
        for (File file : files) {
            System.out.println(file.getName());
        }
    }
    
    private void summarizeExistingConversation(String filePath) {
        try {
            if (!Paths.get(filePath).toFile().exists()) {
                System.err.println("File not found: " + filePath);
                return;
            }
            
            System.out.println("Loading conversation from " + filePath);
            Conversation conversation = storage.loadConversation(filePath);
            
            System.out.println("Generating summary...");
            String summary = summarizer.generateSummary(conversation);
            storage.saveSummary(conversation, summary);
            
            // Format and display the summary using the enhanced formatter
            displayFormattedSummary(summary);
        } catch (IOException e) {
            logger.error("Failed to summarize conversation: {}", e.getMessage(), e);
            System.err.println("Failed to summarize conversation: " + e.getMessage());
        }
    }
    
    private void readConversation(String filePath) {
        try {
            if (!Paths.get(filePath).toFile().exists()) {
                System.err.println("File not found: " + filePath);
                return;
            }
            
            System.out.println("Loading conversation from " + filePath);
            Conversation conversation = storage.loadConversation(filePath);
            
            // Print conversation header with styling
            System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                         CONVERSATION MESSAGES                              ║");
            System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
            System.out.println("Room: " + conversation.getRoom().getTitle());
            System.out.println("Messages: " + conversation.getMessages().size());
            System.out.println("Download Date: " + formatDateTime(conversation.getDownloadDate()));
            System.out.println("");
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String currentDate = null;
            
            // Group messages by date for better readability
            for (Message message : conversation.getMessages()) {
                String messageDate = message.getCreated().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                
                // Print date header when day changes
                if (currentDate == null || !currentDate.equals(messageDate)) {
                    currentDate = messageDate;
                    System.out.println("\n┌──────────────────────────────────────────────────────────────────────────────┐");
                    System.out.println("│ " + currentDate + "                                                          │");
                    System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
                }
                
                // Show time and author with clear formatting
                System.out.println("\n[" + message.getCreated().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + 
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
            
            // If a summary exists, display it with nice formatting
            if (conversation.getSummary() != null && !conversation.getSummary().isEmpty()) {
                System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════╗");
                System.out.println("║                             SUMMARY                                        ║");
                System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
                
                // Print each line of the summary with proper formatting
                String[] summaryLines = conversation.getSummary().split("\n");
                for (String line : summaryLines) {
                    if (line.trim().isEmpty()) {
                        System.out.println(); // preserve empty lines
                    } else {
                        System.out.println("  " + line);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read conversation: {}", e.getMessage(), e);
            System.err.println("Failed to read conversation: " + e.getMessage());
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
     * Helper method to format date-time values from ZonedDateTime
     */
    private String formatDateTime(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return "Unknown date";
        }
        
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * Format and display a summary with enhanced visual presentation
     */
    private void displayFormattedSummary(String summary) {
        // Add current timestamp to the header
        String currentTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        // Display the summary with improved formatting and timestamp
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                       CONVERSATION SUMMARY                                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Generated: " + currentTime + "                                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        
        // Process and print each line of the summary with enhanced formatting
        String[] summaryLines = summary.split("\n");
        boolean inActionItems = false;
        boolean inDecisions = false;
        
        for (String line : summaryLines) {
            if (line.trim().isEmpty()) {
                System.out.println(); // preserve empty lines
            } else if (line.trim().startsWith("**") && line.trim().endsWith("**")) {
                // Track which section we're in for special formatting
                String headerContent = line.trim().replaceAll("\\*\\*", "");
                inActionItems = headerContent.toLowerCase().contains("action") || 
                               headerContent.toLowerCase().contains("task");
                inDecisions = headerContent.toLowerCase().contains("decision") ||
                             headerContent.toLowerCase().contains("conclusion");
                
                // Create a more visually distinctive section header
                String decoration = getHeaderDecoration(headerContent);
                System.out.println();
                System.out.println(decoration);
                System.out.println("┏━━━━━━" + "━".repeat(headerContent.length()) + "━━━━━┓");
                System.out.println("┃  " + headerContent.toUpperCase() + "  ┃");
                System.out.println("┗━━━━━━" + "━".repeat(headerContent.length()) + "━━━━━┛");
                System.out.println(decoration);
            } else if (line.trim().startsWith("---")) {
                // Separator lines - convert to a nicer format with different styles
                System.out.println("  ∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙∙");
            } else if (line.trim().matches("^\\d+\\..*")) {
                // Numbered items with enhanced formatting based on section
                String number = line.trim().split("\\.", 2)[0];
                String content = line.trim().substring(number.length() + 1).trim();
                
                if (inActionItems) {
                    // Action items get a distinctive marker and formatting
                    System.out.println("  ➤ " + number + ". " + content);
                } else if (inDecisions) {
                    // Decisions get a different marker
                    System.out.println("  ✓ " + number + ". " + content);
                } else {
                    // Regular numbered items
                    System.out.println("  • " + number + ". " + content);
                }
            } else {
                // Regular text with context-aware indentation
                if (inActionItems && line.trim().toLowerCase().contains("by:") || 
                    line.trim().toLowerCase().contains("owner:") ||
                    line.trim().toLowerCase().contains("due:")) {
                    // Highlight ownership and deadlines in action items
                    System.out.println("      ↳ " + line.trim());
                } else {
                    // Standard indentation for regular text
                    System.out.println("    " + line);
                }
            }
        }
        
        // Add footer with legend for markers
        System.out.println("\n" + "═".repeat(76));
        System.out.println(" Legend:  • Regular Point   ➤ Action Item   ✓ Decision");
        System.out.println("═".repeat(76));
    }
    
    /**
     * Get decorative line based on section header content
     */
    private String getHeaderDecoration(String headerContent) {
        if (headerContent.toLowerCase().contains("overview")) {
            return "┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅┅";
        } else if (headerContent.toLowerCase().contains("action") || 
                  headerContent.toLowerCase().contains("task")) {
            return "⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥⬥";
        } else if (headerContent.toLowerCase().contains("decision") ||
                  headerContent.toLowerCase().contains("conclusion")) {
            return "◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈◈";
        } else if (headerContent.toLowerCase().contains("key")) {
            return "▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪";
        } else {
            return "────────────────────────────────────────────────────────────────────────────";
        }
    }
    
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new WebExSummarizerCli());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}