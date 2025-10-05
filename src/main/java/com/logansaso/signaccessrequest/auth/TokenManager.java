package com.logansaso.signaccessrequest.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.logansaso.signaccessrequest.SignAccessRequestPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class TokenManager {
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String tokenEndpoint;
    private final SignAccessRequestPlugin plugin;
    private final Gson gson;

    private String accessToken;
    private long tokenExpiresAt;

    public TokenManager(String baseUrl, String clientId, String clientSecret, String tokenEndpoint, SignAccessRequestPlugin plugin) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenEndpoint = tokenEndpoint;
        this.plugin = plugin;
        this.gson = new Gson();
        this.accessToken = null;
        this.tokenExpiresAt = 0;
    }

    /**
     * Gets a valid access token, refreshing if necessary
     */
    public CompletableFuture<String> getAccessToken() {
        // Check if we have a valid cached token
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return CompletableFuture.completedFuture(accessToken);
        }

        // Need to fetch a new token
        return fetchNewToken();
    }

    private CompletableFuture<String> fetchNewToken() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String tokenUrl = baseUrl + "/" + tokenEndpoint;
                URL url = new URL(tokenUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                // Basic auth with client credentials
                String auth = clientId + ":" + clientSecret;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

                conn.setDoOutput(true);

                // Send request body
                String requestBody = "grant_type=client_credentials";

                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Token Request:");
                    plugin.getLogger().info("[DEBUG]   URL: " + tokenUrl);
                    plugin.getLogger().info("[DEBUG]   Method: POST");
                    plugin.getLogger().info("[DEBUG]   Body: " + requestBody);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Token Response Code: " + responseCode);
                }

                if (responseCode != 200) {
                    String errorMsg = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (plugin.isDebugMode()) {
                        plugin.getLogger().warning("[DEBUG] Token Error Response: " + errorMsg);
                    }
                    throw new IOException("Failed to get access token. HTTP " + responseCode + ": " + errorMsg);
                }

                // Read response
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                if (plugin.isDebugMode()) {
                    // Mask the token in debug output for security
                    String maskedResponse = response.replaceAll("\"access_token\"\\s*:\\s*\"[^\"]+\"",
                                                                "\"access_token\":\"***MASKED***\"");
                    plugin.getLogger().info("[DEBUG] Token Response: " + maskedResponse);
                }

                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

                accessToken = jsonResponse.get("access_token").getAsString();
                int expiresIn = jsonResponse.get("expires_in").getAsInt();

                // Set expiry with 5 minute buffer
                tokenExpiresAt = System.currentTimeMillis() + ((expiresIn - 300) * 1000L);

                plugin.getLogger().info("Successfully obtained ConductorOne access token (expires in " + expiresIn + "s)");
                return accessToken;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to fetch access token: " + e.getMessage());
                throw new RuntimeException("Failed to fetch access token", e);
            }
        });
    }

    /**
     * Clears the cached token, forcing a refresh on next request
     */
    public void invalidateToken() {
        this.accessToken = null;
        this.tokenExpiresAt = 0;
    }
}
