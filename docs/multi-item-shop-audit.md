# Multi-item shop audit

The multi-item and Bedrock-safe storefront was audited before merging to `main`.

## Hardened during the audit

- GUI trades resolve the current listing, current price, owner, and shop flags at click time.
- View-only administrators can no longer open or modify real shop stock.
- Shop staff can manage listings and stock, but only the owner or a full shop administrator can change financial settings or staff membership.
- Chat prompts move all Bukkit/PDC access back to the server thread and revalidate permissions when completed.
- Player and admin-shop transactions validate finite totals and order economy/item mutations with rollback on failed removals.
- Multi-item payloads are preserved when shulker shops are broken, moved, and placed again.
- Persisted listing payloads reject duplicate IDs, duplicate item identities, invalid capacity, and non-finite prices.
- Shop interaction respects earlier event cancellations and no longer mutates events at MONITOR priority.

## Required live smoke test

1. Test two differently named books with different prices from Java and Bedrock.
2. Test Buy 1, Buy Stack, Sell 1, and Sell Stack.
3. Remove a staff member while they have a price prompt open and confirm completion is denied.
4. Leave an old trade GUI open, change its price, then confirm the new price is charged.
5. Break and replace a multi-listing shulker shop and confirm all listings remain.
6. Restart with a double-chest shop and open either half.

## Follow-up verification

- Stale management GUIs revalidate listing or settings permissions before every mutation.
- Inventory additions and economy deposits now expose failure and trigger rollback.
- Malformed staff UUID data is ignored safely instead of breaking shop access.
- Prompt state is cleaned on disconnect and rapid duplicate prompt input cannot leak into public chat.

## Final polish

- Global settings derive their new value from current PDC state, so stale GUIs cannot reverse another administrator's change.
- Listing toggles and removal now surface persistence failures instead of presenting false success.
- Editing an existing listing no longer silently re-enables a direction that the owner globally disabled.
- Transactions reject null/AIR templates, guard missing economy providers, and parse shared-income metadata defensively.
