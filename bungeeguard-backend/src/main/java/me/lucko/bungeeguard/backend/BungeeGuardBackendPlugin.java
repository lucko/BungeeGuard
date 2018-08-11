package me.lucko.bungeeguard.backend;

import com.destroystokyo.paper.event.player.PlayerHandshakeEvent;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
public final class BungeeGuardBackendPlugin extends JavaPlugin implements Listener {

    private static final Type PROPERTY_LIST_TYPE = new TypeToken<List<JsonObject>>() {
    }.getType();

    private final Gson gson = new Gson();

    private String noDataKickMessage;
    private String noPropertiesKickMessage;
    private String invalidTokenKickMessage;

    private List<String> allowedTokens;

    @Override
    public void onEnable() {
        getLogger().info("Using Paper PlayerHandshakeEvent");
        getServer().getPluginManager().registerEvents(this, this);

        saveDefaultConfig();
        FileConfiguration config = getConfig();

        noDataKickMessage = colored(config.getString("no-data-kick-message"));
        noPropertiesKickMessage = colored(config.getString("no-properties-kick-message"));
        invalidTokenKickMessage = colored(config.getString("invalid-token-kick-message"));
        allowedTokens = ImmutableList.copyOf(config.getStringList("allowed-tokens"));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHandshake(PlayerHandshakeEvent event) {
        String handshake = event.getOriginalHandshake();
        String[] split = handshake.split("\00");

        if (split.length != 3 && split.length != 4) {
            event.setFailMessage(noDataKickMessage);
            event.setFailed(true);
            return;
        }

        // extract ipforwarding info from the handshake
        String serverHostname = split[0];
        String socketAddressHostname = split[1];
        UUID uniqueId = UUID.fromString(split[2].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

        // doesn't contain any properties - so deny
        if (split.length == 3) {
            getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - No properties were sent in their handshake.");
            event.setFailMessage(noPropertiesKickMessage);
            event.setFailed(true);
            return;
        }

        // deserialize the properties in the handshake
        List<JsonObject> properties = gson.fromJson(split[3], PROPERTY_LIST_TYPE);

        // fail if no properties
        if (properties.isEmpty()) {
            getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - No properties were sent in their handshake.");
            event.setFailMessage(noPropertiesKickMessage);
            event.setFailed(true);
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
            getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - A token was not included in their handshake properties.");
            event.setFailMessage(noPropertiesKickMessage);
            event.setFailed(true);
            return;
        }

        if (!allowedTokens.contains(token)) {
            getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - An invalid token was used: " + token);
            event.setFailMessage(invalidTokenKickMessage);
            event.setFailed(true);
            return;
        }

        // create a new properties array, without our token
        List<JsonObject> newProperties = new ArrayList<>();
        for (JsonObject property : properties) {
            if (property.get("name").getAsString().equals("bungeeguard-token")) {
                continue;
            }

            newProperties.add(property);
        }

        // re-serialize the properties array, without our token this time
        String newPropertiesString = gson.toJson(newProperties, PROPERTY_LIST_TYPE);

        // pass data back to the event
        event.setServerHostname(serverHostname);
        event.setSocketAddressHostname(socketAddressHostname);
        event.setUniqueId(uniqueId);
        event.setPropertiesJson(newPropertiesString);
    }

    private static String colored(String toColoring) {
        return ChatColor.translateAlternateColorCodes('&', toColoring);
    }

}