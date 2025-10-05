package com.logansaso.signaccessrequest;

import com.logansaso.signaccessrequest.auth.TokenManager;
import com.logansaso.signaccessrequest.client.C1ApiClient;
import com.logansaso.signaccessrequest.command.C1CommandExecutor;
import com.logansaso.signaccessrequest.listener.SignBreakListener;
import com.logansaso.signaccessrequest.listener.SignChangeListener;
import com.logansaso.signaccessrequest.listener.SignInteractListener;
import org.bukkit.plugin.java.JavaPlugin;

public class SignAccessRequestPlugin extends JavaPlugin {

    private TokenManager tokenManager;
    private C1ApiClient apiClient;
    private boolean debugMode;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize authentication and API client
        try {
            initializeServices();
        } catch (IllegalArgumentException e) {
            getLogger().severe("Failed to initialize plugin: " + e.getMessage());
            getLogger().severe("Please check your config.yml configuration");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register event listeners
        registerListeners();

        // Register commands
        registerCommands();

        getLogger().info("SignAccessRequest plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SignAccessRequest plugin disabled");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        debugMode = getConfig().getBoolean("debug.enabled", false);

        // Reinitialize services with new config
        try {
            initializeServices();
            getLogger().info("Configuration reloaded successfully!");
        } catch (IllegalArgumentException e) {
            getLogger().severe("Failed to reload configuration: " + e.getMessage());
            throw e;
        }
    }

    private void initializeServices() {
        String baseUrl = getConfig().getString("conductorone.base-url");
        String clientId = getConfig().getString("conductorone.client-id");
        String clientSecret = getConfig().getString("conductorone.client-secret");
        String tokenEndpoint = getConfig().getString("conductorone.token-endpoint");
        String grantTaskEndpoint = getConfig().getString("conductorone.grant-task-endpoint");
        String revokeTaskEndpoint = getConfig().getString("conductorone.revoke-task-endpoint");
        debugMode = getConfig().getBoolean("debug.enabled", false);

        // Debug log the loaded values (mask sensitive data)
        getLogger().info("Loading configuration:");
        getLogger().info("  base-url: " + baseUrl);
        getLogger().info("  client-id length: " + (clientId != null ? clientId.length() : "null") + " characters");
        getLogger().info("  client-secret length: " + (clientSecret != null ? clientSecret.length() : "null") + " characters");
        getLogger().info("  token-endpoint: " + tokenEndpoint);
        getLogger().info("  grant-task-endpoint: " + grantTaskEndpoint);
        getLogger().info("  revoke-task-endpoint: " + revokeTaskEndpoint);

        // Validate configuration
        if (baseUrl == null || baseUrl.isEmpty() || baseUrl.equals("https://your-tenant.conductor.one")) {
            throw new IllegalArgumentException("conductorone.base-url is not configured");
        }
        if (clientId == null || clientId.isEmpty() || clientId.equals("your-client-id-here")) {
            throw new IllegalArgumentException("conductorone.client-id is not configured");
        }
        if (clientSecret == null || clientSecret.isEmpty() || clientSecret.equals("your-client-secret-here")) {
            throw new IllegalArgumentException("conductorone.client-secret is not configured");
        }

        // Initialize token manager
        tokenManager = new TokenManager(
            baseUrl,
            clientId,
            clientSecret,
            tokenEndpoint,
            this
        );

        // Initialize API client
        apiClient = new C1ApiClient(
            baseUrl,
            grantTaskEndpoint,
            revokeTaskEndpoint,
            tokenManager,
            this
        );
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new SignChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new SignInteractListener(apiClient, this), this);
        getServer().getPluginManager().registerEvents(new SignBreakListener(this), this);
    }

    private void registerCommands() {
        C1CommandExecutor commandExecutor = new C1CommandExecutor(this);
        getCommand("c1reload").setExecutor(commandExecutor);
        getCommand("c1debug").setExecutor(commandExecutor);
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    public C1ApiClient getApiClient() {
        return apiClient;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        getConfig().set("debug.enabled", debugMode);
        saveConfig();
    }
}
