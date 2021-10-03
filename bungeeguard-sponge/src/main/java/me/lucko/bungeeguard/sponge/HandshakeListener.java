package me.lucko.bungeeguard.sponge;

import me.lucko.bungeeguard.backend.BackendPlugin;
import me.lucko.bungeeguard.backend.TokenStore;
import me.lucko.bungeeguard.backend.listener.AbstractHandshakeListener;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.profile.property.ProfileProperty;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.util.Collection;
import java.util.Iterator;

public class HandshakeListener extends AbstractHandshakeListener {

    public HandshakeListener(BackendPlugin plugin, TokenStore tokenStore) {
        super(plugin, tokenStore);
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

            this.plugin.logWarn("Denying connection from " + connectionDescription + " - reason: " + reason);

            e.setMessage(TextSerializers.FORMATTING_CODE.deserialize(this.invalidTokenKickMessage));
            e.setCancelled(true);
            e.setMessageCancelled(false);
        }
    }
}
