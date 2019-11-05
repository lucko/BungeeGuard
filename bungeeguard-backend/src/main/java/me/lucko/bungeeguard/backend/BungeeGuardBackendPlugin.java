/*
 * This file is part of BungeeGuard, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.bungeeguard.backend;

import me.lucko.bungeeguard.backend.listeners.BukkitLoginListener;
import me.lucko.bungeeguard.backend.listeners.PaperHandshakeListener;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Simple plugin which listens for player connections, and cancels all attempts
 * which don't contain the special token set by the proxy.
 *
 * The token is included within the player's profile properties, but removed during the handshake/login.
 */
public class BungeeGuardBackendPlugin extends JavaPlugin {

    private String noDataKickMessage;
    private String noPropertiesKickMessage;
    private String invalidTokenKickMessage;

    private Set<String> allowedTokens;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.noDataKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("no-data-kick-message"));
        this.noPropertiesKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("no-properties-kick-message"));
        this.invalidTokenKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("invalid-token-kick-message"));
        this.allowedTokens = new HashSet<>(config.getStringList("allowed-tokens"));

        try {
            Class.forName("com.destroystokyo.paper.event.player.PlayerHandshakeEvent");

            getLogger().info("Using Paper's PlayerHandshakeEvent for connection filtering.");
            getServer().getPluginManager().registerEvents(new PaperHandshakeListener(this), this);
        } catch (ClassNotFoundException e) {
            getLogger().info("Using Bukkit's PlayerLoginEvent for connection filtering. " +
                    "Please consider upgrading to Paper (https://papermc.io/) to allow BungeeGuard to filter connections at the handshake stage (better!).");
            getServer().getPluginManager().registerEvents(new BukkitLoginListener(this), this);
        }
    }

    public String getNoDataKickMessage() {
        return this.noDataKickMessage;
    }

    public String getNoPropertiesKickMessage() {
        return this.noPropertiesKickMessage;
    }

    public String getInvalidTokenKickMessage() {
        return this.invalidTokenKickMessage;
    }

    public boolean denyLogin(String address, UUID uniqueId, String token) {
        if (this.allowedTokens.isEmpty()) {
            getLogger().warning("No token configured. Saving the one from the connection " + uniqueId + " @ " + address + " to the config!");
            this.allowedTokens.add(token);
            getConfig().set("allowed-tokens", new ArrayList<>(this.allowedTokens));
            saveConfig();
        } else if (!this.allowedTokens.contains(token)) {
            logDeniedConnection(address, uniqueId, "An invalid token was used: " + token);
            return true;
        }

        return false;
    }

    public void logDeniedConnection(String address, UUID uniqueId, String message) {
        getLogger().warning("Denied connection from " + uniqueId + " @ " + address + " - " + message);
    }
}
