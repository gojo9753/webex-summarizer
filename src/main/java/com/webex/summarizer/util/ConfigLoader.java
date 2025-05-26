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
        properties.setProperty("webex.client.id", "YOUR_CLIENT_ID");
        properties.setProperty("webex.client.secret", "YOUR_CLIENT_SECRET");
        properties.setProperty("webex.redirect.uri", "http://localhost:8080/callback");
        properties.setProperty("storage.directory", "conversations");
        properties.setProperty("llm.api.endpoint", "https://api.openai.com/v1/completions");
        properties.setProperty("llm.api.key", "YOUR_LLM_API_KEY");
        
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