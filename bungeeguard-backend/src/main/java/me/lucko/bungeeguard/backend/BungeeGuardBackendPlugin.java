package me.lucko.bungeeguard.backend;

import me.lucko.bungeeguard.backend.listeners.PaperHandhakeListener;
import me.lucko.bungeeguard.backend.listeners.BukkitLoginListener;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Simple plugin which re-implements the BungeeCord handshake protocol, and cancels all attempts
 * which don't contain the special token set by the proxy.
 *
 * The token is included within the player's profile properties, but removed during the handshake.
 */
public class BungeeGuardBackendPlugin extends JavaPlugin {

    private String noDataKickMessage;
    private String noPropertiesKickMessage;
    private String invalidTokenKickMessage;

    private Set<String> allowedTokens;

    @Override
    public void onEnable() {
        try {
            Class.forName("com.destroystokyo.paper.event.player.PlayerHandshakeEvent");

            getLogger().info("Using Paper PlayerHandshakeEvent");
            getServer().getPluginManager().registerEvents(new PaperHandhakeListener(this), this);
        } catch (ClassNotFoundException e) {
            getLogger().info("Using Bukkit PlayerLoginEvent");
            getServer().getPluginManager().registerEvents(new BukkitLoginListener(this), this);
        }

        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.noDataKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("no-data-kick-message"));
        this.noPropertiesKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("no-properties-kick-message"));
        this.invalidTokenKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("invalid-token-kick-message"));
        this.allowedTokens = new HashSet<>(config.getStringList("allowed-tokens"));
    }

    public String getNoDataKickMessage() {
        return noDataKickMessage;
    }

    public String getNoPropertiesKickMessage() {
        return noPropertiesKickMessage;
    }

    public String getInvalidTokenKickMessage() {
        return invalidTokenKickMessage;
    }

    public boolean denyLogin(String address, UUID uniqueId, String token) {
        if (allowedTokens.isEmpty()) {
            getLogger().warning("No token configured. Saving the one from the connection " + uniqueId + " @ " + address + " to the config!");
            allowedTokens.add(token);
            getConfig().set("allowed-tokens", new ArrayList<>(allowedTokens));
            saveConfig();
        } else if (!allowedTokens.contains(token)) {
            logDeniedConnection(address, uniqueId, "An invalid token was used: " + token);
            return true;
        }

        return false;
    }

    public void logDeniedConnection(String address, UUID uniqueId, String message) {
        getLogger().warning("Denied connection from " + uniqueId + " @ " + address + " - " + message);
    }
}
