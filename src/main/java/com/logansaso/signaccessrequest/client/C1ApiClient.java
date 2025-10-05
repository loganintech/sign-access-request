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
    private final String grantTaskEndpoint;
    private final TokenManager tokenManager;
    private final SignAccessRequestPlugin plugin;
    private final Gson gson;

    public C1ApiClient(String baseUrl, String grantTaskEndpoint, TokenManager tokenManager, SignAccessRequestPlugin plugin) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.grantTaskEndpoint = grantTaskEndpoint;
        this.tokenManager = tokenManager;
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /**
     * Creates a grant task for the given entitlement alias
     *
     * @param player The player requesting access
     * @param entitlementAlias The alias of the entitlement to request (e.g., "prod-admin-access")
     * @return A CompletableFuture that completes with an AccessRequestResult
     */
    public CompletableFuture<AccessRequestResult> createGrantTask(Player player, String entitlementAlias) {
        return tokenManager.getAccessToken().thenComposeAsync(token -> {
            try {
                // Step 1: Search for entitlement by alias
                JsonObject entitlement = searchEntitlementByAlias(token, entitlementAlias);
                if (entitlement == null) {
                    return CompletableFuture.completedFuture(
                        new AccessRequestResult(false, "Entitlement '" + entitlementAlias + "' not found", null));
                }

                String appId = entitlement.get("appId").getAsString();
                String entitlementId = entitlement.get("id").getAsString();

                // Step 2: Search for app user in this specific app by minecraft username
                String appUserId = searchAppUserByUsername(token, appId, player.getName());
                if (appUserId == null) {
                    return CompletableFuture.completedFuture(
                        new AccessRequestResult(false, "User '" + player.getName() + "' not found in app", null));
                }

                // Step 3: Create grant task
                return createGrantTaskWithIds(token, player, appId, entitlementId, appUserId, entitlementAlias);

            } catch (Exception e) {
                plugin.getLogger().severe("Error in grant task workflow: " + e.getMessage());
                if (plugin.isDebugMode()) {
                    e.printStackTrace();
                }
                return CompletableFuture.completedFuture(
                    new AccessRequestResult(false, "Internal error: " + e.getMessage(), null));
            }
        });
    }

    /**
     * Searches for an entitlement by alias
     */
    private JsonObject searchEntitlementByAlias(String token, String alias) throws IOException {
        String searchUrl = baseUrl + "/api/v1/search/entitlements";
        URL url = new URL(searchUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("alias", alias);
        requestBody.addProperty("pageSize", 1);

        String requestBodyJson = gson.toJson(requestBody);

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] Entitlement Search:");
            plugin.getLogger().info("[DEBUG]   Alias: " + alias);
        }

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBodyJson.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            if (jsonResponse.has("list") && jsonResponse.getAsJsonArray("list").size() > 0) {
                JsonObject entitlementView = jsonResponse.getAsJsonArray("list").get(0).getAsJsonObject();
                if (entitlementView.has("appEntitlement")) {
                    return entitlementView.getAsJsonObject("appEntitlement");
                }
            }
        }

        return null;
    }

    /**
     * Searches for an app user in a specific app by minecraft username
     */
    private String searchAppUserByUsername(String token, String appId, String username) throws IOException {
        String searchUrl = baseUrl + "/api/v1/search/app_users";
        URL url = new URL(searchUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);
        requestBody.addProperty("query", username);
        requestBody.addProperty("pageSize", 1);

        String requestBodyJson = gson.toJson(requestBody);

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] App User Search:");
            plugin.getLogger().info("[DEBUG]   App ID: " + appId);
            plugin.getLogger().info("[DEBUG]   Query: " + username);
        }

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBodyJson.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            if (jsonResponse.has("list") && jsonResponse.getAsJsonArray("list").size() > 0) {
                JsonObject appUserView = jsonResponse.getAsJsonArray("list").get(0).getAsJsonObject();
                if (appUserView.has("appUser")) {
                    JsonObject appUser = appUserView.getAsJsonObject("appUser");
                    if (appUser.has("id")) {
                        return appUser.get("id").getAsString();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Creates a grant task with the resolved IDs
     */
    private CompletableFuture<AccessRequestResult> createGrantTaskWithIds(String token, Player player,
                                                                            String appId, String entitlementId,
                                                                            String appUserId, String entitlementAlias) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestUrl = baseUrl + "/" + grantTaskEndpoint;
                URL url = new URL(requestUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);

                // Build grant task request body according to API spec
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("appId", appId);
                requestBody.addProperty("appEntitlementId", entitlementId);
                requestBody.addProperty("appUserId", appUserId);

                // Add description with player info
                requestBody.addProperty("description", "Access request from Minecraft player: " + player.getName());

                // Add metadata in requestData field
                JsonObject requestData = new JsonObject();
                requestData.addProperty("source", "minecraft-sign");
                requestData.addProperty("playerName", player.getName());
                requestData.addProperty("playerUUID", player.getUniqueId().toString());
                requestBody.add("requestData", requestData);

                String requestBodyJson = gson.toJson(requestBody);

                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Grant Task Request:");
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
                        plugin.getLogger().info("[DEBUG] Grant Task Response: " + response);
                    }

                    plugin.getLogger().info("Successfully created grant task for " + player.getName() +
                                          " for entitlement: " + entitlementAlias);

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
     * Extracts the task URL from the grant task API response using numeric_id
     */
    private String extractTaskUrl(String response) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            // Grant task response format: { "taskView": { "task": { "numericId": "..." } } }
            String numericId = null;
            if (jsonResponse.has("taskView")) {
                JsonObject taskView = jsonResponse.getAsJsonObject("taskView");
                if (taskView.has("task")) {
                    JsonObject task = taskView.getAsJsonObject("task");
                    // Try numeric_id first (for user-friendly URLs)
                    if (task.has("numericId")) {
                        numericId = task.get("numericId").getAsString();
                    }
                    // Fallback to regular id
                    else if (task.has("id")) {
                        numericId = task.get("id").getAsString();
                    }
                }
            }

            if (numericId != null) {
                // Construct the task URL with numeric ID
                return baseUrl + "/tasks/" + numericId;
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
