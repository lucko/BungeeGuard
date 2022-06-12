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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.temporary.MinimalInjector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import me.lucko.bungeeguard.backend.BungeeGuardBackend;
import me.lucko.bungeeguard.backend.TokenStore;
import me.lucko.bungeeguard.backend.listener.AbstractHandshakeListener;
import me.lucko.bungeeguard.spigot.BungeeCordHandshake;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.logging.Level;

/**
 * A handshake listener using ProtocolLib.
 */
public class ProtocolHandshakeListener extends AbstractHandshakeListener {

    public ProtocolHandshakeListener(BungeeGuardBackend plugin, TokenStore tokenStore) {
        super(plugin, tokenStore);
    }

    public void registerAdapter(Plugin plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new Adapter(plugin));
    }

    private final class Adapter extends PacketAdapter {
        Adapter(Plugin plugin) {
            super(plugin, ListenerPriority.LOWEST, PacketType.Handshake.Client.SET_PROTOCOL);
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            PacketContainer packet = event.getPacket();

            // only handle the LOGIN phase
            PacketType.Protocol state = packet.getProtocols().read(0);
            if (state != PacketType.Protocol.LOGIN) {
                return;
            }

            String handshake = packet.getStrings().read(0);
            BungeeCordHandshake decoded = BungeeCordHandshake.decodeAndVerify(handshake, ProtocolHandshakeListener.this.tokenStore);

            if (decoded instanceof BungeeCordHandshake.Fail) {
                String ip = "null";
                Player player = event.getPlayer();
                InetSocketAddress address = player.getAddress();
                if (address != null) {
                    ip = address.getHostString();
                    if (ip.length() > 15) {
                        ip = BungeeCordHandshake.encodeBase64(ip);
                    }
                }
                BungeeCordHandshake.Fail fail = (BungeeCordHandshake.Fail) decoded;
                this.plugin.getLogger().warning("Denying connection from " + ip + " - " + fail.describeConnection() + " - reason: " + fail.reason().name());

                String kickMessage;
                if (fail.reason() == BungeeCordHandshake.Fail.Reason.INVALID_HANDSHAKE) {
                    kickMessage = ProtocolHandshakeListener.this.noDataKickMessage;
                } else {
                    kickMessage = ProtocolHandshakeListener.this.invalidTokenKickMessage;
                }

                try {
                    closeConnection(player, kickMessage);
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.SEVERE, "An error occurred while closing connection for " + player, e);
                }

                // just in-case the connection didn't close, screw up the hostname
                // so Spigot can't pick up anything that might've been spoofed in nms.HandshakeListener
                packet.getStrings().write(0, "null");

                return;
            }

            // great, handshake was decoded and verified successfully.
            // we can re-encode the handshake now so Spigot can pick up the spoofed stuff.
            BungeeCordHandshake.Success data = (BungeeCordHandshake.Success) decoded;
            packet.getStrings().write(0, data.encode());
        }
    }

    private static void closeConnection(Player player, String kickMessage) throws Exception {
        WrappedChatComponent component = WrappedChatComponent.fromJson(ComponentSerializer.toString(TextComponent.fromLegacyText(kickMessage)));

        PacketContainer packet = new PacketContainer(PacketType.Login.Server.DISCONNECT);
        packet.getModifier().writeDefaults();
        packet.getChatComponents().write(0, component);

        // send custom disconnect message to client
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);

        // call PlayerConnection#disconnect to ensure the underlying socket is closed
        MinimalInjector injector = TemporaryPlayerFactory.getInjectorFromPlayer(player);
        injector.disconnect("");
    }

}
