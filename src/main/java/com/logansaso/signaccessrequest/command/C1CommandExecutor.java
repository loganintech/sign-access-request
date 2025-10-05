package com.logansaso.signaccessrequest.command;

import com.logansaso.signaccessrequest.SignAccessRequestPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class C1CommandExecutor implements CommandExecutor {

    private final SignAccessRequestPlugin plugin;

    public C1CommandExecutor(SignAccessRequestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("c1reload")) {
            return handleReload(sender);
        } else if (command.getName().equalsIgnoreCase("c1debug")) {
            return handleDebug(sender, args);
        }
        return false;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("signaccessrequest.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                .color(NamedTextColor.RED));
            return true;
        }

        try {
            plugin.reloadPluginConfig();
            sender.sendMessage(Component.text("SignAccessRequest configuration reloaded successfully!")
                .color(NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Debug mode: " + (plugin.isDebugMode() ? "enabled" : "disabled"))
                .color(NamedTextColor.GRAY));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration: " + e.getMessage())
                .color(NamedTextColor.RED));
            plugin.getLogger().severe("Error reloading config: " + e.getMessage());
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("signaccessrequest.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            // Toggle debug mode
            boolean newMode = !plugin.isDebugMode();
            plugin.setDebugMode(newMode);
            sender.sendMessage(Component.text("Debug mode " + (newMode ? "enabled" : "disabled"))
                .color(newMode ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } else {
            String arg = args[0].toLowerCase();
            if (arg.equals("on") || arg.equals("true") || arg.equals("enable")) {
                plugin.setDebugMode(true);
                sender.sendMessage(Component.text("Debug mode enabled")
                    .color(NamedTextColor.GREEN));
            } else if (arg.equals("off") || arg.equals("false") || arg.equals("disable")) {
                plugin.setDebugMode(false);
                sender.sendMessage(Component.text("Debug mode disabled")
                    .color(NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text("Usage: /c1debug [on|off]")
                    .color(NamedTextColor.RED));
                return false;
            }
        }
        return true;
    }
}
