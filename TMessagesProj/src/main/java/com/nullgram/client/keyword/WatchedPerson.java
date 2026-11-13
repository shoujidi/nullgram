// SPDX-License-Identifier: GPL-3.0-only
package com.nullgram.client.keyword;

/** Stable identity in one Telegram account; no nickname or signature matching. */
public final class WatchedPerson {
    public static final int PRIVATE = 1, GROUP = 2, CHANNEL = 4, ALL = 7;
    public final long id;
    public final String name;
    public final int scopes;
    public final boolean online, privateRead;
    public WatchedPerson(long id, String name, int scopes, boolean online) {
        this(id, name, scopes, online, false);
    }
    public WatchedPerson(long id, String name, int scopes, boolean online, boolean privateRead) {
        if (id <= 0 || name == null || name.length() > 256 || scopes < 0 || (scopes & ~ALL) != 0)
            throw new IllegalArgumentException("WATCHED_PERSON");
        this.id = id; this.name = name; this.scopes = scopes; this.online = online; this.privateRead = privateRead;
    }
}
