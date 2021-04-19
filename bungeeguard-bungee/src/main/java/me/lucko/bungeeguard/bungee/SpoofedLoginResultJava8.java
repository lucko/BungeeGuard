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

import net.md_5.bungee.connection.LoginResult;

public class SpoofedLoginResultJava8 extends SpoofedLoginResult {
    private static final String SERVER_CONNECTOR = "net.md_5.bungee.ServerConnector";
    private static final String SERVER_CONNECTOR_CONNECTED = "connected";

    // online mode constructor
    public SpoofedLoginResultJava8(LoginResult oldProfile, String extraToken) {
        super(oldProfile, extraToken);
    }

    // offline mode constructor
    public SpoofedLoginResultJava8(String extraToken) {
        super(extraToken);
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
        } else {
            return super.getProperties();
        }
    }
}
