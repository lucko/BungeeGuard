package me.lucko.bungeeguard.backend;

import com.destroystokyo.paper.event.player.PlayerHandshakeEvent;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.mojang.authlib.properties.Property;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Simple plugin which re-implements the BungeeCord handshake protocol, and cancels all attempts
 * which don't contain the special token set by the proxy.
 *
 * The token is included within the player's profile properties, but removed during the handshake.
 */
public class BackendPlugin extends JavaPlugin implements Listener {

    private final Gson gson = new Gson();

    private String noDataKickMessage;
    private String noPropertiesKickMessage;
    private String invalidTokenKickMessage;
    private List<String> allowedTokens;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

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
        Property[] properties = gson.fromJson(split[3], Property[].class);

        // fail if no properties
        if (properties.length == 0) {
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
        Property[] newProperties = new Property[properties.length - 1];
        int i = 0;
        for (Property property : properties) {
            if (property.getName().equals("bungeeguard-token")) {
                continue;
            }

            newProperties[i++] = property;
        }

        // re-serialize the properties array, without our token this time
        String newPropertiesString = gson.toJson(newProperties, Property[].class);

        // pass data back to the event
        e.setServerHostname(serverHostname);
        e.setSocketAddressHostname(socketAddressHostname);
        e.setUniqueId(uniqueId);
        e.setPropertiesJson(newPropertiesString);
    }

}
