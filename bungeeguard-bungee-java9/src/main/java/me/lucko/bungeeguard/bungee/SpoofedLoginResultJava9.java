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
import net.md_5.bungee.connection.LoginResult;

public class SpoofedLoginResultJava9 extends SpoofedLoginResult {
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    // online mode constructor
    public SpoofedLoginResultJava9(LoginResult oldProfile, String extraToken) {
        super(oldProfile, extraToken);
    }

    // offline mode constructor
    public SpoofedLoginResultJava9(String extraToken) {
        super(extraToken);
    }

    @Override
    public LoginResult.Property[] getProperties() {
        Class<?> caller = STACK_WALKER.getCallerClass();

        // if the getProperties method is being called by the server connector, include our token in the properties
        if (caller == ServerConnector.class) {
            return addTokenProperty(super.getProperties());
        } else {
            return super.getProperties();
        }
    }
}
