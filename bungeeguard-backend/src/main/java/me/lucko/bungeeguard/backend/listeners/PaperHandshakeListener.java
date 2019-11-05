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

import com.destroystokyo.paper.event.player.PlayerHandshakeEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import me.lucko.bungeeguard.backend.BungeeGuardBackendPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaperHandshakeListener implements Listener {
    private static final Type PROPERTY_LIST_TYPE = new TypeToken<List<JsonObject>>() {}.getType();

    private final Gson gson = new Gson();
    private final BungeeGuardBackendPlugin plugin;

    public PaperHandshakeListener(BungeeGuardBackendPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHandshake(PlayerHandshakeEvent e) {
        String handshake = e.getOriginalHandshake();
        String[] split = handshake.split("\00");

        if (split.length != 3 && split.length != 4) {
            e.setFailMessage(this.plugin.getNoDataKickMessage());
            e.setFailed(true);
            return;
        }

        // extract ipforwarding info from the handshake
        String serverHostname = split[0];
        String socketAddressHostname = split[1];
        UUID uniqueId = UUID.fromString(split[2].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

        // doesn't contain any properties - so deny
        if (split.length == 3) {
            this.plugin.logDeniedConnection(socketAddressHostname, uniqueId, "No properties were sent in their handshake.");
            e.setFailMessage(this.plugin.getNoPropertiesKickMessage());
            e.setFailed(true);
            return;
        }

        // deserialize the properties in the handshake
        List<JsonObject> properties = new ArrayList<>(this.gson.fromJson(split[3], PROPERTY_LIST_TYPE));

        // fail if no properties
        if (properties.isEmpty()) {
            this.plugin.logDeniedConnection(socketAddressHostname, uniqueId, "No properties were sent in their handshake.");
            e.setFailMessage(this.plugin.getNoPropertiesKickMessage());
            e.setFailed(true);
            return;
        }

        String token = null;

        // try to find the token
        for (JsonObject property : properties) {
            if (property.get("name").getAsString().equals("bungeeguard-token")) {
                token = property.get("value").getAsString();
                break;
            }
        }

        // deny connection if no token was provided
        if (token == null) {
            this.plugin.logDeniedConnection(socketAddressHostname, uniqueId, "A token was not included in their handshake properties.");
            e.setFailMessage(this.plugin.getNoDataKickMessage());
            e.setFailed(true);
            return;
        }

        if (this.plugin.denyLogin(socketAddressHostname, uniqueId, token)) {
            e.setFailMessage(this.plugin.getInvalidTokenKickMessage());
            e.setFailed(true);
            return;
        }

        // remove our property
        properties.removeIf(property -> property.get("name").getAsString().equals("bungeeguard-token"));
        String newPropertiesString = this.gson.toJson(properties, PROPERTY_LIST_TYPE);

        // pass data back to the event
        e.setServerHostname(serverHostname);
        e.setSocketAddressHostname(socketAddressHostname);
        e.setUniqueId(uniqueId);
        e.setPropertiesJson(newPropertiesString);
    }
}
