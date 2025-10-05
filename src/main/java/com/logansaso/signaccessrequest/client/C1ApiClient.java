package com.logansaso.signaccessrequest.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.logansaso.signaccessrequest.SignAccessRequestPlugin;
import com.logansaso.signaccessrequest.auth.TokenManager;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class C1ApiClient {
    private final String baseUrl;
    private final String accessRequestEndpoint;
    private final TokenManager tokenManager;
    private final SignAccessRequestPlugin plugin;
    private final Gson gson;

    public C1ApiClient(String baseUrl, String accessRequestEndpoint, TokenManager tokenManager, SignAccessRequestPlugin plugin) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.accessRequestEndpoint = accessRequestEndpoint;
        this.tokenManager = tokenManager;
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /**
     * Creates an access request for the given entitlement slug
     *
     * @param player The player requesting access
     * @param entitlementSlug The slug of the entitlement to request
     * @return A CompletableFuture that completes with an AccessRequestResult
     */
    public CompletableFuture<AccessRequestResult> createAccessRequest(Player player, String entitlementSlug) {
        return tokenManager.getAccessToken().thenApplyAsync(token -> {
            try {
                String requestUrl = baseUrl + "/" + accessRequestEndpoint;
                URL url = new URL(requestUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);

                // Build request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("entitlementSlug", entitlementSlug);
                requestBody.addProperty("userId", player.getUniqueId().toString());
                requestBody.addProperty("userName", player.getName());

                // Add justification if needed
                JsonObject metadata = new JsonObject();
                metadata.addProperty("source", "minecraft-sign");
                metadata.addProperty("playerName", player.getName());
                requestBody.add("metadata", metadata);

                String requestBodyJson = gson.toJson(requestBody);

                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Access Request:");
                    plugin.getLogger().info("[DEBUG]   URL: " + requestUrl);
                    plugin.getLogger().info("[DEBUG]   Method: POST");
                    plugin.getLogger().info("[DEBUG]   Body: " + requestBodyJson);
                }

                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBodyJson.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();

                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Access Request Response Code: " + responseCode);
                }

                if (responseCode == 200 || responseCode == 201) {
                    String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                    if (plugin.isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] Access Request Response: " + response);
                    }

                    plugin.getLogger().info("Successfully created access request for " + player.getName() +
                                          " for entitlement: " + entitlementSlug);

                    // Parse response to get task ID and construct URL
                    String taskUrl = extractTaskUrl(response);
                    return new AccessRequestResult(true, "Request submitted", taskUrl);
                } else if (responseCode == 401) {
                    // Token might be invalid, invalidate it
                    tokenManager.invalidateToken();
                    String errorMsg = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

                    if (plugin.isDebugMode()) {
                        plugin.getLogger().warning("[DEBUG] Auth Error Response: " + errorMsg);
                    }

                    plugin.getLogger().warning("Authentication failed when creating access request: " + errorMsg);
                    return new AccessRequestResult(false, "Authentication failed. Please contact an admin.", null);
                } else {
                    String errorMsg = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

                    if (plugin.isDebugMode()) {
                        plugin.getLogger().warning("[DEBUG] Error Response: " + errorMsg);
                    }

                    plugin.getLogger().warning("Failed to create access request. HTTP " + responseCode + ": " + errorMsg);
                    return new AccessRequestResult(false, "API returned error code " + responseCode, null);
                }

            } catch (IOException e) {
                plugin.getLogger().severe("Error creating access request: " + e.getMessage());
                if (plugin.isDebugMode()) {
                    e.printStackTrace();
                }
                return new AccessRequestResult(false, "Network connection failed", null);
            }
        });
    }

    /**
     * Extracts the task URL from the API response
     */
    private String extractTaskUrl(String response) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            // Try to extract task ID from different possible response formats
            String taskId = null;
            if (jsonResponse.has("id")) {
                taskId = jsonResponse.get("id").getAsString();
            } else if (jsonResponse.has("taskId")) {
                taskId = jsonResponse.get("taskId").getAsString();
            } else if (jsonResponse.has("task") && jsonResponse.getAsJsonObject("task").has("id")) {
                taskId = jsonResponse.getAsJsonObject("task").get("id").getAsString();
            }

            if (taskId != null) {
                // Construct the task URL
                return baseUrl + "/tasks/" + taskId;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to extract task URL from response: " + e.getMessage());
        }
        return null;
    }

    public static class AccessRequestResult {
        private final boolean success;
        private final String message;
        private final String taskUrl;

        public AccessRequestResult(boolean success, String message, String taskUrl) {
            this.success = success;
            this.message = message;
            this.taskUrl = taskUrl;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getTaskUrl() {
            return taskUrl;
        }
    }
}
