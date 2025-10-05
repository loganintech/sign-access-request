package com.logansaso.signaccessrequest.listener;

import com.logansaso.signaccessrequest.SignAccessRequestPlugin;
import com.logansaso.signaccessrequest.util.SignValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class SignChangeListener implements Listener {

    private final SignAccessRequestPlugin plugin;

    public SignChangeListener(SignAccessRequestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Component line1 = event.line(0);

        // Check if this is a C1 request sign
        if (line1 == null || !SignValidator.containsSignPrefix(line1)) {
            return;
        }

        // Check if player has permission to create signs
        if (!player.hasPermission("signaccessrequest.create")) {
            event.setCancelled(true);
            player.sendMessage(Component.text("✗ You don't have permission to create C1 access request signs!")
                .color(NamedTextColor.RED));
            return;
        }

        // Determine sign type
        SignValidator.SignType signType = SignValidator.getSignType(line1);
        String signPrefix = SignValidator.getPrefixForType(signType);

        // Validate the sign format
        Component line2 = event.line(1);
        String entitlementSlug = SignValidator.getPlainText(line2);

        if (entitlementSlug.isEmpty()) {
            // Invalid sign - no entitlement slug on line 2
            event.line(0, Component.text(signPrefix).color(NamedTextColor.RED));
            player.sendMessage(Component.text("✗ Invalid sign! Line 2 must contain the entitlement alias.")
                .color(NamedTextColor.RED));
            player.sendMessage(Component.text("   Example: prod-admin-access")
                .color(NamedTextColor.GRAY));
            return;
        }

        // Valid sign - make line 1 blue, keep line 2 as-is
        event.line(0, Component.text(signPrefix).color(NamedTextColor.BLUE));

        String actionType = signType == SignValidator.SignType.GRANT ? "grant" : "revoke";
        player.sendMessage(Component.text("✓ C1 " + actionType + " sign created successfully!")
            .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("   Entitlement: " + entitlementSlug)
            .color(NamedTextColor.GRAY));

        plugin.getLogger().info("Player " + player.getName() + " created a C1 " + actionType + " sign for entitlement: " + entitlementSlug);
    }
}
