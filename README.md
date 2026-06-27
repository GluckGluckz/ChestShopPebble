# PebbleShop

PebbleShop is Pebble Quest's player chest shop plugin. It is based on EzChestShop, but the public identity, default configuration, economy path, commands, and player-facing language are being rebuilt for the Pebble plugin suite.

## What it does

- Lets players create market stalls by placing a sign on or next to a supported container and typing `[shop]` on the sign. `[sign]` is still accepted as a compatibility alias.
- New sign-created shops start empty and safe: buying and selling are disabled until the owner chooses the shop item in the GUI and sets prices.
- Existing shops can be given a new physical shop sign the same way: place a sign on or next to the shop container and type `[shop]`.
- Shop removal is sign-first too: owners/admins remove a shop by breaking its linked shop sign. `/pshop create` and `/pshop remove` are intentionally disabled for players.
- Supports player shops, admin shops, buy/sell toggles, shop admins, shared income, transaction logs, custom hologram messages, and offline profit reports.
- Keeps the internal `/ecs` command key for inherited code compatibility while promoting Pebble aliases like `/pshop`, `/ps`, `/pebbleshop`, and `/pebblestore`.
- Uses PebbleCore Cash as the economy source, so shop purchases, sales, and shared income use the same active-profile wallet as the rest of Pebble Quest.
- Supports SQLite by default and MySQL for larger live servers.
- Keeps optional hooks for supported server plugins such as PlaceholderAPI, WorldGuard, Towny, CMI, WildChests, and Multiverse-Core.

## Pebble Quest defaults

The default config has been adjusted for Pebble Quest:

- Plugin name: `PebbleShop`
- Hard dependency: `PebbleCore`, so PebbleCore loads first and its Cash economy is available before shops start
- Default database table prefix: `pshop_`
- Pebble-branded signs, GUI text, Discord embeds, and profit reports
- Pebble-style command aliases and player-facing messages
- Upstream EzChestShop update checks disabled
- Legacy Vault, Slimefun, and AdvancedRegionMarket runtime dependencies removed from the Pebble build path

## Economy integration

PebbleShop resolves PebbleCore reflectively at runtime, similar to PebbleFish. The shop transaction code is bridged into PebbleCore Cash through PebbleShop's own economy abstraction, so the server does not need Vault installed for PebbleShop to load.

The intended runtime path is:

```text
PebbleShop transaction -> PebbleShop economy bridge -> PebbleCore active profile Cash
```

## Runtime target

PebbleShop is configured for Pebble Quest's Paper `26.1.2` target. Compatibility shims are kept for inherited code paths where needed, but the Pebble build should not depend on CraftBukkit/NMS version packages.

## Build

```bash
mvn -U clean package
```

GitHub Actions builds the jar automatically on `main` pushes and uploads it as the `PebbleShop` artifact. The release workflow publishes versioned jars to the GitHub Releases tab and then bumps the repo to the next `-SNAPSHOT` version.

## Notes

Internal Java package names are intentionally left as `me.deadlight.ezchestshop` during this rebrand pass to avoid unnecessary migration risk. The public plugin identity, commands, Maven artifacts, default config, dependency path, economy integration, and player-facing branding are PebbleShop.
