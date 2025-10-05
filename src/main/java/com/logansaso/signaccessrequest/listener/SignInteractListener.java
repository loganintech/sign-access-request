package com.logansaso.signaccessrequest.listener;

import com.logansaso.signaccessrequest.SignAccessRequestPlugin;
import com.logansaso.signaccessrequest.client.C1ApiClient;
import com.logansaso.signaccessrequest.util.SignValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

public class SignInteractListener implements Listener {

    private final C1ApiClient apiClient;
    private final SignAccessRequestPlugin plugin;

    public SignInteractListener(C1ApiClient apiClient, SignAccessRequestPlugin plugin) {
        this.apiClient = apiClient;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) block.getState();
        Player player = event.getPlayer();
        Component line1 = sign.line(0);

        // Check if this is a C1 request sign
        if (!SignValidator.containsSignPrefix(line1)) {
            return;
        }

        // Check if player has permission to use signs
        if (!player.hasPermission("signaccessrequest.use")) {
            player.sendMessage(Component.text("You don't have permission to use C1 access request signs!")
                .color(NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Check if sign is valid (blue text = valid)
        if (!SignValidator.isValidSign(line1)) {
            player.sendMessage(Component.text("This sign is not properly configured! The first line must be blue.")
                .color(NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Get the entitlement alias from line 2
        Component line2 = sign.line(1);
        String entitlementAlias = SignValidator.getPlainText(line2);

        if (entitlementAlias.isEmpty()) {
            player.sendMessage(Component.text("This sign is missing an entitlement alias on line 2!")
                .color(NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Cancel the event to prevent any default behavior
        event.setCancelled(true);

        // Send grant task request with user feedback
        player.sendMessage(Component.text("⏳ Submitting access request for: ")
            .color(NamedTextColor.YELLOW)
            .append(Component.text(entitlementAlias).color(NamedTextColor.WHITE))
            .append(Component.text("...").color(NamedTextColor.YELLOW)));

        apiClient.createGrantTask(player, entitlementAlias).thenAccept(result -> {
            // Schedule back to main thread for sending message
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    player.sendMessage(Component.text("✓ Access request submitted successfully!")
                        .color(NamedTextColor.GREEN));

                    // Show task URL if available
                    if (result.getTaskUrl() != null) {
                        player.sendMessage(Component.text("   View your request: ")
                            .color(NamedTextColor.GRAY)
                            .append(Component.text(result.getTaskUrl())
                                .color(NamedTextColor.AQUA)));
                    }
                } else {
                    player.sendMessage(Component.text("✗ Failed to submit access request")
                        .color(NamedTextColor.RED));
                    player.sendMessage(Component.text("   " + result.getMessage())
                        .color(NamedTextColor.GRAY));
                }
            });
        }).exceptionally(throwable -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(Component.text("✗ An error occurred while submitting the request")
                    .color(NamedTextColor.RED));
                player.sendMessage(Component.text("   Please contact an administrator")
                    .color(NamedTextColor.GRAY));
                plugin.getLogger().severe("Error in access request for " + player.getName() + ": " + throwable.getMessage());
                if (plugin.isDebugMode()) {
                    throwable.printStackTrace();
                }
            });
            return null;
        });
    }
}
