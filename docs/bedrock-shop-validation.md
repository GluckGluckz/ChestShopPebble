# Bedrock shop validation

PebbleShop's multi-item interface is designed around normal click/tap actions only. Customer listings open a dedicated transaction screen with separate buttons for buying one, buying a stack, selling one, and selling a stack. Staff management also uses separate Add and Remove buttons.

## Geyser test checklist

- Open a normal player shop from a Bedrock client.
- Tap two metadata-distinct listings, such as differently named books.
- Buy one item and a stack through the dedicated buttons.
- Sell one item and a stack through the dedicated buttons.
- Confirm no customer action requires right-click or shift-click.
- Add and remove shop staff using the separate staff controls and chat prompts.
- Add and replace listings while holding the template item in the main hand.
- Confirm prices, toggles, stock, double-chest capacity, and restart persistence.
- Repeat the trade flow on an admin shop.

The nested validation pull request compiles the complete feature branch before the main multi-item pull request is merged.
