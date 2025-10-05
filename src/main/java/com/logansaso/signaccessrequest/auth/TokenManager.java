package com.logansaso.signaccessrequest.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.logansaso.signaccessrequest.SignAccessRequestPlugin;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

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

        // Validate client credentials
        if (clientId == null || clientId.length() < 20) {
            throw new IllegalArgumentException("clientId must be at least 20 characters long. Current length: " +
                (clientId == null ? "null" : clientId.length()) + ". Please check your config.yml");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalArgumentException("clientSecret cannot be empty. Please check your config.yml");
        }

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

    /**
     * Parses ConductorOne client secret in the format: prefix:data:v1:base64url_encoded_jwk
     * Returns the Ed25519 private key bytes
     */
    private byte[] parseClientSecret() throws Exception {
        String[] parts = clientSecret.split(":", 4);

        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid client secret format. Expected format: prefix:data:v1:base64url_jwk");
        }

        // Verify the version identifier (third part should be "v1")
        if (!"v1".equals(parts[2])) {
            throw new IllegalArgumentException("Invalid client secret version. Expected 'v1', got: " + parts[2]);
        }

        // Decode the base64-URL encoded JWK (fourth part)
        byte[] jwkBytes = Base64.getUrlDecoder().decode(parts[3]);

        // Parse JWK JSON to extract the private key "d" field
        String jwkJson = new String(jwkBytes, StandardCharsets.UTF_8);
        JsonObject jwk = gson.fromJson(jwkJson, JsonObject.class);

        if (!jwk.has("d")) {
            throw new IllegalArgumentException("JWK does not contain private key 'd' field");
        }

        // Decode the base64url-encoded private key
        String dValue = jwk.get("d").getAsString();
        return Base64.getUrlDecoder().decode(dValue);
    }

    /**
     * Creates a signed JWT for client assertion using EdDSA (Ed25519)
     */
    private String createClientAssertion() throws Exception {
        // Extract audience (hostname without port)
        String audience = baseUrl.replaceFirst("https?://", "").split(":")[0];

        // Current time in seconds
        long now = System.currentTimeMillis() / 1000;
        long expiry = now + 120; // 2 minutes from now
        long notBefore = now - 120; // 2 minutes ago

        // Build JWT header
        JsonObject header = new JsonObject();
        header.addProperty("alg", "EdDSA");
        header.addProperty("typ", "JWT");

        // Build JWT claims
        JsonObject claims = new JsonObject();
        claims.addProperty("iss", clientId);
        claims.addProperty("sub", clientId);
        claims.addProperty("aud", audience);
        claims.addProperty("exp", expiry);
        claims.addProperty("iat", now);
        claims.addProperty("nbf", notBefore);

        // Encode header and claims
        String headerEncoded = base64UrlEncode(header.toString().getBytes(StandardCharsets.UTF_8));
        String claimsEncoded = base64UrlEncode(claims.toString().getBytes(StandardCharsets.UTF_8));

        // Create signing input
        String signingInput = headerEncoded + "." + claimsEncoded;

        // Parse private key and sign
        byte[] privateKeyBytes = parseClientSecret();
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(privateKeyBytes, 0);

        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        byte[] message = signingInput.getBytes(StandardCharsets.UTF_8);
        signer.update(message, 0, message.length);
        byte[] signature = signer.generateSignature();

        // Encode signature
        String signatureEncoded = base64UrlEncode(signature);

        // Return complete JWT
        return signingInput + "." + signatureEncoded;
    }

    /**
     * Base64 URL-safe encoding without padding
     */
    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private CompletableFuture<String> fetchNewToken() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create signed JWT for client assertion
                String clientAssertion = createClientAssertion();

                String tokenUrl = baseUrl + "/" + tokenEndpoint;
                URL url = new URL(tokenUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                conn.setDoOutput(true);

                // Send request body with JWT client assertion (ConductorOne OAuth2 flow)
                String requestBody = "grant_type=client_credentials"
                    + "&client_id=" + java.net.URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_assertion_type=" + java.net.URLEncoder.encode("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", StandardCharsets.UTF_8)
                    + "&client_assertion=" + java.net.URLEncoder.encode(clientAssertion, StandardCharsets.UTF_8);

                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Token Request:");
                    plugin.getLogger().info("[DEBUG]   URL: " + tokenUrl);
                    plugin.getLogger().info("[DEBUG]   Method: POST");
                    plugin.getLogger().info("[DEBUG]   Body (assertion masked): grant_type=client_credentials&client_id=" + clientId + "&client_assertion_type=...");
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

                // Set expiry with 5-minute buffer
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
