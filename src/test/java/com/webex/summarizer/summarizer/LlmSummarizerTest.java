package com.webex.summarizer.summarizer;

import com.webex.summarizer.model.Conversation;
import com.webex.summarizer.model.Message;
import com.webex.summarizer.model.Room;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for LlmSummarizer.
 * Uses mocking to avoid actual AWS Bedrock calls.
 */
public class LlmSummarizerTest {

    @Test
    public void testProgressReporting() throws Exception {
        // Create test data - a conversation with messages
        Room room = new Room();
        room.setId("test-room-id");
        room.setTitle("Test Room");
        
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Message message = new Message();
            message.setId("msg-" + i);
            message.setPersonEmail("user" + i + "@example.com");
            message.setText("Test message " + i);
            message.setCreated(ZonedDateTime.now().minusMinutes(i));
            messages.add(message);
        }
        
        Conversation conversation = new Conversation();
        conversation.setRoom(room);
        conversation.setMessages(messages);
        
        // Create a test implementation of progress listener that records calls
        List<String> progressUpdates = new ArrayList<>();
        LlmSummarizer.SummarizationProgressListener testListener = 
            (current, total, status) -> progressUpdates.add(current + "/" + total + ": " + status);
        
        // Create a test implementation of BedrockClient
        BedrockClient testClient = new TestBedrockClient("Test summary");
        
        // Create summarizer with reflection to inject test client
        LlmSummarizer summarizer = new LlmSummarizer("default", "us-east-1", "anthropic.claude-v2");
        
        // Use reflection to replace the client
        java.lang.reflect.Field clientField = LlmSummarizer.class.getDeclaredField("bedrockClient");
        clientField.setAccessible(true);
        clientField.set(summarizer, testClient);
        
        // Set the progress listener
        summarizer.setProgressListener(testListener);
        
        // Call the method under test
        String result = summarizer.generateSummary(conversation);
        
        // Verify the progress listener was called
        assertTrue(progressUpdates.contains("1/1: Processing full conversation"), 
                  "Progress listener should be called with correct parameters");
        
        // Verify the result
        assertEquals("Test summary", result);
    }
    
    // Simple test implementation that avoids Mockito
    private static class TestBedrockClient extends BedrockClient {
        private final String responseText;
        
        public TestBedrockClient(String responseText) {
            super("default", "us-east-1", "anthropic.claude-v2");
            this.responseText = responseText;
        }
        
        @Override
        public String generateText(String prompt) {
            return responseText;
        }
    }
    
    @Test
    public void testGenerateChunkedSummary() throws Exception {
        // Create test data - a very large conversation with huge messages to force chunking
        Room room = new Room();
        room.setId("test-room-id");
        room.setTitle("Test Room");
        
        List<Message> messages = new ArrayList<>();
        
        // Create a small number of very large messages to force token-based chunking
        // Each message will be roughly 15,000 tokens (60,000 characters)
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 60000; i++) {
            largeText.append("x");
        }
        
        // Create 5 large messages (each ~15,000 tokens, so total ~75,000 tokens)
        for (int i = 0; i < 5; i++) {
            Message message = new Message();
            message.setId("msg-" + i);
            message.setPersonEmail("user" + i + "@example.com");
            message.setText(largeText.toString());
            message.setCreated(ZonedDateTime.now().minusMinutes(i));
            messages.add(message);
        }
        
        Conversation conversation = new Conversation();
        conversation.setRoom(room);
        conversation.setMessages(messages);
        
        // Track calls to BedrockClient.generateText
        List<String> generatedTexts = new ArrayList<>();
        
        // Create a test implementation of BedrockClient that records calls
        class TracingBedrockClient extends BedrockClient {
            public TracingBedrockClient() {
                super("default", "us-east-1", "anthropic.claude-v2");
            }
            
            @Override
            public String generateText(String prompt) {
                generatedTexts.add(prompt.substring(0, Math.min(50, prompt.length())));
                return "Chunk summary";
            }
        }
        
        TracingBedrockClient testClient = new TracingBedrockClient();
        
        // Record progress updates
        List<String> progressUpdates = new ArrayList<>();
        LlmSummarizer.SummarizationProgressListener testListener = 
            (current, total, status) -> progressUpdates.add(current + "/" + total + ": " + status);
        
        // Instead of modifying constants, let's create a test implementation with a custom
        // calculateChunks method that always returns at least 2 chunks
        
        // Create a custom subclass of LlmSummarizer for testing
        class TestSummarizer extends LlmSummarizer {
            public TestSummarizer() {
                super("default", "us-east-1", "anthropic.claude-v2");
            }
            
            // Override calculateChunkCount to always return at least 2 chunks
            @Override
            protected int calculateChunkCount(List<Message> messages) {
                return Math.max(2, super.calculateChunkCount(messages));
            }
        }
        
        TestSummarizer summarizer = new TestSummarizer();
        
        // Inject test client
        java.lang.reflect.Field clientField = LlmSummarizer.class.getDeclaredField("bedrockClient");
        clientField.setAccessible(true);
        clientField.set(summarizer, testClient);
        
        // Set the progress listener
        summarizer.setProgressListener(testListener);
        
        // Call the method under test
        String result = summarizer.generateSummary(conversation);
        
        // Verify the generateText method was called more than once (for each chunk and final summary)
        assertTrue(generatedTexts.size() >= 2, 
                 "Should have generated at least 2 texts (chunks + final)");
        
        // Verify progress listener was called multiple times
        assertTrue(progressUpdates.size() >= 2,
                 "Should have reported progress at least twice (for each chunk)");
        
        // Verify the result
        assertEquals("Chunk summary", result);
    }
}