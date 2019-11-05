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

package me.lucko.bungeeguard.backend.listeners;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.lucko.bungeeguard.backend.BungeeGuardBackendPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Collection;
import java.util.logging.Level;

public class BukkitLoginListener implements Listener {
    private final BungeeGuardBackendPlugin plugin;

    public BukkitLoginListener(BungeeGuardBackendPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLogin(PlayerLoginEvent e) {
        Player player = e.getPlayer();
        String hostAddress = e.getAddress().getHostAddress();

        GameProfile gameProfile;

        try {
            // get the GameProfile of the player
            gameProfile = (GameProfile) player.getClass().getDeclaredMethod("getProfile").invoke(player);
        } catch (ReflectiveOperationException ex) {
            this.plugin.getLogger().log(Level.WARNING, "Denied connection from " + player.getUniqueId() + " @ " + hostAddress + " - Unable to get GameProfile.", ex);
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, this.plugin.getNoDataKickMessage());
            return;
        }

        Collection<Property> tokens = gameProfile.getProperties().get("bungeeguard-token");

        // deny connection if no token was provided
        if (tokens.isEmpty()) {
            this.plugin.logDeniedConnection(hostAddress, player.getUniqueId(), "A token was not included in their GameProfile properties.");
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, this.plugin.getNoDataKickMessage());
            return;
        }

        Property tokenProperty = tokens.iterator().next();
        String token = tokenProperty.getValue();

        if (this.plugin.denyLogin(hostAddress, player.getUniqueId(), token)) {
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, this.plugin.getInvalidTokenKickMessage());
            return;
        }

        // remove our property
        gameProfile.getProperties().removeAll("bungeeguard-token");
    }
}
