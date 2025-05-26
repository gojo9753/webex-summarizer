package com.webex.summarizer.auth;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor;

public class WebExOAuthApi extends DefaultApi20 {
    
    private static final String AUTHORIZATION_URL = "https://webexapis.com/v1/authorize";
    private static final String TOKEN_URL = "https://webexapis.com/v1/access_token";
    
    protected WebExOAuthApi() {
    }
    
    private static class InstanceHolder {
        private static final WebExOAuthApi INSTANCE = new WebExOAuthApi();
    }
    
    public static WebExOAuthApi instance() {
        return InstanceHolder.INSTANCE;
    }
    
    @Override
    public String getAccessTokenEndpoint() {
        return TOKEN_URL;
    }
    
    @Override
    protected String getAuthorizationBaseUrl() {
        return AUTHORIZATION_URL;
    }
    
    @Override
    public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
        return OpenIdJsonTokenExtractor.instance();
    }
}