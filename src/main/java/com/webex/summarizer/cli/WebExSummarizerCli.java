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
import com.webex.summarizer.util.SummaryFormatter;
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
            SummaryCommand.class,
            SearchCommand.class
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
    
    @Option(names = {"--page"}, description = "Page number to display (starting from 1)")
    private Integer page = 1;
    
    @Option(names = {"--limit"}, description = "Maximum number of messages to display per page")
    private Integer messagesPerPage = 1000;
    
    @Option(names = {"--references"}, description = "Show reference IDs for messages")
    private boolean showReferences = true;
    
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
            
            List<Message> allMessages = conversation.getMessages();
            int totalMessages = allMessages.size();
            
            // Determine pagination parameters
            int pageSize = messagesPerPage != null ? messagesPerPage : 1000;
            int totalPages = (int) Math.ceil((double) totalMessages / pageSize);
            
            // Validate page number
            if (page < 1) page = 1;
            if (page > totalPages) page = totalPages;
            
            // Calculate start and end indices for the current page
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalMessages);
            
            // Get messages for the current page
            List<Message> pageMessages = allMessages.subList(startIndex, endIndex);
            
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
            
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            String currentDate = null;
            
            // Group messages by date for better readability
            for (int i = 0; i < pageMessages.size(); i++) {
                Message message = pageMessages.get(i);
                
                // Calculate absolute message number across all pages
                int messageNumber = startIndex + i + 1;
                
                String messageDate = message.getCreated().format(dateFormatter);
                
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
                                      message.getCreated().format(timeFormatter) + "] " + 
                                      formatSender(message.getPersonEmail()));
                } else {
                    System.out.println("\n[" + message.getCreated().format(timeFormatter) + "] " + 
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
            
            // If a summary exists, display it with enhanced formatting
            if (conversation.getSummary() != null && !conversation.getSummary().isEmpty()) {
                // Use our enhanced formatting method
                displayFormattedSummary(conversation.getSummary());
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
     * Format message number with leading zeros for consistent width
     */
    private String formatMessageNumber(int number) {
        return String.format("%04d", number);
    }
    
    /**
     * Format and display a summary with enhanced visual presentation
     */
    private void displayFormattedSummary(String summary) {
        SummaryFormatter.printFormattedSummary(summary);
    }
    
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new WebExSummarizerCli());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}