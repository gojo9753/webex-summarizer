package com.webex.summarizer.summarizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BedrockClient {

    private static final Logger logger = LoggerFactory.getLogger(BedrockClient.class);
    private final String awsProfile;
    private final Region awsRegion;
    private final String modelId;
    private final ObjectMapper objectMapper;
    
    private BedrockRuntimeClient runtimeClient;

    public BedrockClient(String awsProfile, String awsRegion, String modelId) {
        this.awsProfile = awsProfile != null ? awsProfile : "default";
        this.awsRegion = Region.of(awsRegion != null ? awsRegion : "us-east-1");
        this.modelId = modelId;
        this.objectMapper = new ObjectMapper();
        
        initializeClients();
    }
    
    private void initializeClients() {
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.builder()
                .profileName(awsProfile)
                .build();
        
        // Configure client with extended timeouts for working with large conversations
        this.runtimeClient = BedrockRuntimeClient.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .httpClient(ApacheHttpClient.builder()
                    .socketTimeout(java.time.Duration.ofMinutes(10))
                    .connectionTimeout(java.time.Duration.ofMinutes(5))
                    .connectionAcquisitionTimeout(java.time.Duration.ofMinutes(5))
                    .build())
                .overrideConfiguration(config -> 
                    config.apiCallTimeout(java.time.Duration.ofMinutes(15))
                         .apiCallAttemptTimeout(java.time.Duration.ofMinutes(10)))
                .build();
                
        logger.info("AWS Bedrock runtime client initialized with profile: {} and region: {} (socket timeout: 10 minutes, API timeout: 15 minutes)",
                   awsProfile, awsRegion);
    }
    
    public List<Map<String, String>> listAvailableModels() {
        // Since we can't use ListFoundationModels API, we'll return a hardcoded list of common models
        List<Map<String, String>> models = new ArrayList<>();
        
        // Latest Claude models
        Map<String, String> claudeSonnet4 = new HashMap<>();
        claudeSonnet4.put("id", "us.anthropic.claude-sonnet-4-20250514-v1:0");
        claudeSonnet4.put("name", "Claude Sonnet 4 (Latest)");
        claudeSonnet4.put("provider", "Anthropic");
        models.add(claudeSonnet4);
        
        Map<String, String> claudeSonnet3 = new HashMap<>();
        claudeSonnet3.put("id", "anthropic.claude-3-sonnet-20240229-v1:0");
        claudeSonnet3.put("name", "Claude 3 Sonnet");
        claudeSonnet3.put("provider", "Anthropic");
        models.add(claudeSonnet3);
        
        Map<String, String> claudeHaiku = new HashMap<>();
        claudeHaiku.put("id", "anthropic.claude-3-haiku-20240307-v1:0");
        claudeHaiku.put("name", "Claude 3 Haiku");
        claudeHaiku.put("provider", "Anthropic");
        models.add(claudeHaiku);
        
        Map<String, String> claudeOpus = new HashMap<>();
        claudeOpus.put("id", "anthropic.claude-3-opus-20240229-v1:0");
        claudeOpus.put("name", "Claude 3 Opus");
        claudeOpus.put("provider", "Anthropic");
        models.add(claudeOpus);
        
        // Legacy Claude models
        Map<String, String> claude2 = new HashMap<>();
        claude2.put("id", "anthropic.claude-v2");
        claude2.put("name", "Claude v2 (Legacy)");
        claude2.put("provider", "Anthropic");
        models.add(claude2);
        
        Map<String, String> claude2_1 = new HashMap<>();
        claude2_1.put("id", "anthropic.claude-v2:1");
        claude2_1.put("name", "Claude v2.1 (Legacy)");
        claude2_1.put("provider", "Anthropic");
        models.add(claude2_1);
        
        // Other Bedrock models
        Map<String, String> titan = new HashMap<>();
        titan.put("id", "amazon.titan-text-express-v1");
        titan.put("name", "Titan Text Express");
        titan.put("provider", "Amazon");
        models.add(titan);
        
        Map<String, String> llama = new HashMap<>();
        llama.put("id", "meta.llama2-13b-chat-v1");
        llama.put("name", "Llama 2 13B Chat");
        llama.put("provider", "Meta");
        models.add(llama);
        
        return models;
    }
    
    public String getModelDetails(String modelId) {
        // Since we can't use GetFoundationModel API, we'll return a basic message
        return String.format(
            "Model ID: %s\n" +
            "Note: Detailed model information not available in this implementation.\n" +
            "Please check AWS console or documentation for more details.",
            modelId
        );
    }
    
    public String generateText(String prompt) throws IOException {
        if (isModernClaudeModel(modelId)) {
            // Use Messages API for newer Claude models (Claude 3 and later)
            return generateTextWithMessagesAPI(prompt);
        } else {
            // Use legacy approach for older models
            return generateTextWithLegacyAPI(prompt);
        }
    }
    
    /**
     * Determines if the model is a modern Claude model (Claude 3+ or newer us.anthropic format)
     * that supports the Messages API.
     */
    private boolean isModernClaudeModel(String modelId) {
        return modelId.contains("claude-3") || 
               modelId.contains("claude-sonnet") || 
               modelId.contains("claude-opus") || 
               modelId.contains("claude-haiku") || 
               modelId.startsWith("us.anthropic");
    }
    
    /**
     * Generates text using the modern Messages API (for Claude 3 and newer models)
     */
    private String generateTextWithMessagesAPI(String prompt) throws IOException {
        logger.info("Using Messages API with model: {}", modelId);
        
        // Create the request body directly as JSON
        ObjectNode requestBody = objectMapper.createObjectNode();
        
        // Create messages array
        ArrayNode messagesArray = objectMapper.createArrayNode();
        
        // Create user message
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        
        // Create content for user message
        ObjectNode content = objectMapper.createObjectNode();
        content.put("type", "text");
        content.put("text", prompt);
        
        // Add content to user message
        ArrayNode contentArray = objectMapper.createArrayNode();
        contentArray.add(content);
        userMessage.set("content", contentArray);
        
        // Add user message to messages array
        messagesArray.add(userMessage);
        
        // Set parameters for the Messages API
        requestBody.put("anthropic_version", "bedrock-2023-05-31");
        requestBody.set("messages", messagesArray);
        requestBody.put("system", "You are a helpful assistant that summarizes WebEx conversations accurately and concisely.");
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.7);
        requestBody.put("top_p", 0.9);
        
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        logger.debug("Request body: {}", requestBodyJson);
        
        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestBodyJson))
                .build();
                
        InvokeModelResponse response = runtimeClient.invokeModel(request);
        String responseBody = response.body().asUtf8String();
        
        // Parse the response for modern Claude models
        JsonNode responseJson = objectMapper.readTree(responseBody);
        
        JsonNode contentNode = responseJson.path("content");
        if (contentNode.isArray() && contentNode.size() > 0) {
            StringBuilder responseText = new StringBuilder();
            
            for (JsonNode contentItem : contentNode) {
                if (contentItem.has("text")) {
                    responseText.append(contentItem.get("text").asText());
                }
            }
            
            return responseText.toString();
        } else {
            throw new IOException("Unexpected response format from Messages API");
        }
    }
    
    /**
     * Generates text using the legacy API (for older models)
     */
    private String generateTextWithLegacyAPI(String prompt) throws IOException {
        logger.info("Using legacy API with model: {}", modelId);
        
        // Different models have different input formats
        String requestBody;
        
        if (modelId.contains("anthropic.claude")) {
            requestBody = formatClaudeRequest(prompt);
        } else if (modelId.contains("amazon.titan")) {
            requestBody = formatTitanRequest(prompt);
        } else if (modelId.contains("meta.llama2")) {
            requestBody = formatLlamaRequest(prompt);
        } else {
            throw new IllegalArgumentException("Unsupported model ID: " + modelId);
        }
        
        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();
                
        InvokeModelResponse response = runtimeClient.invokeModel(request);
        String responseBody = response.body().asUtf8String();
        
        // Parse the response based on the model
        return parseModelResponse(responseBody);
    }
    
    private String formatClaudeRequest(String prompt) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("prompt", "\n\nHuman: " + prompt + "\n\nAssistant:");
        requestBody.put("max_tokens_to_sample", 4000);
        requestBody.put("temperature", 0.7);
        requestBody.put("top_p", 0.9);
        requestBody.put("stop_sequences", objectMapper.createArrayNode().add("\n\nHuman:"));
        
        return objectMapper.writeValueAsString(requestBody);
    }
    
    private String formatTitanRequest(String prompt) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("inputText", prompt);
        requestBody.put("textGenerationConfig", 
            objectMapper.createObjectNode()
                .put("maxTokenCount", 4000)
                .put("temperature", 0.7)
                .put("topP", 0.9));
                
        return objectMapper.writeValueAsString(requestBody);
    }
    
    private String formatLlamaRequest(String prompt) throws IOException {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("prompt", prompt);
        requestBody.put("max_gen_len", 4000);
        requestBody.put("temperature", 0.7);
        requestBody.put("top_p", 0.9);
        
        return objectMapper.writeValueAsString(requestBody);
    }
    
    private String parseModelResponse(String responseBody) throws IOException {
        JsonNode responseJson = objectMapper.readTree(responseBody);
        
        if (modelId.contains("anthropic.claude")) {
            return responseJson.path("completion").asText();
        } else if (modelId.contains("amazon.titan")) {
            return responseJson.path("results").get(0).path("outputText").asText();
        } else if (modelId.contains("meta.llama2")) {
            return responseJson.path("generation").asText();
        } else {
            throw new IllegalArgumentException("Unsupported model ID for response parsing: " + modelId);
        }
    }
}