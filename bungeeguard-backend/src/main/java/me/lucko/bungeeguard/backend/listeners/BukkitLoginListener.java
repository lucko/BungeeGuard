package me.lucko.bungeeguard.backend.listeners;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.lucko.bungeeguard.backend.BungeeGuardBackendPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Collection;
import java.util.logging.Level;

public class BukkitLoginListener implements Listener {

    private final BungeeGuardBackendPlugin plugin;

    public BukkitLoginListener(BungeeGuardBackendPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLogin(PlayerLoginEvent e) {
        Player player = e.getPlayer();
        String hostAddress = e.getAddress().getHostAddress();

        GameProfile gameProfile;

        try {
            // get the GameProfile of the player
            gameProfile = (GameProfile) player.getClass().getDeclaredMethod("getProfile").invoke(player);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING, "Denied connection from " + player.getUniqueId() + " @ " + hostAddress + " - Unable to get GameProfile.", ex);
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, plugin.getNoDataKickMessage());
            return;
        }

        Collection<Property> tokens = gameProfile.getProperties().get("bungeeguard-token");

        // deny connection if no token was provided
        if (tokens.isEmpty()) {
            plugin.logDeniedConnection(hostAddress, player.getUniqueId(), "A token was not included in their GameProfile properties.");
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, plugin.getNoDataKickMessage());
            return;
        }

        Property tokenProperty = tokens.iterator().next();
        String token = tokenProperty.getValue();

        if (plugin.denyLogin(hostAddress, player.getUniqueId(), token)) {
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, plugin.getInvalidTokenKickMessage());
            return;
        }

        // remove our property
        gameProfile.getProperties().removeAll("bungeeguard-token");
    }
}
