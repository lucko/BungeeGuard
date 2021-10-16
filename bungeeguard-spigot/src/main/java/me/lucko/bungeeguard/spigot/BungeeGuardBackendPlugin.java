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

package me.lucko.bungeeguard.spigot;

import me.lucko.bungeeguard.backend.BungeeGuardBackend;
import me.lucko.bungeeguard.backend.TokenStore;
import me.lucko.bungeeguard.spigot.listener.PaperHandshakeListener;
import me.lucko.bungeeguard.spigot.listener.ProtocolHandshakeListener;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Simple plugin which overrides the BungeeCord handshake protocol, and cancels all
 * connections which don't contain a special auth token set by the proxy.
 *
 * The token is included within the player's profile properties, but removed during the handshake.
 */
public class BungeeGuardBackendPlugin extends JavaPlugin implements BungeeGuardBackend {

    private TokenStore tokenStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.tokenStore = new TokenStore(this);
        this.tokenStore.load();

        if (isPaperHandshakeEvent()) {
            getLogger().info("Using Paper's PlayerHandshakeEvent to listen for connections.");

            PaperHandshakeListener listener = new PaperHandshakeListener(this, this.tokenStore);
            getServer().getPluginManager().registerEvents(listener, this);

        } else if (hasProtocolLib()) {
            getLogger().info("Using ProtocolLib to listen for connections.");

            ProtocolHandshakeListener listener = new ProtocolHandshakeListener(this, this.tokenStore);
            listener.registerAdapter(this);

        } else {
            getLogger().severe("------------------------------------------------------------");
            getLogger().severe("BungeeGuard is unable to listen for handshakes! The server will now shut down.");
            getLogger().severe("");
            if (isPaperServer()) {
                getLogger().severe("Please install ProtocolLib in order to use this plugin.");
            } else {
                getLogger().severe("If your server is using 1.9.4 or newer, please upgrade to Paper - https://papermc.io");
                getLogger().severe("If your server is using 1.8.8 or older, please install ProtocolLib.");
            }
            getLogger().severe("------------------------------------------------------------");
            getServer().shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(ChatColor.RED + "Sorry, this command can only be ran from the console.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ChatColor.RED + "Running BungeeGuard v" + getDescription().getVersion());
            sender.sendMessage(ChatColor.GRAY + "Use '/bungeeguard reload' to reload the configuration.");
            return true;
        }

        this.tokenStore.reload();
        sender.sendMessage(ChatColor.RED + "BungeeGuard configuration reloaded.");
        return true;
    }

    @Override
    public String getMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString(key));
    }

    @Override
    public List<String> getTokens() {
        return getConfig().getStringList("allowed-tokens");
    }

    private static boolean isPaperHandshakeEvent() {
        return classExists("com.destroystokyo.paper.event.player.PlayerHandshakeEvent");
    }

    private static boolean isPaperServer() {
        return classExists("com.destroystokyo.paper.PaperConfig");
    }

    private boolean hasProtocolLib() {
        return getServer().getPluginManager().getPlugin("ProtocolLib") != null;
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}
