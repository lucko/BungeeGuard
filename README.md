# 💂 BungeeGuard

BungeeGuard is a plugin-based security/firewall solution for [BungeeCord](https://www.spigotmc.org/wiki/bungeecord/) (and [Velocity](https://velocitypowered.com/)) proxies.

* [Download](https://github.com/lucko/BungeeGuard/releases)
* [Development Builds (Jenkins)](https://ci.lucko.me/job/BungeeGuard/)
* [Install Guide](INSTALLATION.md)

## The problem

BungeeCord installations are **insecure by default**, and require additional firewall rules to be configured (using iptables or otherwise) to prevent malicious users from bypassing the proxy and connecting using any uuid/username they choose.

This is a **well-known issue**, and over the years many (even large) servers have been successfully targeted using this attack.

### The conventional solution

The conventional solution recommended by the BungeeCord author is to configure a firewall rule using iptables or ufw to prevent outside connections to the backend servers.

However, there are two main problems with this:

1. Configuring these firewall rules is complicated, especially for inexperienced users.
   1. Even experienced users sometimes make mistakes or overlook things. Unless the setup is absolutely perfect, rules are prone to being broken during later changes, or reset on system reboot.
2. Users on "shared hosting" do not have access to the underlying system and most likely cannot setup their own firewall rules.

### The BungeeGuard solution

Server admins install BungeeGuard (just an ordinary plugin!) on their proxies and backend servers.

* On the **proxy**, BungeeGuard adds a secret "authentication token" to the login handshake.
* On the **backend** (Spigot etc. server), BungeeGuard checks login handshakes to ensure they contain an allowed authentication token. 

It's really that simple.

## Installation

Installation is very straightforward.

If you have access to the underlying system and are able to setup firewall rules using iptables (or otherwise), I strongly recommend you do so. Then, install BungeeGuard as well.

See [INSTALLATION.md](INSTALLATION.md) for a detailed install guide.

## License

BungeeGuard is licensed and made available under the permissive MIT license. Please see [LICENSE.txt](LICENSE.txt) for more information.

Details about vulnerability reporting & security disclosures can be found in [SECURITY.md](SECURITY.md).
