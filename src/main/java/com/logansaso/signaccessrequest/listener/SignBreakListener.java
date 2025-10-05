package com.logansaso.signaccessrequest.listener;

import com.logansaso.signaccessrequest.SignAccessRequestPlugin;
import com.logansaso.signaccessrequest.util.SignValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class SignBreakListener implements Listener {

    private final SignAccessRequestPlugin plugin;

    public SignBreakListener(SignAccessRequestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Check if the block is a sign
        if (!(block.getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) block.getState();
        Player player = event.getPlayer();
        Component line1 = sign.line(0);

        // Check if this is a C1 request sign
        if (!SignValidator.containsSignPrefix(line1)) {
            return;
        }

        // Check if sign is valid (blue text = valid)
        boolean isValidSign = SignValidator.isValidSign(line1);

        if (isValidSign) {
            // Valid sign - requires destroy permission
            if (!player.hasPermission("signaccessrequest.destroy")) {
                event.setCancelled(true);
                player.sendMessage(Component.text("✗ You don't have permission to destroy C1 access request signs!")
                    .color(NamedTextColor.RED));
                return;
            }

            // Player has permission - log the action
            String entitlementSlug = SignValidator.getPlainText(sign.line(1));
            plugin.getLogger().info("Player " + player.getName() + " destroyed a C1 access request sign (entitlement: " + entitlementSlug + ")");
        } else {
            // Invalid sign (red text) - can be destroyed by creator or anyone with destroy permission
            if (!player.hasPermission("signaccessrequest.create") &&
                !player.hasPermission("signaccessrequest.destroy")) {
                event.setCancelled(true);
                player.sendMessage(Component.text("✗ You don't have permission to destroy this sign!")
                    .color(NamedTextColor.RED));
                return;
            }

            plugin.getLogger().info("Player " + player.getName() + " removed an invalid C1 sign");
        }
    }
}
