# UnifiedEnchantments 1.0 — crafted runes with configurable proc chances

Paper 26.3 / Java 25. Adds **three custom effects on vanilla weapons**, implemented via item PersistentDataContainer rather than modifying Paper's native enchantment registry. No NMS or client mods.

## Survival craft

Craft a rune book (shapeless):
- **Vampirism:** Book + Ghast Tear + Diamond + Gold Ingot
- **Frost:** Book + Blue Ice + Amethyst Shard + Lapis Lazuli
- **Ember:** Book + Blaze Rod + Magma Cream + Gold Ingot

To apply, hold a weapon (sword/axe/spear/trident/mace) in your main hand and a rune book in the offhand, then right-click. The rune book is consumed and the weapon gets one level, up to level 3. Settings in config.yml control chances and effect duration.

### Effects

**Vampirism** heals the attacker after a successful melee hit, chance 8% × level, heal 1 health point × level (capped); **Frost** applies Slowness I for 3 seconds with 10% × level; **Ember** ignites the hit mob for 2.5 seconds with 10% × level. PvP effects are off by default. A hit may trigger more than one if a sword has multiple runes.

Command `/uenchant info` displays info; `/uenchant give <vampirism|frost|ember> [player]` creates a rune book for admins; `/uenchant reload` re-reads chances; `/uenchant audit` reports duplicate tagged item IDs across **currently online** players' inventories without deleting any items.

## Anti-dupe compatibility and limits

Each rune-enhanced weapon gets a unique PDC UUID marker; /uenchant audit reports two or more currently online inventory items with the same ID. It does **not** prove a dupe exploit happened and does not automatically destroy items. It cannot catch all duplication (offline items, containers, serialization exploits, UUID clones); use UnifiedProtection and up-to-date Paper engine fixes. Storing no hidden server-side item awards and never awarding drops avoids introducing extra loot duplication paths.

Custom runes are NOT vanilla registered enchantments; they don't appear in the vanilla enchanting table, can't combine via the anvil and require this plugin to function. Existing unenchanted weapons and enchants remain unaffected; lore/IDs are separate PDC metadata. Effects use standard Bukkit API and no chunk scans.

## Deploy

Back up player data, install JAR, restart Paper. Test offhand interactions, weapon stacking, item persistence, custom item plugins and PvP on a staging server. Build success and pure unit tests don't prove plugin compatibility with all custom inventories.
