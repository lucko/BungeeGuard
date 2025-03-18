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

package me.lucko.bungeeguard.spigot.listener;

import com.destroystokyo.paper.event.player.PlayerHandshakeEvent;

import me.lucko.bungeeguard.backend.TokenStore;
import me.lucko.bungeeguard.backend.listener.AbstractHandshakeListener;
import me.lucko.bungeeguard.spigot.BungeeCordHandshake;

import me.lucko.bungeeguard.spigot.BungeeGuardBackendPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A handshake listener using Paper's {@link PlayerHandshakeEvent}.
 */
public class PaperHandshakeListener extends AbstractHandshakeListener implements Listener {
    
    private static final Method getOriginalSocketAddressHostname;
    static {
        Method method = null;
        try {
            method = PlayerHandshakeEvent.class.getMethod("getOriginalSocketAddressHostname");
        } catch (NoSuchMethodException ignored) {
            // Paper added this method in 1.16
        }
        getOriginalSocketAddressHostname = method;
    }

    private final Logger logger;

    public PaperHandshakeListener(BungeeGuardBackendPlugin plugin, TokenStore tokenStore) {
        super(plugin, tokenStore);
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHandshake(PlayerHandshakeEvent e) {
        BungeeCordHandshake decoded = BungeeCordHandshake.decodeAndVerify(e.getOriginalHandshake(), this.tokenStore);

        if (decoded instanceof BungeeCordHandshake.Fail) {
            BungeeCordHandshake.Fail fail = (BungeeCordHandshake.Fail) decoded;
            String ip = "";
            if (getOriginalSocketAddressHostname != null) {
                try {
                    ip = getOriginalSocketAddressHostname.invoke(e) + " - ";
                } catch (ReflectiveOperationException ex) {
                    this.logger.log(Level.SEVERE, "Unable to get original address", ex);
                }
            }

            this.logger.warning("Denying connection from " + ip + " - reason: " + fail.reason().name());

            if (fail.reason() == BungeeCordHandshake.Fail.Reason.INVALID_HANDSHAKE) {
                e.setFailMessage(this.noDataKickMessage);
            } else {
                e.setFailMessage(this.invalidTokenKickMessage);
            }

            e.setFailed(true);
            return;
        }

        BungeeCordHandshake.Success data = (BungeeCordHandshake.Success) decoded;
        e.setServerHostname(data.serverHostname());
        e.setSocketAddressHostname(data.socketAddressHostname());
        e.setUniqueId(data.uniqueId());
        e.setPropertiesJson(data.propertiesJson());
    }

}
