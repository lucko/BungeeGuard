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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Encapsulates a BungeeCord "ip forwarding" handshake result.
 */
public class BungeeCordHandshake {

    /** The name of the BungeeGuard auth token. */
    private static final String BUNGEEGUARD_TOKEN_NAME = "bungeeguard-token";
    /** The key used to define the name of properties in the handshake. */
    private static final String PROPERTY_NAME_KEY = "name";
    /** The key used to define the value of properties in the handshake. */
    private static final String PROPERTY_VALUE_KEY = "value";

    /** Shared Gson instance. */
    private static final Gson GSON = new Gson();
    /** The type of the property list in the handshake. */
    private static final Type PROPERTY_LIST_TYPE = new TypeToken<List<JsonObject>>(){}.getType();

    /**
     * Decodes a BungeeCord handshake, additionally ensuring it contains a
     * BungeeGuard token allowed by the {@link TokenStore}.
     *
     * @param handshake the handshake data
     * @param tokenStore the token store
     * @return the handshake result
     */
    public static BungeeCordHandshake decodeAndVerify(String handshake, TokenStore tokenStore) {
        try {
            return decodeAndVerify0(handshake, tokenStore);
        } catch (Exception e) {
            new Exception("Failed to decode handshake", e).printStackTrace();
            return new Fail(Fail.Reason.INVALID_HANDSHAKE, handshake);
        }
    }

    private static BungeeCordHandshake decodeAndVerify0(String handshake, TokenStore tokenStore) throws Exception {
        String[] split = handshake.split("\00");
        if (split.length != 3 && split.length != 4) {
            return new Fail(Fail.Reason.INVALID_HANDSHAKE, handshake);
        }

        String serverHostname = split[0];
        String socketAddressHostname = split[1];
        UUID uniqueId = UUID.fromString(split[2].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

        String connectionDescription = uniqueId + " @ " + socketAddressHostname;

        if (split.length == 3) {
            return new Fail(Fail.Reason.NO_TOKEN, connectionDescription);
        }

        List<JsonObject> properties = new LinkedList<>(GSON.fromJson(split[3], PROPERTY_LIST_TYPE));
        if (properties.isEmpty()) {
            return new Fail(Fail.Reason.NO_TOKEN, connectionDescription);
        }

        String bungeeGuardToken = null;
        for (Iterator<JsonObject> iterator = properties.iterator(); iterator.hasNext(); ) {
            JsonObject property = iterator.next();
            if (property.get(PROPERTY_NAME_KEY).getAsString().equals(BUNGEEGUARD_TOKEN_NAME)) {
                bungeeGuardToken = property.get(PROPERTY_VALUE_KEY).getAsString();
                iterator.remove();
            }
        }

        if (bungeeGuardToken == null) {
            return new Fail(Fail.Reason.NO_TOKEN, connectionDescription);
        }

        if (!tokenStore.isAllowed(bungeeGuardToken)) {
            return new Fail(Fail.Reason.INCORRECT_TOKEN, connectionDescription + " - " + bungeeGuardToken);
        }

        String newPropertiesString = GSON.toJson(properties, PROPERTY_LIST_TYPE);
        return new Success(serverHostname, socketAddressHostname, uniqueId, newPropertiesString);
    }

    /**
     * Encapsulates a successful handshake.
     */
    public static final class Success extends BungeeCordHandshake {
        private final String serverHostname;
        private final String socketAddressHostname;
        private final UUID uniqueId;
        private final String propertiesJson;

        Success(String serverHostname, String socketAddressHostname, UUID uniqueId, String propertiesJson) {
            this.serverHostname = serverHostname;
            this.socketAddressHostname = socketAddressHostname;
            this.uniqueId = uniqueId;
            this.propertiesJson = propertiesJson;
        }

        public String serverHostname() {
            return this.serverHostname;
        }

        public String socketAddressHostname() {
            return this.socketAddressHostname;
        }

        public UUID uniqueId() {
            return this.uniqueId;
        }

        public String propertiesJson() {
            return this.propertiesJson;
        }

        /**
         * Re-encodes this handshake to the format used by BungeeCord.
         *
         * @return an encoded string for the handshake
         */
        public String encode() {
            return this.serverHostname + "\00" + this.socketAddressHostname + "\00" + this.uniqueId + "\00" + this.propertiesJson;
        }
    }

    /**
     * Encapsulates an unsuccessful handshake.
     */
    public static final class Fail extends BungeeCordHandshake {
        private final Reason reason;
        private final String connectionDescription;

        Fail(Reason reason, String connectionDescription) {
            this.reason = reason;
            this.connectionDescription = connectionDescription;
        }

        public Reason reason() {
            return this.reason;
        }

        public String describeConnection() {
            return this.connectionDescription;
        }

        public enum Reason {
            INVALID_HANDSHAKE, NO_TOKEN, INCORRECT_TOKEN
        }
    }

}
