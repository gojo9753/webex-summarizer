package com.webex.summarizer.cli;

import com.webex.summarizer.api.WebExMessageService;
import com.webex.summarizer.api.WebExRoomService;
import com.webex.summarizer.auth.WebExAuthenticator;
import com.webex.summarizer.model.Conversation;
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
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

@Command(name = "webex-summarizer", 
        description = "Download and summarize WebEx conversations",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class WebExSummarizerCli implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(WebExSummarizerCli.class);
    
    @Option(names = {"-c", "--config"}, description = "Path to config file")
    private String configPath = "config.properties";
    
    @Option(names = {"-o", "--output-dir"}, description = "Output directory for downloaded conversations")
    private String outputDir;
    
    @Option(names = {"-a", "--auth"}, description = "Perform WebEx authentication")
    private boolean auth = false;
    
    @Option(names = {"-l", "--list-rooms"}, description = "List available WebEx rooms")
    private boolean listRooms = false;
    
    @Option(names = {"-r", "--room"}, description = "WebEx room ID to download")
    private String roomId;
    
    @Option(names = {"-s", "--summarize"}, description = "Generate summary for the downloaded conversation")
    private boolean summarize = false;
    
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
    
    @Override
    public void run() {
        try {
            // Initialize components
            configLoader = new ConfigLoader(configPath);
            
            if (outputDir == null) {
                outputDir = configLoader.getProperty("storage.directory", "conversations");
            }
            
            storage = new ConversationStorage(outputDir);
            
            if (auth) {
                performAuthentication();
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
            } else {
                System.out.println("No command specified. Use --help for usage information.");
            }
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    private void initApiClients() throws IOException {
        String clientId = configLoader.getProperty("webex.client.id");
        String clientSecret = configLoader.getProperty("webex.client.secret");
        String redirectUri = configLoader.getProperty("webex.redirect.uri");
        
        authenticator = new WebExAuthenticator(clientId, clientSecret, redirectUri);
        
        // Load access token from config
        String accessToken = configLoader.getProperty("webex.access.token");
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("No access token found. Please authenticate first using --auth.");
        }
        
        // Initialize API services
        roomService = new WebExRoomService(authenticator);
        messageService = new WebExMessageService(authenticator, roomService);
        
        // Initialize summarizer if needed
        if (summarize) {
            String llmApiEndpoint = configLoader.getProperty("llm.api.endpoint");
            String llmApiKey = configLoader.getProperty("llm.api.key");
            summarizer = new LlmSummarizer(llmApiEndpoint, llmApiKey);
        }
    }
    
    private void performAuthentication() {
        try {
            String clientId = configLoader.getProperty("webex.client.id");
            String clientSecret = configLoader.getProperty("webex.client.secret");
            String redirectUri = configLoader.getProperty("webex.redirect.uri");
            
            authenticator = new WebExAuthenticator(clientId, clientSecret, redirectUri);
            
            System.out.println("Please open the following URL in your browser:");
            System.out.println(authenticator.getAuthorizationUrl());
            System.out.println("\nAfter authorization, you will be redirected to a page.");
            System.out.println("Please enter the code parameter from the URL:");
            
            Scanner scanner = new Scanner(System.in);
            String code = scanner.nextLine().trim();
            
            authenticator.handleCallback(code);
            
            // Save the token in the config
            configLoader.setProperty("webex.access.token", authenticator.getAccessToken());
            configLoader.saveProperties();
            
            System.out.println("Authentication successful. Token saved.");
        } catch (IOException | InterruptedException | ExecutionException e) {
            logger.error("Authentication error: {}", e.getMessage(), e);
            System.err.println("Authentication error: " + e.getMessage());
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
                
                System.out.println("\nSummary:");
                System.out.println(summary);
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
            
            System.out.println("\nSummary:");
            System.out.println(summary);
        } catch (IOException e) {
            logger.error("Failed to summarize conversation: {}", e.getMessage(), e);
            System.err.println("Failed to summarize conversation: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new WebExSummarizerCli()).execute(args);
        System.exit(exitCode);
    }
}