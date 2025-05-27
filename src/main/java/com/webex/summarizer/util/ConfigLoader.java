package com.webex.summarizer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    
    private final String configPath;
    private final Properties properties;
    
    public ConfigLoader(String configPath) {
        this.configPath = configPath;
        this.properties = new Properties();
        loadProperties();
    }
    
    private void loadProperties() {
        File configFile = new File(configPath);
        
        if (!configFile.exists()) {
            logger.info("Config file does not exist. Creating default config at: {}", configPath);
            createDefaultConfig();
            return;
        }
        
        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);
            logger.info("Config loaded from: {}", configPath);
        } catch (IOException e) {
            logger.error("Failed to load config: {}", e.getMessage(), e);
        }
    }
    
    private void createDefaultConfig() {
        // WebEx config (only token needed with new simplified auth)
        properties.setProperty("webex.token", "YOUR_WEBEX_TOKEN");
        properties.setProperty("storage.directory", "conversations");
        
        // AWS Bedrock config
        properties.setProperty("aws.profile", "default");
        properties.setProperty("aws.region", "us-east-1");
        properties.setProperty("aws.bedrock.model", "anthropic.claude-v2");
        
        saveProperties();
    }
    
    public void saveProperties() {
        try (FileOutputStream fos = new FileOutputStream(configPath)) {
            properties.store(fos, "WebEx Summarizer Configuration");
            logger.info("Config saved to: {}", configPath);
        } catch (IOException e) {
            logger.error("Failed to save config: {}", e.getMessage(), e);
        }
    }
    
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
}