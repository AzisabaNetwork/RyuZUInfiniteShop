## SearchableInfiniteShop
Originally created by [Y-RyuZU](https://github.com/Y-RyuZU). 2.x is a rewrite for Paper 1.21.11.

Villager-style shops for Paper 1.21.11:

- Trade list on the left, the selected trade in full on the right; buy x1 / x8 / x64 / as many as possible
- Any number of costs and rewards per trade, any amounts (256 diamonds is one entry); money and console commands
- Limits per player or shared stock, resetting daily / weekly / monthly; success chance; required permission
- Search across all shops by item name, NPC name, MMID or the item in your hand
- NPCs: any vanilla entity (look copied from any entity, mannequins with skins), MythicMobs mobs, Citizens NPCs, or a block
- Editing in GUIs and dialogs, change history with restore, trades/shops as items
- Shop definitions in YAML; trade counts and logs in SQLite or MariaDB

1.x data in the plugin folder is migrated automatically on first start (the old files are kept in `legacy-1.x/`).

## Credits
- 1.x borrowed commandlib codes from [RyuZUCommandsGenerator](https://github.com/Y-RyuZU/RyuZUCommandsGenerator)
