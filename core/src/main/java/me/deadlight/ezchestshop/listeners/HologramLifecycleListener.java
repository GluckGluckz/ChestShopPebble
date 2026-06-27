package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.holograms.HologramCleanup;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.io.File;
import java.util.Locale;

/** Clears client-side hologram packets before PebbleShop reloads/rebuilds config state. */
public final class HologramLifecycleListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isReloadCommand(event.getMessage())) {
            prepareReload("player reload command");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (isReloadCommand(event.getCommand())) {
            prepareReload("console reload command");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == EzChestShop.getPlugin()) {
            HologramCleanup.resetAll("plugin disable");
        }
    }

    private void prepareReload(String reason) {
        HologramCleanup.resetAll(reason);
        ensureConfigExists();
    }

    private void ensureConfigExists() {
        EzChestShop plugin = EzChestShop.getPlugin();
        if (plugin == null) return;
        File config = new File(plugin.getDataFolder(), "config.yml");
        if (!config.exists()) {
            plugin.saveResource("config.yml", false);
            plugin.getLogger().info("[PebbleShop] Missing config.yml was regenerated before reload.");
        }
    }

    private boolean isReloadCommand(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        String[] parts = normalized.split("\\s+");
        if (parts.length < 2) return false;
        String command = parts[0];
        String subcommand = parts[1];
        return subcommand.equals("reload") && (command.equals("ecsadmin")
                || command.equals("pshopadmin")
                || command.equals("psadmin")
                || command.equals("pebbleadminshop")
                || command.equals("pebblemarketadmin"));
    }
}
