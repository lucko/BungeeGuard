package me.lucko.bungeeguard.backend;

import lombok.RequiredArgsConstructor;

import com.google.gson.reflect.TypeToken;

import protocolsupport.api.events.PlayerPropertiesResolveEvent.ProfileProperty;
import protocolsupport.protocol.utils.spoofedata.SpoofedData;
import protocolsupport.protocol.utils.spoofedata.SpoofedDataParser;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@RequiredArgsConstructor
public class ProtocolSupportHack implements Function<String, SpoofedData> {
    private static final Type PROPERTY_LIST_TYPE = new TypeToken<List<ProfileProperty>>(){}.getType();

    public static void hook(BackendPlugin plugin) throws Exception {
        Field parsers = SpoofedDataParser.class.getDeclaredField("parsers");
        parsers.setAccessible(true);

        //noinspection unchecked
        List<Function<String, SpoofedData>> parsersList = (List) parsers.get(null);
        parsersList.clear();

        parsersList.add(new ProtocolSupportHack(plugin));
    }

    private final BackendPlugin plugin;

    @Override
    public SpoofedData apply(String handshake) {
        String[] split = handshake.split("\00");

        if (split.length != 3 && split.length != 4) {
            return null;
        }

        // extract ipforwarding info from the handshake
        String serverHostname = split[0];
        String socketAddressHostname = split[1];
        UUID uniqueId = UUID.fromString(split[2].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

        // doesn't contain any properties - so deny
        if (split.length == 3) {
            plugin.getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - No properties were sent in their handshake.");
            return null;
        }

        // deserialize the properties in the handshake
        List<ProfileProperty> properties = plugin.getGson().fromJson(split[3], PROPERTY_LIST_TYPE);

        // fail if no properties
        if (properties.isEmpty()) {
            plugin.getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - No properties were sent in their handshake.");
            return null;
        }

        String token = null;

        // try to find the token
        for (ProfileProperty property : properties) {
            if (property.getName().equals("bungeeguard-token")) {
                token = property.getValue();
                break;
            }
        }

        // deny connection if no token was provided
        if (token == null) {
            plugin.getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - A token was not included in their handshake properties.");
            return null;
        }

        if (!plugin.getAllowedTokens().contains(token)) {
            plugin.getLogger().warning("Denied connection from " + uniqueId + " @ " + socketAddressHostname + " - An invalid token was used: " + token);
            return null;
        }

        // create a new properties array, without our token
        List<ProfileProperty> newProperties = new ArrayList<>();
        for (ProfileProperty property : properties) {
            if (property.getName().equals("bungeeguard-token")) {
                continue;
            }

            newProperties.add(property);
        }

        // re-serialize the properties array, without our token this time
        ProfileProperty[] newPropertiesArray = newProperties.toArray(new ProfileProperty[newProperties.size()]);

        // return the spoofed data
        return new SpoofedData(serverHostname, socketAddressHostname, uniqueId, newPropertiesArray);
    }
}
