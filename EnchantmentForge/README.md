# EnchantmentForge — Paper 26.3

Отдельный серверный плагин с двумя пользовательскими зачарованиями и улучшенным странствующим торговцем. Устанавливается **только на сервер**: клиентские моды и ресурспаки не требуются. Не изменяет TargetClaim и TalismanForge.

## Зачарования

- **Неутрачиваемость I** (`soulbound`): предмет с зачарованием не выпадает при смерти, а остаётся в инвентаре игрока после возрождения. Все остальные вещи выпадают как обычно.
- **Тройная стрела I** (`triple_shot`): количество стрел зависит от натяжения лука: быстро отпустил — 1 стрела, средний заряд — 2, сильный заряд — 3. Две стрелы летят симметрично в стороны, три — по центру и по сторонам. Горизонтальный угол разлёта и пороги натяжения настраиваются в `config.yml`. По-прежнему расходуется только одна обычная стрела; дополнительные нельзя подобрать.

Сохраняются компоненты предмета, остальные обычные чары и свойства стрел (скорость, базовый урон, критический удар, горение, эффект стрелы).

**Важно:** Это серверные пользовательские зачарования через PersistentDataContainer и наковальню, а не зарегистрированные datapack-зачарования. Они отображаются отдельными строками описания и дают блеск. В стандартном столе зачарований сами по себе не появляются.

## Настройка силы натяжения

По умолчанию: `triple-shot-medium-force: 0.50`, `triple-shot-full-force: 0.90`. Значение силы натяжения берётся из события Paper `EntityShootBowEvent#getForce()` (диапазон 0–1).

## Как получить

Для оператора сервера:

```text
/ce give Steve soulbound
/ce give Steve triple_shot
/ce give Steve soulbound 3
```

Положите предмет в левый слот наковальни, а специальную книгу — в правый. Цена: 8 уровней для Неутрачиваемости, 6 уровней для Тройной стрелы (меняется в config.yml). Можно объединить оба зачарования на одном луке. Зачарование удаляется точилом.

Для быстрого теста у оператора с предметом в руке:

```text
/ce apply soulbound
/ce apply triple_shot
```

Сокращение команды: `/ce` или `/enchants`.

## Сборка

Требуется Java 25 и Gradle 9.2.1:

```bash
gradle -p EnchantmentForge build --no-daemon
```

Готовый JAR: `EnchantmentForge/build/libs/EnchantmentForge-1.2.0.jar`. Либо скачайте JAR в GitHub Actions → **Build EnchantmentForge JAR** → Artifacts.

Положите JAR в `plugins/` Paper 26.3, перезапустите сервер. Настройки: `plugins/EnchantmentForge/config.yml`.

## Проверка в игре

1. Выдать себе книгу `/ce give <ник> soulbound`, зачаровать меч и умереть с другими предметами. Меч останется у игрока, остальные вещи выпадут.
2. Зачаровать лук книгой `triple_shot` и выстрелить трижды: короткое натяжение — одна стрела, среднее (50–89% силы) — две, сильное (90–100%) — три. Проверить разлёт по горизонтали.
3. Проверить обычные зачарования на предмете, повторное наложение (запрещено), снятие в точиле и работу наковальни.
4. Проверить с `/gamerule keepInventory true` — не должно появляться копий предмета.

Это исходный код для Paper; реальный тест на запущенном Minecraft-сервере требуется отдельно.

## Улучшенный странствующий торговец

- Спавнится **чаще**: дополнительная попытка появления рядом с одним случайным игроком каждые 25 минут (только обычный мир, загруженные чанки и безопасная поверхность). По умолчанию одновременно до **2 торговцев на мир**; в 25% успешных попыток появляется второй, если лимит позволяет. Обычные ванильные появления остаются.
- **Лам нет**: их появление отменяется. При запуске удаляются привязанные к торговцу ламы из уже загруженных чанков. Отвязанных/прирученных лам не трогаем при запуске.
- Торговец предлагает **26 случайных позиций** из большого каталога, каждая с ограниченным количеством покупок. При следующем появлении ассортимент меняется, не обновляется при каждом открытии.
- Валюта — **алмазы**, без изумрудов. Цены выше ванильных, особенно на редкости. Для товаров дороже 64 алмазов используется два слота оплаты.

Оберег всегда стоит **5 алмазов**. Две наши книги продаются за фиксированные предметы (см. таблицу), а все остальные товары — за алмазы с делителем цены **2,5** и округлением вверх (настраивается в `config.yml`).

### Гарантированный ассортимент

| Товар | Цена | Выдача |
|---|---:|---:|
| Оберег возвращения | 5 алмазов | 1 |
| Книга «Неутрачиваемость I» | 1 аметистовый блок + 1 обычная книга | 1 |
| Книга «Тройная стрела I» | 1 алмазный блок + 1 обычная книга | 1 |
| Тотем бессмертия | 16 | 1 |
| Око Края | 8 | 4 |
| Пустые карты | 2 | 8 |
| Жареная говядина | 3 | 32 |
| Один случайный спавнер (зомби, скелет, паук, пещерный паук, ифрит) | 34–45 | 1 |
| Одно случайное яйцо призыва | 8–23 | 1 |

Также в сменном ассортименте: **все ванильные зачарования без проклятий** на максимальном обычном уровне (реальные зачарованные книги через реестр Paper 26.3), различные яйца призыва, дополнительные спавнеры, элитры, незеритовый шаблон улучшения, трезубец, золотая еда, жемчуг Края, шалкеровые панцири, тотемы, тростник, сахар, бамбук, кактусы, пшеница, семена, овощи, грибы, бумага, стекло, песок, гравий и другие полезные ресурсы стаками. Полный перечень и цены — в `WanderingTraderFeature.java`.

Спавнеры создаются с `BlockStateMeta` и типом соответствующего моба, а не просто подписанным пустым блоком. Товары имеют фиксированные лимиты использования; торговец не пополняет запасы бесконечно.

### Оберег возвращения

- Предмет: осколок эха с внутренним PDC-ключом `enchantmentforge:inventory_charm` и ограничением **максимум 8 штук в одном стаке** через `ItemMeta#setMaxStackSize(8)`.
- При смерти, когда `keepInventory=false`, **один оберег расходуется**, а выпадающие предметы игрока переносятся в `PlayerDeathEvent#getItemsToKeep()`; броня и вторая рука сохраняются. Опыт не сохраняется.
- Работает только при наличии оберега непосредственно в инвентаре/экипировке; предмет внутри шалкера не активируется.
- При `keepInventory=true` не тратится. Не клонирует инвентарь через `setKeepInventory(true)`, не оставляет одновременно сохранённые предметы и дроп. Предмет с аналогичным названием без внутреннего ключа не подходит.
- Уже зачарованные на «Неутрачиваемость» предметы продолжат сохраняться при смерти без оберега. **Примечание:** вещи с проклятием утраты, не попадающие в список дропа Minecraft, оберег не восстанавливает.

### Команды администратора

- `/ce trader` — попытаться призвать торговца на безопасной поверхности в загруженном чанке поблизости.
- `/ce give <ник> charm [количество]` — тестовая выдача оберегов (1–64, в стаках по 8).
- Старые команды `/ce give` и `/ce apply` для зачарований сохранены.

### Настройки

`trader-spawn-minutes: 25` — дополнительный интервал, 5–180 минут.
`trader-max-per-world: 2` — максимум торговцев в мире для дополнительных появлений, 1–5.
`trader-despawn-ticks: 48000` — срок жизни дополнительного торговца, 24000–144000 тиков.
`trader-offers-per-trader: 26` — количество позиций одного торговца, 9–45.
`trader-price-divisor: 2.5` — делитель цен **всех товаров за алмазы, кроме оберега**, от 2 до 3. Стоимость наших двух книг фиксирована и не меняется этим параметром.
`trader-charm-price: 5` — фиксированная цена оберега.

Новые поля добавляются в `plugins/EnchantmentForge/config.yml` вручную при обновлении старого `config.yml` или используются значения по умолчанию.

### Тесты после установки

1. Проверить `/ce trader` рядом с открытой местностью, отсутствие лам и открытие торгового интерфейса.
2. Убедиться, что в каждой партии есть наши две книги, тотем, оберег, ока, еда, карты, один спавнер и яйцо.
3. Купить «Неутрачиваемость» за аметистовый блок и обычную книгу, «Тройную стрелу» за алмазный блок и книгу. Убедиться, что алмазы не заменяют эти ингредиенты. Проверить покупку остальных товаров и размещение купленного спавнера.
4. Сложить 8 оберегов; убедиться, что девятый не добавляется в тот же стак. Умереть с несколькими оберегами, бронёй, вещами в основной/второй руке. После смерти должен сохраниться инвентарь, а количество оберегов уменьшиться ровно на один.
5. Повторить при `keepInventory=true` и с предметами «Неутрачиваемость»; проверить, что ничего не дублируется.
6. Проверить взаимодействие с другим плагином изменения дропа/инвентаря на тестовом сервере. Полная совместимость с любыми сторонними обработчиками смерти не гарантируется.

**Требуется Paper 26.3, Java 25.** Код компилируется в GitHub Actions, но перед установкой на основной сервер нужен игровой тест и резервная копия.

## v1.2.0 — One full catalog for every Wandering Trader

Every Wandering Trader (natural spawn, `/summon wandering_trader`, existing on disk, and `/ce trader`) now receives the same full ordered catalog: all useful goods, all supported enchantment books, all spawn eggs and all selected spawners. Old v1.1 trader markers are upgraded the first time the trader loads, is summoned or is right-clicked. The full long merchant UI intentionally scrolls; there is no random short selection. Missing client recipe assets are not required. Prices remain the same, and traders do not bring llamas. A full server restart is needed after replacing an existing JAR.

## 1.3.0 — limited, varied trader stock and proportional diamond economy

- Every natural, manually summoned, and /ce spawned Wandering Trader now guarantees the **independent level-2 claim** for **exactly 20 diamonds**, purchasable up to two times per trader. It is a special server-tagged TARGET and cannot be upgraded by TargetClaim 1.6.0; it uses one of the player's two claim slots.
- A trader has **14–21 distinct offers** per appearance instead of the entire massive catalog. Usually includes 2–4 featured offers, 11–15 random supplies, and sometimes one rare spawner (22% chance), plus the independent claim. Each individual merchant stores its offers; reopening the UI does not reroll them.
- Prices use category-weighted bases, e.g. crops/foods/building supplies less, books/eggs medium, rare spawners/Elytra/netherite expensive. Many have about ±12% random price variation per offer; the independent claim and the two custom book recipes have fixed prices. Old trader-price-divisor setting is obsolete and ignored.
- New trader v4 marker reconfigures pre-existing traders on chunk load, interaction, server startup or new spawn, without llama caravans.

## v1.3.1: merchant independent cores and proportional random stock

Every natural or forced Wandering Trader sells a fixed-price independent TargetClaim core for **20 diamonds**. The core creates a non-upgradable **96³** claim and the plugin TargetClaim limits these to 3 per owner globally across all worlds (normal claim quotas are separate). This special core is only usable when the compatible TargetClaim module is installed — both are included in MinecraftUnified 1.1.1.

Each trader offers approximately 14–21 purchases total, with a different stable selection chosen on merchant initialization; established full-catalog traders are refreshed by the new PDC v4 marker. Common foods/crops/building blocks are inexpensive; rare spawners, elytra and netherite items remain much more costly. Most offered prices have a small ±12% variance, but the independent claim is always exactly 20 diamonds and specialty book ingredient recipes remain fixed.

## EnchantmentForge 1.4.0 — roaming village merchants

- Custom wandering-trader spawning now runs once every **5 minutes** while players are active (in addition to vanilla spawning), instead of every 25 minutes. The new default cap is **4 traders per ordinary world**; only one is created per interval. Existing server configs with old `trader-spawn-minutes: 25` use the new `trader-journey-spawn-minutes` default automatically.
- When an active player is near at least **two loaded villagers**, those villages are preferred for new traders with a **75% selection bias**. Merchants spawn at safe walkable locations, never inside blocks or in unloaded chunks. If no villages are nearby, a merchant can appear while exploring ordinary terrain.
- Every trader, including vanilla ones, is AI-aware. Roughly every **13–26 seconds** while not trading, it chooses a loaded walkable waypoint, often walking between villagers or roaming nearby wilderness. Traders cannot teleport to waypoints; Paper pathfinding does the work. Waypoint scanning has per-tick bounds, does not generate chunks and pauses while a merchant is trading.
- New config: `trader-journey-spawn-minutes: 5`, `trader-journey-max-per-world: 4`, `trader-journey-village-preference: 0.75`, `trader-journey-roam-enabled: true`, `trader-journey-speed: 0.78`. The original random 14–21 goods and independent 20-diamond claim remain unchanged.
