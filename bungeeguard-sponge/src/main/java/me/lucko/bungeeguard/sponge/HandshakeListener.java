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

package me.lucko.bungeeguard.sponge;

import me.lucko.bungeeguard.backend.BungeeGuardBackend;
import me.lucko.bungeeguard.backend.TokenStore;
import me.lucko.bungeeguard.backend.listener.AbstractHandshakeListener;

import org.slf4j.Logger;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.profile.property.ProfileProperty;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.Collection;
import java.util.Iterator;

public class HandshakeListener extends AbstractHandshakeListener {

    private final Text noDataKickText;
    private final Text invalidTokenKickText;
    private final Logger logger;

    public HandshakeListener(BungeeGuardBackend plugin, TokenStore tokenStore, Logger logger) {
        super(plugin, tokenStore);
        this.logger = logger;
        this.noDataKickText = TextSerializers.FORMATTING_CODE.deserialize(this.noDataKickMessage);
        this.invalidTokenKickText = TextSerializers.FORMATTING_CODE.deserialize(this.invalidTokenKickMessage);
    }

    @Listener
    public void onClientAuth(ClientConnectionEvent.Auth e) {
        GameProfile profile = e.getProfile();
        Collection<ProfileProperty> tokens = profile.getPropertyMap().get("bungeeguard-token");

        String bungeeGuardToken = null;

        for (Iterator<ProfileProperty> iterator = tokens.iterator(); iterator.hasNext(); ) {
            bungeeGuardToken = iterator.next().getValue();
            iterator.remove();
        }

        if (bungeeGuardToken == null || !this.tokenStore.isAllowed(bungeeGuardToken)) {
            String connectionDescription = profile.getUniqueId() + " @ " + e.getConnection().getAddress().getHostString();
            String reason = bungeeGuardToken == null ? "No Token" : "Invalid token";

            this.logger.warn("Denying connection from " + connectionDescription + " - reason: " + reason);

            e.setMessage(bungeeGuardToken == null ? this.noDataKickText : this.invalidTokenKickText);
            e.setCancelled(true);
            e.setMessageCancelled(false);
        }
    }
}
