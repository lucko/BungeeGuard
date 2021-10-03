package me.lucko.bungeeguard.backend;

import java.util.List;

public interface BackendPlugin {

    String getMessage(String key);

    List<String> getTokens();

    void logWarn(String message);

    void reloadConfig();
}
