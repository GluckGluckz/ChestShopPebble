# PebbleShop

PebbleShop is Pebble Quest's lightweight player chest shop plugin, forked from EzChestShop and rebranded for the Pebble plugin suite.

## What it does

- Lets players create chest shops by looking at a supported container and running `/pshop create <buy> <sell>`.
- Supports player shops, admin shops, buy/sell toggles, shop admins, shared income, transaction logs, and custom hologram messages.
- Keeps the original `/ecs` compatibility command while adding Pebble-friendly aliases like `/pshop`, `/ps`, and `/pebbleshop`.
- Uses PebbleCore Cash as the primary economy so shop purchases, sales, and shared income flow through the same Pebble Quest profile wallet.
- Supports SQLite by default and MySQL for larger servers.
- Includes optional integrations for Vault, WorldGuard, Slimefun, PlaceholderAPI, Towny, and AdvancedRegionMarket.

## Pebble Quest defaults

The default config has been adjusted for Pebble Quest:

- Plugin name: `PebbleShop`
- Hard dependency: `PebbleCore`, so PebbleCore loads first and its Cash economy is available before shops start
- Default database table prefix: `pshop_`
- Upstream EzChestShop update checks disabled
- Pebble-branded Discord embeds
- Pebble-style command aliases and player-facing messages

## Economy integration

PebbleShop resolves PebbleCore reflectively at runtime, similar to PebbleFish. The plugin exposes PebbleCore Cash through a small Vault-compatible adapter, which lets the original chest shop transaction code keep using balance/deposit/withdraw calls while routing the money into PebbleCore's active-profile Cash balance.

Vault remains a soft fallback in the metadata, but PebbleCore Cash is preferred whenever it is available.

## Runtime target

PebbleShop is configured for Pebble Quest's Paper `26.1.2` target and keeps compatibility gates for the legacy upstream versions `1.16.5`, `1.17.1`, `1.18.2`, `1.19.4`, and `1.20.4`.

## Build

```bash
mvn clean package
```

The distribution jar is produced from the `dist` module under `target/`.

GitHub Actions builds the jar automatically on `main` pushes and uploads it as the `PebbleShop` artifact. The release workflow publishes versioned jars to the GitHub Releases tab and then bumps the repo to the next `-SNAPSHOT` version.

## Notes

Internal Java package names are intentionally left as `me.deadlight.ezchestshop` in this rebrand pass to keep the fork stable and avoid unnecessary migration risk. The public plugin identity, commands, Maven artifacts, default config, economy dependency, and player-facing branding are PebbleShop.
