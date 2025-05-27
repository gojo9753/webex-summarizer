package com.webex.summarizer.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.webex.summarizer.auth.WebExAuthenticator;
import com.webex.summarizer.model.Room;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebExRoomService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebExRoomService.class);
    private static final String API_BASE_URL = "https://webexapis.com/v1";
    
    private final WebExAuthenticator authenticator;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public WebExRoomService(WebExAuthenticator authenticator) {
        this.authenticator = authenticator;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    public List<Room> listRooms() throws IOException {
        if (!authenticator.isAuthenticated()) {
            throw new IllegalStateException("Not authenticated");
        }
        
        String url = API_BASE_URL + "/rooms";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + authenticator.getAccessToken())
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            
            String responseBody = response.body().string();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode itemsNode = rootNode.path("items");
            
            List<Room> rooms = new ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                Room room = objectMapper.treeToValue(itemNode, Room.class);
                rooms.add(room);
            }
            
            return rooms;
        }
    }
    
    public Room getRoom(String roomId) throws IOException {
        if (!authenticator.isAuthenticated()) {
            throw new IllegalStateException("Not authenticated");
        }
        
        String url = API_BASE_URL + "/rooms/" + roomId;
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + authenticator.getAccessToken())
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            
            String responseBody = response.body().string();
            return objectMapper.readValue(responseBody, Room.class);
        }
    }
}