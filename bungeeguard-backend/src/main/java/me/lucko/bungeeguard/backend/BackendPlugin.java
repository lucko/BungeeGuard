package me.lucko.bungeeguard.backend;

import lombok.Getter;

import com.destroystokyo.paper.event.player.PlayerHandshakeEvent;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.properties.Property;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simple plugin which re-implements the BungeeCord handshake protocol, and cancels all attempts
 * which don't contain the special token set by the proxy.
 *
 * The token is included within the player's profile properties, but removed during the handshake.
 */
public class BackendPlugin extends JavaPlugin implements Listener {
    private static final Type PROPERTY_LIST_TYPE = new TypeToken<List<Property>>(){}.getType();

    @Getter
    private final Gson gson = new Gson();

    private String noDataKickMessage;
    private String noPropertiesKickMessage;
    private String invalidTokenKickMessage;

    @Getter
    private List<String> allowedTokens;

    @Override
    public void onEnable() {

        if (getServer().getPluginManager().isPluginEnabled("ProtocolSupport")) {
            getLogger().info("Using ProtocolSupport hack");
            try {
                ProtocolSupportHack.hook(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            getLogger().info("Using Paper PlayerHandshakeEvent");
            getServer().getPluginManager().registerEvents(this, this);
        }

        saveDefaultConfig();
        FileConfiguration config = getConfig();

        noDataKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("no-data-kick-message"));
        noPropertiesKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("no-properties-kick-message"));
        invalidTokenKickMessage = ChatColor.translateAlternateColorCodes('&', config.getString("invalid-token-kick-message"));
        allowedTokens = ImmutableList.copyOf(config.getStringList("allowed-tokens"));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHandshake(PlayerHandshakeEvent e) {
        String handshake = e.getOriginalHandshake();
        String[] split = handshake.split("\00");

        if (split.length != 3 && split.length != 4) {
            e.setFailMessage(noDataKickMessage);
            e.setFailed(true);
            return;
        }

        // extract ipforwarding info from the handshake
        String serverHostname = split[0];
        String socketAddressHostname = split[1];
        UUID uniqueId = UUID.fromString(split[2].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

        // doesn't contain any properties - so deny
        if (split.length == 3) {
            getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - No properties were sent in their handshake.");
            e.setFailMessage(noPropertiesKickMessage);
            e.setFailed(true);
            return;
        }

        // deserialize the properties in the handshake
        List<Property> properties = gson.fromJson(split[3], PROPERTY_LIST_TYPE);

        // fail if no properties
        if (properties.isEmpty()) {
            getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - No properties were sent in their handshake.");
            e.setFailMessage(noPropertiesKickMessage);
            e.setFailed(true);
            return;
        }

        String token = null;

        // try to find the token
        for (Property property : properties) {
            if (property.getName().equals("bungeeguard-token")) {
                token = property.getValue();
                break;
            }
        }

        // deny connection if no token was provided
        if (token == null) {
            getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - A token was not included in their handshake properties.");
            e.setFailMessage(noPropertiesKickMessage);
            e.setFailed(true);
            return;
        }

        if (!allowedTokens.contains(token)) {
            getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - An invalid token was used: " + token);
            e.setFailMessage(invalidTokenKickMessage);
            e.setFailed(true);
            return;
        }

        // create a new properties array, without our token
        List<Property> newProperties = new ArrayList<>();
        for (Property property : properties) {
            if (property.getName().equals("bungeeguard-token")) {
                continue;
            }

            newProperties.add(property);
        }

        // re-serialize the properties array, without our token this time
        String newPropertiesString = gson.toJson(newProperties, PROPERTY_LIST_TYPE);

        // pass data back to the event
        e.setServerHostname(serverHostname);
        e.setSocketAddressHostname(socketAddressHostname);
        e.setUniqueId(uniqueId);
        e.setPropertiesJson(newPropertiesString);
    }

}
