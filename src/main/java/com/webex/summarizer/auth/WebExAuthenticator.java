package com.webex.summarizer.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebExAuthenticator {
    
    private static final Logger logger = LoggerFactory.getLogger(WebExAuthenticator.class);
    private String token;
    
    public WebExAuthenticator(String token) {
        this.token = token;
        logger.info("WebEx authenticator initialized with token");
    }
    
    public String getAccessToken() {
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("No token provided. Set a token first.");
        }
        return token;
    }
    
    public void setAccessToken(String token) {
        this.token = token;
        logger.info("Token set successfully");
    }
    
    public boolean isAuthenticated() {
        return token != null && !token.isEmpty();
    }
}