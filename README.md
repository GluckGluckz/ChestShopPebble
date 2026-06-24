# PebbleShop

PebbleShop is Pebble Quest's lightweight player chest shop plugin, forked from EzChestShop and rebranded for the Pebble plugin suite.

## What it does

- Lets players create chest shops by looking at a supported container and running `/pshop create <buy> <sell>`.
- Supports player shops, admin shops, buy/sell toggles, shop admins, shared income, transaction logs, and custom hologram messages.
- Keeps the original `/ecs` compatibility command while adding Pebble-friendly aliases like `/pshop`, `/ps`, and `/pebbleshop`.
- Supports SQLite by default and MySQL for larger servers.
- Includes optional integrations for Vault, WorldGuard, Slimefun, PlaceholderAPI, Towny, and AdvancedRegionMarket.

## Pebble Quest defaults

The default config has been adjusted for Pebble Quest:

- Plugin name: `PebbleShop`
- Default database table prefix: `pshop_`
- Upstream EzChestShop update checks disabled
- Pebble-branded Discord embeds
- Pebble-style command aliases and player-facing messages

## Build

```bash
mvn clean package
```

The distribution jar is produced from the `dist` module under `target/`.

## Notes

Internal Java package names are intentionally left as `me.deadlight.ezchestshop` in this rebrand pass to keep the fork stable and avoid unnecessary migration risk. The public plugin identity, commands, Maven artifacts, default config, and player-facing branding are PebbleShop.
