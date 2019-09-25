package me.lucko.bungeeguard.proxy;

import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.connection.LoginResult;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Extension of {@link LoginResult} which returns a modified Property array when
 * {@link #getProperties()} is called by the ServerConnector implementation.
 *
 * To achieve this, the stack trace is analyzed. This is kinda crappy, but is the only way
 * to modify the properties without leaking the token to other clients via the tablist.
 */
class SpoofedLoginResult extends LoginResult {
    private static final String SERVER_CONNECTOR = "net.md_5.bungee.ServerConnector";
    private static final String SERVER_CONNECTOR_CONNECTED = "connected";

    private static final Field PROFILE_FIELD;
    static {
        try {
            PROFILE_FIELD = InitialHandler.class.getDeclaredField("loginProfile");
            PROFILE_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final String extraToken;
    private final boolean offline;

    // online mode constructor
    private SpoofedLoginResult(LoginResult oldProfile, String extraToken) {
        super(oldProfile.getId(), oldProfile.getName(), oldProfile.getProperties());
        this.extraToken = extraToken;
        this.offline = false;
    }

    // offline mode constructor
    private SpoofedLoginResult(String extraToken) {
        super(null, null, new Property[0]);
        this.extraToken = extraToken;
        this.offline = true;
    }

    @Override
    public Property[] getProperties() {
        // there's no way this is the best way to do this, but idfk
        StackTraceElement[] trace = new Exception().getStackTrace();

        if (trace.length < 2) {
            return super.getProperties();
        }

        StackTraceElement callLocation = trace[1];

        // if the getProperties method is being called by the server connector, include our token in the properties
        if (callLocation.getClassName().equals(SERVER_CONNECTOR) && callLocation.getMethodName().equals(SERVER_CONNECTOR_CONNECTED)) {
            return addTokenProperty(super.getProperties());
        }

        return super.getProperties();
    }

    private Property[] addTokenProperty(Property[] properties) {
        Property[] newProperties = Arrays.copyOf(properties, properties.length + 1);
        newProperties[properties.length] = new Property("bungeeguard-token", this.extraToken, "");
        return newProperties;
    }

    @Override
    public String getId() {
        if (this.offline) {
            throw new RuntimeException("getId called for offline variant of SpoofedLoginResult");
        }
        return super.getId();
    }

    @Override
    public String getName() {
        if (this.offline) {
            throw new RuntimeException("getId called for offline variant of SpoofedLoginResult");
        }
        return super.getId();
    }

    static void inject(InitialHandler handler, String token) {
        LoginResult profile = handler.getLoginProfile();
        LoginResult newProfile;

        // profile is null for offline mode servers
        if (profile == null) {
            newProfile = new SpoofedLoginResult(token); // offline mode constructor
        } else {
            newProfile = new SpoofedLoginResult(profile, token); // online mode constructor
        }

        try {
            PROFILE_FIELD.set(handler, newProfile);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
