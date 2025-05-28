package com.webex.summarizer.cli;

import com.webex.summarizer.api.WebExMessageService;
import com.webex.summarizer.api.WebExRoomService;
import com.webex.summarizer.auth.WebExAuthenticator;
import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.storage.ConversationStorage;
import com.webex.summarizer.summarizer.LlmSummarizer;
import com.webex.summarizer.util.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

@Command(name = "summarize", description = "Generate summaries for WebEx conversations", mixinStandardHelpOptions = true)
public class SummaryCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(SummaryCommand.class);

    @Option(names = {"-c", "--config"}, description = "Path to config file")
    private String configPath = "config.properties";

    @Option(names = {"-o", "--output-dir"}, description = "Output directory for downloaded conversations")
    private String outputDir;

    @Option(names = {"--token"}, description = "WebEx API token to use")
    private String token;

    @Option(names = {"--room"}, description = "WebEx room ID to download and summarize")
    private String roomId;

    @Option(names = {"--file"}, description = "Path to a saved conversation file to summarize")
    private String filePath;

    @Option(names = {"-p", "--aws-profile"}, description = "AWS profile to use")
    private String awsProfile;
    
    @Option(names = {"--region"}, description = "AWS region to use")
    private String awsRegion;
    
    @Option(names = {"-m", "--model"}, description = "AWS Bedrock model ID to use")
    private String modelId;
    
    @Option(names = {"--list-summaries"}, description = "List all conversations with summaries")
    private boolean listSummaries = false;

    @Override
    public Integer call() throws Exception {
        try {
            ConfigLoader configLoader = new ConfigLoader(configPath);
            
            // Set output directory
            if (outputDir == null) {
                outputDir = configLoader.getProperty("storage.directory", "conversations");
            }
            
            ConversationStorage storage = new ConversationStorage(outputDir);

            if (listSummaries) {
                listSummariesAction(storage);
                return 0;
            }
            
            Conversation conversation = null;
            
            // Initialize authenticator if needed for room operations
            WebExAuthenticator authenticator = null;
            if (roomId != null) {
                // Get token from command line or config file
                if (token == null) {
                    token = configLoader.getProperty("webex.token");
                    if (token == null || token.isEmpty()) {
                        System.err.println("No WebEx token found. Please provide a token with --token or set webex.token in config.properties.");
                        return 1;
                    }
                }
                
                authenticator = new WebExAuthenticator(token);
            }
            
            // Initialize summarizer
            LlmSummarizer summarizer = initializeSummarizer(configLoader);
            
            // Load or download conversation
            if (filePath != null) {
                conversation = loadConversation(filePath, storage);
            } else if (roomId != null && authenticator != null) {
                conversation = downloadConversation(roomId, authenticator, storage);
            } else {
                System.err.println("Please specify either a room ID (--room) or a file path (--file).");
                return 1;
            }
            
            if (conversation == null) {
                return 1; // Error occurred in loading or downloading
            }
            
            // Generate and save summary
            return generateSummary(conversation, summarizer, storage);
            
        } catch (Exception e) {
            logger.error("Failed to generate summary: {}", e.getMessage(), e);
            System.err.println("Failed to generate summary: " + e.getMessage());
            return 1;
        }
    }
    
    private LlmSummarizer initializeSummarizer(ConfigLoader configLoader) {
        // Use AWS profile from command line or config file
        String profile = awsProfile != null ? awsProfile : configLoader.getProperty("aws.profile", "default");
        
        // Use AWS region from command line or config file
        String region = awsRegion != null ? awsRegion : configLoader.getProperty("aws.region", "us-east-1");
        
        // Use model ID from command line or config file
        String model = modelId != null ? modelId : configLoader.getProperty("aws.bedrock.model", "anthropic.claude-v2");
        
        LlmSummarizer summarizer = new LlmSummarizer(profile, region, model);
        logger.info("LLM Summarizer initialized with AWS Bedrock (profile: {}, region: {}, model: {})",
                    profile, region, model);
        
        return summarizer;
    }
    
    private Conversation loadConversation(String filePath, ConversationStorage storage) throws IOException {
        if (!Paths.get(filePath).toFile().exists()) {
            System.err.println("File not found: " + filePath);
            return null;
        }
        
        System.out.println("Loading conversation from " + filePath);
        return storage.loadConversation(filePath);
    }
    
    private Conversation downloadConversation(String roomId, WebExAuthenticator authenticator, ConversationStorage storage) throws IOException {
        // Initialize API services
        WebExRoomService roomService = new WebExRoomService(authenticator);
        WebExMessageService messageService = new WebExMessageService(authenticator, roomService);
        
        System.out.println("Downloading conversation from room " + roomId + "...");
        Conversation conversation = messageService.downloadConversation(roomId);
        
        // Save the conversation to file
        storage.saveConversation(conversation);
        System.out.println("Conversation downloaded successfully with " + 
                conversation.getMessages().size() + " messages and saved to file.");
        
        return conversation;
    }
    
    private int generateSummary(Conversation conversation, LlmSummarizer summarizer, ConversationStorage storage) throws IOException {
        // Display basic conversation info
        System.out.println("\nRoom: " + conversation.getRoom().getTitle());
        System.out.println("Messages: " + conversation.getMessages().size());
        
        // Set up progress reporting
        summarizer.setProgressListener(new LlmSummarizer.SummarizationProgressListener() {
            @Override
            public void onSummarizationProgress(int currentChunk, int totalChunks, String status) {
                // Create a progress bar for visual feedback
                int progressBarWidth = 30;
                int progress = (int)((double)currentChunk / totalChunks * progressBarWidth);
                
                StringBuilder progressBar = new StringBuilder("[");
                for (int i = 0; i < progressBarWidth; i++) {
                    if (i < progress) {
                        progressBar.append("=");
                    } else if (i == progress) {
                        progressBar.append(">");
                    } else {
                        progressBar.append(" ");
                    }
                }
                progressBar.append("] ");
                progressBar.append(currentChunk).append("/").append(totalChunks);
                
                // Clear line and print progress
                System.out.print("\r" + progressBar + " " + status + "                       ");
                System.out.flush();
            }
        });
        
        System.out.println("\nGenerating summary...");
        String summary = summarizer.generateSummary(conversation);
        
        // Move to next line after progress reporting
        System.out.println("\n");
        
        // Save the summary
        storage.saveSummary(conversation, summary);
        
        // Display the formatted summary using our helper method
        displayFormattedSummary(summary);
        
        return 0;
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
    
    private void listSummariesAction(ConversationStorage storage) throws IOException {
        File[] files = storage.listConversationFiles();
        
        if (files.length == 0) {
            System.out.println("No conversation files found in " + outputDir);
            return;
        }
        
        System.out.println("Conversations with summaries:");
        System.out.println("─────────────────────────────────────────────────────────────────────────────");
        System.out.printf("%-40s | %-20s | %-10s\n", "Filename", "Room Title", "Has Summary");
        System.out.println("─────────────────────────────────────────────────────────────────────────────");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        int count = 0;
        for (File file : files) {
            try {
                Conversation conversation = storage.loadConversation(file.getAbsolutePath());
                boolean hasSummary = conversation.getSummary() != null && !conversation.getSummary().isEmpty();
                
                System.out.printf("%-40s | %-20s | %-10s\n",
                        truncateString(file.getName(), 40),
                        truncateString(conversation.getRoom().getTitle(), 20),
                        hasSummary ? "Yes" : "No");
                
                if (hasSummary) {
                    count++;
                }
            } catch (IOException e) {
                logger.error("Failed to load conversation file: {}", file.getName(), e);
            }
        }
        
        System.out.println("─────────────────────────────────────────────────────────────────────────────");
        System.out.printf("Total: %d conversations with summaries (out of %d files)\n", count, files.length);
        System.out.println("\nTo view a conversation with its summary, use:");
        System.out.println("java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar --read --file <conversation-file>");
    }
    
    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}