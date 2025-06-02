package me.lucko.bungeeguard.spigot.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.handshaking.client.WrapperHandshakingClientHandshake;
import me.lucko.bungeeguard.backend.BungeeGuardBackend;
import me.lucko.bungeeguard.backend.TokenStore;
import me.lucko.bungeeguard.backend.listener.AbstractHandshakeListener;
import me.lucko.bungeeguard.spigot.BungeeCordHandshake;
import me.lucko.bungeeguard.spigot.BungeeGuardBackendPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.logging.Level;

/**
 * A handshake listener using PacketEvents.
 */
public class PacketEventsHandshakeListener extends AbstractHandshakeListener {

    private static final int HANDSHAKE_PACKET_ID = 0x00; // ID for Handshake packets

    public PacketEventsHandshakeListener(BungeeGuardBackend plugin, TokenStore tokenStore) {
        super(plugin, tokenStore);
    }

    public void registerListener() {
        PacketEvents.getAPI().getEventManager().registerListener(new HandshakeListener(), PacketListenerPriority.HIGHEST);
    }

    private final class HandshakeListener implements PacketListener {

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketId() != HANDSHAKE_PACKET_ID) {
                return;
            }

            Player player = event.getPlayer();

            WrapperHandshakingClientHandshake wrapperHandshakingClientHandshake = new WrapperHandshakingClientHandshake(event);
            if (!wrapperHandshakingClientHandshake.getIntention().equals(WrapperHandshakingClientHandshake.ConnectionIntention.LOGIN)) {
                return;
            }

            String handshake = wrapperHandshakingClientHandshake.readString(0);
            BungeeCordHandshake decoded = BungeeCordHandshake.decodeAndVerify(handshake, PacketEventsHandshakeListener.this.tokenStore);

            if (decoded instanceof BungeeCordHandshake.Fail) {
                String ip = "null";
                InetSocketAddress address = player.getAddress();
                if (address != null) {
                    ip = address.getHostString();
                    if (ip.length() > 15) {
                        ip = BungeeCordHandshake.encodeBase64(ip);
                    }
                }
                BungeeCordHandshake.Fail fail = (BungeeCordHandshake.Fail) decoded;
                BungeeGuardBackendPlugin.getInstance().getLogger().warning("Denying connection from " + ip + " - " + fail.describeConnection() + " - reason: " + fail.reason().name());

                String kickMessage;
                if (fail.reason() == BungeeCordHandshake.Fail.Reason.INVALID_HANDSHAKE) {
                    kickMessage = PacketEventsHandshakeListener.this.noDataKickMessage;
                } else {
                    kickMessage = PacketEventsHandshakeListener.this.invalidTokenKickMessage;
                }

                try {
                    closeConnection(player, kickMessage);
                } catch (Exception e) {
                    BungeeGuardBackendPlugin.getInstance().getLogger().log(Level.SEVERE, "An error occurred while closing connection for " + player, e);
                }

                // Prevent further processing by modifying the packet
                wrapperHandshakingClientHandshake.writeString("null");
                return;
            }

            // Successfully decoded and verified the handshake
            BungeeCordHandshake.Success data = (BungeeCordHandshake.Success) decoded;
            wrapperHandshakingClientHandshake.writeString(data.encode());
        }
    }

    private static void closeConnection(Player player, String kickMessage) {
        player.kickPlayer(kickMessage);
    }
}
