package com.webex.summarizer.auth;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class WebExAuthenticator {
    
    private static final Logger logger = LoggerFactory.getLogger(WebExAuthenticator.class);
    private final OAuth20Service oAuthService;
    private OAuth2AccessToken accessToken;
    
    public WebExAuthenticator(String clientId, String clientSecret, String redirectUri) {
        this.oAuthService = new ServiceBuilder(clientId)
                .apiSecret(clientSecret)
                .callback(redirectUri)
                // Use default scopes from developer portal
                .build(new WebExOAuthApi());
    }
    
    public String getAuthorizationUrl() {
        return oAuthService.getAuthorizationUrl();
    }
    
    public void handleCallback(String code) throws IOException, InterruptedException, ExecutionException {
        this.accessToken = oAuthService.getAccessToken(code);
        logger.info("Authentication successful");
    }
    
    public void refreshToken() throws IOException, InterruptedException, ExecutionException {
        if (accessToken != null && accessToken.getRefreshToken() != null) {
            this.accessToken = oAuthService.refreshAccessToken(accessToken.getRefreshToken());
            logger.info("Token refreshed successfully");
        } else {
            logger.error("No refresh token available");
            throw new IllegalStateException("No refresh token available");
        }
    }
    
    public String getAccessToken() {
        if (accessToken == null) {
            throw new IllegalStateException("Not authenticated. Call handleCallback() first.");
        }
        return accessToken.getAccessToken();
    }
    
    public void setAccessToken(String token) {
        this.accessToken = new OAuth2AccessToken(token);
    }
    
    public boolean isAuthenticated() {
        return accessToken != null;
    }
}