package com.webex.summarizer.cli;

import com.webex.summarizer.api.WebExMessageService;
import com.webex.summarizer.api.WebExRoomService;
import com.webex.summarizer.auth.WebExAuthenticator;
import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import com.webex.summarizer.storage.ConversationStorage;
import com.webex.summarizer.summarizer.LlmSummarizer;
import com.webex.summarizer.util.ConfigLoader;
import com.webex.summarizer.util.SummaryFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

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
    
    @Option(names = {"--start-date", "--from"}, description = "Start date for filtering messages (format: yyyy-MM-dd), e.g., 2025-05-25. If only this is specified, messages from this date to current date will be included.")
    private String startDateStr;
    
    @Option(names = {"--end-date", "--to"}, description = "End date for filtering messages (format: yyyy-MM-dd). Optional if start-date is specified.")
    private String endDateStr;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
            
            // We'll check for empty conversations after date filtering in the generateSummary method
            
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
        // Filter messages by date if date parameters are provided
        if (startDateStr != null || endDateStr != null) {
            filterMessagesByDate(conversation);
        }
        
        // Display basic conversation info
        System.out.println("\nRoom: " + conversation.getRoom().getTitle());
        System.out.println("Messages: " + conversation.getMessages().size());
        
        // Display date range if filtered
        if (conversation.getDateFrom() != null) {
            System.out.println("Date from: " + conversation.getDateFrom().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        if (conversation.getDateTo() != null) {
            // Only display "to date" if it's not today (when user only specified start date)
            LocalDate today = LocalDate.now();
            LocalDate dateTo = conversation.getDateTo().minusDays(1).toLocalDate();
            if (!dateTo.equals(today)) {
                System.out.println("Date to: " + dateTo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }
        }
        
        // Check if there are any messages to summarize after potential date filtering
        if (conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
            System.out.println("\nWarning: No messages found to summarize.");
            
            // Construct appropriate message based on whether date filtering was applied
            String noMessagesMessage;
            if (conversation.getDateFrom() != null || conversation.getDateTo() != null) {
                noMessagesMessage = "No messages were found in the date range specified. " +
                    "This could be because no messages exist within the date range you selected, " +
                    "or because the room \"" + conversation.getRoom().getTitle() + "\" is empty.";
            } else {
                noMessagesMessage = "This appears to be an empty chat or group. " +
                    "The room \"" + conversation.getRoom().getTitle() + "\" exists but contains no messages.";
            }
            
            // Save the empty summary
            storage.saveSummary(conversation, noMessagesMessage);
            
            // Display a simple formatted summary
            SummaryFormatter.printFormattedSummary(noMessagesMessage);
            
            return 0;
        }
        
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
        SummaryFormatter.printFormattedSummary(summary);
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
    
    /**
     * Filter messages in the conversation based on start and end dates
     * Note: WebEx API returns timestamps as epoch seconds which are converted to
     * ZonedDateTime objects by the Jackson JavaTimeModule during deserialization.
     * 
     * @param conversation The conversation to filter
     */
    private void filterMessagesByDate(Conversation conversation) {
        LocalDate startDate = null;
        LocalDate endDate = null;
        
        // Parse start date
        if (startDateStr != null && !startDateStr.isEmpty()) {
            try {
                // Check if date is in correct format
                if (startDateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    startDate = LocalDate.parse(startDateStr, DATE_FORMATTER);
                } else if (startDateStr.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                    // Try to parse dates like 2025-5-28 or 2025-05-5 by reformatting
                    String[] parts = startDateStr.split("-");
                    String year = parts[0];
                    String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                    String day = parts[2].length() == 1 ? "0" + parts[2] : parts[2];
                    startDate = LocalDate.parse(year + "-" + month + "-" + day, DATE_FORMATTER);
                } else {
                    throw new DateTimeParseException("Format error", startDateStr, 0);
                }
                
                conversation.setDateFrom(startDate.atStartOfDay(ZonedDateTime.now().getZone()));
                System.out.println("Filtering messages from " + startDate.format(DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                System.err.println("Invalid start date format. Please use yyyy-MM-dd (example: 2025-05-28). Using no start date filter.");
            }
        }
        
        // Parse end date
        // If end date is provided, parse it. Otherwise, use current date if start date is specified
        if (endDateStr != null && !endDateStr.isEmpty()) {
            try {
                // Check if date is in correct format
                if (endDateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);
                } else if (endDateStr.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                    // Try to parse dates like 2025-5-28 or 2025-05-5 by reformatting
                    String[] parts = endDateStr.split("-");
                    String year = parts[0];
                    String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                    String day = parts[2].length() == 1 ? "0" + parts[2] : parts[2];
                    endDate = LocalDate.parse(year + "-" + month + "-" + day, DATE_FORMATTER);
                } else {
                    throw new DateTimeParseException("Format error", endDateStr, 0);
                }
                
                conversation.setDateTo(endDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone()));
                System.out.println("Filtering messages until " + endDate.format(DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                System.err.println("Invalid end date format. Please use yyyy-MM-dd (example: 2025-05-28). Using no end date filter.");
            }
        } else if (startDate != null) {
            // If only start date is specified, use current date as end date
            endDate = LocalDate.now();
            conversation.setDateTo(endDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone()));
            System.out.println("Filtering messages until current date");
        }
        
        // If no valid dates, return without filtering
        if (startDate == null && endDate == null) {
            return;
        }
        
        final LocalDate finalStartDate = startDate;
        final LocalDate finalEndDate = endDate;
        
        // Save original message count for reporting
        int originalCount = conversation.getMessages().size();
        
        // Filter messages by date
        List<Message> filteredMessages = conversation.getMessages().stream()
            .filter(message -> {
                ZonedDateTime created = message.getCreated();
                LocalDate messageDate = created.toLocalDate();
                
                boolean afterStartDate = finalStartDate == null || !messageDate.isBefore(finalStartDate);
                boolean beforeEndDate = finalEndDate == null || !messageDate.isAfter(finalEndDate);
                
                return afterStartDate && beforeEndDate;
            })
            .collect(Collectors.toList());
        
        // Update the conversation with filtered messages
        conversation.setMessages(filteredMessages);
        
        // Report filtering results
        System.out.println("Filtered messages by date: " + filteredMessages.size() + 
                " (out of " + originalCount + " messages)");
                
        // Check if we filtered out all messages
        if (filteredMessages.isEmpty() && originalCount > 0) {
            System.out.println("\nWarning: All messages were filtered out based on your date criteria.");
            System.out.println("No messages exist in this conversation within the date range you specified.");
        }
    }
}