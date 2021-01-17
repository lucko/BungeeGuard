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

package me.lucko.bungeeguard.bungee;

import net.md_5.bungee.ServerConnector;
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
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

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
    private final Property[] justExtraToken;
    private final boolean offline;

    // online mode constructor
    private SpoofedLoginResult(LoginResult oldProfile, String extraToken) {
        super(oldProfile.getId(), oldProfile.getName(), oldProfile.getProperties());
        this.extraToken = extraToken;
        this.justExtraToken = new Property[]{new Property("bungeeguard-token", this.extraToken, "")};
        this.offline = false;
    }

    // offline mode constructor
    private SpoofedLoginResult(String extraToken) {
        super(null, null, new Property[0]);
        this.extraToken = extraToken;
        this.justExtraToken = new Property[]{new Property("bungeeguard-token", this.extraToken, "")};
        this.offline = true;
    }

    @Override
    public Property[] getProperties() {
        // there's no way this is the best way to do this, but idfk
        Class<?> caller = STACK_WALKER.getCallerClass();

        // if the getProperties method is being called by the server connector, include our token in the properties
        if (caller == ServerConnector.class) {
            return addTokenProperty(super.getProperties());
        } else {
            return super.getProperties();
        }
    }

    private Property[] addTokenProperty(Property[] properties) {
        if (properties.length == 0) {
            return this.justExtraToken;
        }

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
