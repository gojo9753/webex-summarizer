package com.webex.summarizer.cli;

import com.webex.summarizer.summarizer.LlmSummarizer;
import com.webex.summarizer.util.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "list-models", description = "List available AWS Bedrock models")
public class ModelListCommand implements Callable<Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ModelListCommand.class);

    @Option(names = {"-c", "--config"}, description = "Path to config file")
    private String configPath = "config.properties";

    @Option(names = {"-p", "--profile"}, description = "AWS profile to use")
    private String awsProfile;
    
    @Option(names = {"--region"}, description = "AWS region to use")
    private String awsRegion;

    @Option(names = {"--detail"}, description = "Show detailed information about a specific model")
    private String modelId;

    @Override
    public Integer call() throws Exception {
        try {
            ConfigLoader configLoader = new ConfigLoader(configPath);
            
            // Use the profile from command line or config file
            if (awsProfile == null) {
                awsProfile = configLoader.getProperty("aws.profile", "default");
            }
            
            // Use the region from command line or config file
            if (awsRegion == null) {
                awsRegion = configLoader.getProperty("aws.region", "us-east-1");
            }
            
            // Placeholder model ID - it's not relevant for listing models
            String placeholderModelId = configLoader.getProperty("aws.bedrock.model", "anthropic.claude-v2");
            
            // Initialize the LLM Summarizer with AWS Bedrock
            LlmSummarizer summarizer = new LlmSummarizer(awsProfile, awsRegion, placeholderModelId);
            
            if (modelId != null) {
                // Show details for a specific model
                String modelDetails = summarizer.getModelDetails(modelId);
                System.out.println("Model Details for " + modelId + ":");
                System.out.println(modelDetails);
            } else {
                // List all available models
                List<Map<String, String>> models = summarizer.listAvailableModels();
                System.out.println("Available AWS Bedrock Models:");
                System.out.println("----------------------------");
                
                for (Map<String, String> model : models) {
                    System.out.printf("ID: %s\nName: %s\nProvider: %s\n\n",
                            model.get("id"), model.get("name"), model.get("provider"));
                }
                
                System.out.println("\nTo use a model, set the model ID in config.properties:");
                System.out.println("aws.bedrock.model=<model-id>");
                System.out.println("\nFor more details about a specific model, use:");
                System.out.println("java -jar target/webex-summarizer-1.0-SNAPSHOT-jar-with-dependencies.jar list-models --detail <model-id>");
            }
            
            return 0;
        } catch (Exception e) {
            logger.error("Failed to list models: {}", e.getMessage(), e);
            System.err.println("Failed to list models: " + e.getMessage());
            return 1;
        }
    }
}