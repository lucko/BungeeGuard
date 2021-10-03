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

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import me.lucko.bungeeguard.backend.BackendPlugin;
import me.lucko.bungeeguard.backend.TokenStore;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.source.ConsoleSource;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Plugin(
        id = "bungeeguard",
        name = "BungeeGuard",
        version = BungeeGuardVersion.VERSION,
        description = "Plugin which adds a security token to the BungeeCord handshaking protocol",
        authors = "Luck"
)
public final class BungeeGuardSponge implements BackendPlugin, CommandExecutor {

    private final Logger logger;
    private final Path configPath;
    private final TokenStore tokenStore;

    private ConfigurationNode config;

    @Inject
    public BungeeGuardSponge(Logger logger, @DefaultConfig(sharedRoot = true) Path configPath) {
        this.logger = logger;
        this.configPath = configPath;
        this.tokenStore = new TokenStore(this);
    }

    @Listener
    public void onInitialization(GamePreInitializationEvent event) {
        if (!Files.exists(this.configPath)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("bungeeguard.conf")) {
                Files.copy(in, this.configPath);
            } catch (IOException e) {
                throw new RuntimeException("Unable to save default config", e);
            }
        }

        this.reloadConfig();

        this.tokenStore.load();

        CommandSpec command = CommandSpec.builder()
                .description(Text.of("Reloads the configuration"))
                .permission("bungeeguard.reload")
                .arguments(GenericArguments.optional(
                        GenericArguments.literal(Text.of("reload"), "reload")
                ))
                .executor(this)
                .build();

        Sponge.getCommandManager().register(this, command, "bungeeguard");
        Sponge.getEventManager().registerListeners(this, new HandshakeListener(this, this.tokenStore));
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) {
        if (!(src instanceof ConsoleSource)) {
            src.sendMessage(Text.of(TextColors.RED, "Sorry, this command can only be ran from the console."));
            return CommandResult.empty();
        }

        if (!args.hasAny(Text.of("reload"))) {
            src.sendMessage(Text.of(TextColors.RED, "Running BungeeGuard v" + BungeeGuardVersion.VERSION));
            src.sendMessage(Text.of(TextColors.GRAY, "Use '/bungeeguard reload' to reload the configuration."));

            return CommandResult.empty();
        }

        try {
            this.tokenStore.reload();
        } catch (Exception e) {
            this.logger.error("An error occurred while reloading tokens", e);

            src.sendMessage(Text.of(TextColors.RED, "An error occurred while reloading tokens."));

            return CommandResult.empty();
        }

        src.sendMessage(Text.of(TextColors.RED, "BungeeGuard configuration reloaded."));

        return CommandResult.success();
    }

    @Override
    public String getMessage(String key) {
        return this.config.getNode(key).getString();
    }

    @Override
    public List<String> getTokens() {
        try {
            return this.config.getNode("allowed-tokens").getList(TypeToken.of(String.class));
        } catch (ObjectMappingException e) {
            this.logger.error("Unable to load tokens", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void logWarn(String message) {
        this.logger.warn(message);
    }

    @Override
    public void reloadConfig() {
        try {
            HoconConfigurationLoader loader = HoconConfigurationLoader.builder().setPath(this.configPath).build();
            this.config = loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load config", e);
        }
    }
}
