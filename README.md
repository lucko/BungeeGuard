# BungeeGuard

BungeeGuard is a pair of plugins which intercept the BungeeCord handshake protocol, and allow backend servers to verify whether players connected from a trusted proxy.

* On the proxy, BungeeGuard inserts a special authentication token into the profile data sent to the backend server when a player tries to connect.
* On the backend server, BungeeGuard re-implements the BungeeCord handshake protocol, and denies connections which do not contain an allowed token.

This means that even if your backend server is not firewalled, malicious users will not be able to spoof logins without knowing one of your allowed tokens.