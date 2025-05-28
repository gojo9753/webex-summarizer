package com.webex.summarizer.summarizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Test class for BedrockClient.
 * Note: These are mostly unit tests that don't actually call the AWS Bedrock service.
 */
public class BedrockClientTest {

    @Test
    public void testListAvailableModels() {
        // Create a client with dummy credentials
        BedrockClient client = new BedrockClient("default", "us-east-1", "anthropic.claude-v2");
        
        // Get the list of models
        List<Map<String, String>> models = client.listAvailableModels();
        
        // Check that the list has models
        assertFalse(models.isEmpty(), "Model list should not be empty");
        
        // Check that the latest Claude model is included
        boolean foundLatestClaude = models.stream()
                .anyMatch(model -> model.get("id").equals("us.anthropic.claude-sonnet-4-20250514-v1:0"));
        
        assertTrue(foundLatestClaude, "Latest Claude model should be in the list");
    }

    @Test
    public void testModelRecognition() throws Exception {
        // Create a test instance with reflection to access private method
        BedrockClient client = new BedrockClient("default", "us-east-1", "anthropic.claude-v2");
        
        // Use reflection to access private method
        java.lang.reflect.Method method = BedrockClient.class.getDeclaredMethod("isModernClaudeModel", String.class);
        method.setAccessible(true);
        
        // Test Claude 3 models
        assertTrue((Boolean) method.invoke(client, "anthropic.claude-3-sonnet-20240229-v1:0"), 
                   "Claude 3 Sonnet should be recognized as a modern model");
        assertTrue((Boolean) method.invoke(client, "anthropic.claude-3-opus-20240229-v1:0"), 
                   "Claude 3 Opus should be recognized as a modern model");
        assertTrue((Boolean) method.invoke(client, "anthropic.claude-3-haiku-20240307-v1:0"), 
                   "Claude 3 Haiku should be recognized as a modern model");
        
        // Test Claude-sonnet models
        assertTrue((Boolean) method.invoke(client, "us.anthropic.claude-sonnet-4-20250514-v1:0"), 
                   "Claude-sonnet should be recognized as a modern model");
        
        // Test older models
        assertFalse((Boolean) method.invoke(client, "anthropic.claude-v2"), 
                    "Claude v2 should not be recognized as a modern model");
        assertFalse((Boolean) method.invoke(client, "anthropic.claude-v2:1"), 
                    "Claude v2.1 should not be recognized as a modern model");
    }

    @Test
    public void testGetModelDetails() {
        BedrockClient client = new BedrockClient("default", "us-east-1", "anthropic.claude-v2");
        
        String testModelId = "test-model-id";
        String details = client.getModelDetails(testModelId);
        
        // Verify the response contains the model ID
        assertTrue(details.contains(testModelId), 
                  "Model details should contain the specified model ID");
    }
}