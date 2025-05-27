package com.webex.summarizer.summarizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

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
                
        this.runtimeClient = BedrockRuntimeClient.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
                
        logger.info("AWS Bedrock runtime client initialized with profile: {} and region: {}", awsProfile, awsRegion);
    }
    
    public List<Map<String, String>> listAvailableModels() {
        // Since we can't use ListFoundationModels API, we'll return a hardcoded list of common models
        List<Map<String, String>> models = new ArrayList<>();
        
        Map<String, String> claude2 = new HashMap<>();
        claude2.put("id", "anthropic.claude-v2");
        claude2.put("name", "Claude v2");
        claude2.put("provider", "Anthropic");
        models.add(claude2);
        
        Map<String, String> claude2_1 = new HashMap<>();
        claude2_1.put("id", "anthropic.claude-v2:1");
        claude2_1.put("name", "Claude v2.1");
        claude2_1.put("provider", "Anthropic");
        models.add(claude2_1);
        
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