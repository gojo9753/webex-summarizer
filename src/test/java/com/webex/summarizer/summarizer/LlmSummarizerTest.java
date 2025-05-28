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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for LlmSummarizer.
 * Uses mocking to avoid actual AWS Bedrock calls.
 */
public class LlmSummarizerTest {

    @Test
    public void testProgressReporting() throws Exception {
        // Create a mock BedrockClient
        BedrockClient mockClient = mock(BedrockClient.class);
        when(mockClient.generateText(anyString())).thenReturn("Mock summary");
        
        // Create a mock progress listener
        LlmSummarizer.SummarizationProgressListener mockListener = mock(LlmSummarizer.SummarizationProgressListener.class);
        
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
        
        // Set up mock response for single summary
        when(mockClient.generateText(anyString())).thenReturn("Test summary");
        
        // Create summarizer with reflection to inject mock client
        LlmSummarizer summarizer = new LlmSummarizer("default", "us-east-1", "anthropic.claude-v2");
        
        // Use reflection to replace the client
        java.lang.reflect.Field clientField = LlmSummarizer.class.getDeclaredField("bedrockClient");
        clientField.setAccessible(true);
        clientField.set(summarizer, mockClient);
        
        // Set the progress listener
        summarizer.setProgressListener(mockListener);
        
        // Call the method under test
        String result = summarizer.generateSummary(conversation);
        
        // Verify the interaction with the progress listener
        verify(mockListener).onSummarizationProgress(1, 1, "Processing full conversation");
        
        // Verify the result
        assertEquals("Test summary", result);
    }
    
    @Test
    public void testGenerateChunkedSummary() throws Exception {
        // Create a mock BedrockClient
        BedrockClient mockClient = mock(BedrockClient.class);
        
        // Create test data - a large conversation with many messages
        Room room = new Room();
        room.setId("test-room-id");
        room.setTitle("Test Room");
        
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < 150; i++) { // More than MAX_MESSAGES_PER_CHUNK
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
        
        // Set up mock response for chunk summaries
        when(mockClient.generateText(anyString())).thenReturn("Chunk summary");
        
        // Create summarizer with reflection to inject mock client
        LlmSummarizer summarizer = new LlmSummarizer("default", "us-east-1", "anthropic.claude-v2");
        
        // Use reflection to replace the client
        java.lang.reflect.Field clientField = LlmSummarizer.class.getDeclaredField("bedrockClient");
        clientField.setAccessible(true);
        clientField.set(summarizer, mockClient);
        
        // Create a mock progress listener
        LlmSummarizer.SummarizationProgressListener mockListener = mock(LlmSummarizer.SummarizationProgressListener.class);
        summarizer.setProgressListener(mockListener);
        
        // Call the method under test
        String result = summarizer.generateSummary(conversation);
        
        // Verify the generateText method was called more than once (for each chunk and final summary)
        verify(mockClient, atLeast(2)).generateText(anyString());
        
        // Verify progress listener was called
        verify(mockListener, atLeast(2)).onSummarizationProgress(anyInt(), anyInt(), anyString());
    }
}