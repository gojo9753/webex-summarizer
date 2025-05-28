package com.webex.summarizer.cli;

import com.webex.summarizer.api.WebExRoomService;
import com.webex.summarizer.auth.WebExAuthenticator;
import com.webex.summarizer.model.Room;
import com.webex.summarizer.util.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list-rooms", description = "List available WebEx rooms", mixinStandardHelpOptions = true)
public class RoomListCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(RoomListCommand.class);

    @Option(names = {"-c", "--config"}, description = "Path to config file")
    private String configPath = "config.properties";

    @Option(names = {"--token"}, description = "WebEx API token to use")
    private String token;

    @Option(names = {"--id"}, description = "Show detailed information about a specific room by ID")
    private String roomId;

    @Option(names = {"--type"}, description = "Filter rooms by type (direct, group)")
    private String roomType;

    @Override
    public Integer call() throws Exception {
        try {
            ConfigLoader configLoader = new ConfigLoader(configPath);
            
            // Use token from command line or config file
            if (token == null) {
                token = configLoader.getProperty("webex.token");
                if (token == null || token.isEmpty()) {
                    System.err.println("No WebEx token found. Please provide a token with --token or set webex.token in config.properties.");
                    return 1;
                }
            }
            
            // Initialize authenticator with token
            WebExAuthenticator authenticator = new WebExAuthenticator(token);
            
            // Initialize room service
            WebExRoomService roomService = new WebExRoomService(authenticator);
            
            if (roomId != null) {
                // Show details for a specific room
                try {
                    Room room = roomService.getRoom(roomId);
                    System.out.println("Room Details:");
                    System.out.println("─────────────────────────────────────────────────────────");
                    System.out.printf("ID:          %s\n", room.getId());
                    System.out.printf("Title:       %s\n", room.getTitle());
                    System.out.printf("Type:        %s\n", room.getType());
                    System.out.printf("Created:     %s\n", room.getCreated());
                    System.out.printf("Is Locked:   %s\n", room.getIsLocked() != null ? room.getIsLocked() : "Unknown");
                    System.out.println("─────────────────────────────────────────────────────────");
                } catch (IOException e) {
                    System.err.println("Failed to get room: " + e.getMessage());
                    return 1;
                }
            } else {
                // List all available rooms
                List<Room> rooms = roomService.listRooms();
                
                // Apply filter if specified
                if (roomType != null) {
                    rooms.removeIf(room -> !room.getType().equalsIgnoreCase(roomType));
                }
                
                System.out.println("Available WebEx Rooms:");
                System.out.println("─────────────────────────────────────────────────────────");
                System.out.printf("%-36s | %-30s | %-10s\n", "Room ID", "Title", "Type");
                System.out.println("─────────────────────────────────────────────────────────");
                
                for (Room room : rooms) {
                    System.out.printf("%-36s | %-30s | %-10s\n",
                            room.getId(),
                            truncateString(room.getTitle(), 30),
                            room.getType());
                }
                
                System.out.println("─────────────────────────────────────────────────────────");
                System.out.printf("Total: %d rooms\n", rooms.size());
                System.out.println("\nTo view messages from a specific room, use:");
                System.out.println("java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-messages --room <room-id>");
            }
            
            return 0;
        } catch (Exception e) {
            logger.error("Failed to list rooms: {}", e.getMessage(), e);
            System.err.println("Failed to list rooms: " + e.getMessage());
            return 1;
        }
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