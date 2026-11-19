package dev.talismanforge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

public final class GrapplingHookListener implements Listener {
    private static final int COOLDOWN_MS = 15_000;
    private static final double MAX_RANGE = 36.0;
    private static final int DAMAGE_PER_USE = 8;
    private final JavaPlugin plugin;
    private final NamespacedKey hookKey;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public GrapplingHookListener(JavaPlugin plugin) {
        this.plugin = plugin;
        hookKey = new NamespacedKey(plugin, "grappling_hook");
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "hook_recipe"), newHook());
        recipe.shape("ISI", "SNS", "ITI");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('N', Material.IRON_NUGGET);
        recipe.setIngredient('T', Material.STICK);
        Bukkit.addRecipe(recipe);
    }

    private ItemStack newHook() {
        ItemStack hook = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = hook.getItemMeta();
        meta.setDisplayName("§5§lКрюк");
        meta.setLore(java.util.List.of(
            "§7ПКМ — притянуть игрока или моба",
            "§7Дальность: §f36 блоков",
            "§7Перезарядка: §d15 секунд",
            "§7Прочность: §f8 успешных применений",
            "§8Крафт: железо, нить, самородок, палка"
        ));
        meta.setItemModel(new NamespacedKey("talismanforge", "grappling_hook"));
        meta.getPersistentDataContainer().set(hookKey, PersistentDataType.BYTE, (byte) 1);
        hook.setItemMeta(meta);
        return hook;
    }

    private boolean isHook(ItemStack item) {
        return item != null && item.getType() == Material.FISHING_ROD
            && item.hasItemMeta()
            && Byte.valueOf((byte) 1).equals(
                item.getItemMeta().getPersistentDataContainer()
                    .get(hookKey, PersistentDataType.BYTE)
            );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void useHook(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isHook(held)) return;

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        long now = System.currentTimeMillis();
        long remaining = lastUse.getOrDefault(player.getUniqueId(), 0L) - now;
        if (remaining > 0) {
            player.sendActionBar(Component.text(
                "Крюк: " + ((remaining + 999) / 1000) + " сек.",
                NamedTextColor.LIGHT_PURPLE
            ));
            return;
        }

        Vector direction = player.getEyeLocation().getDirection().normalize();
        RayTraceResult entityHit = player.getWorld().rayTraceEntities(
            player.getEyeLocation(), direction, MAX_RANGE, 0.65,
            e -> e instanceof LivingEntity && e != player
                && !(e instanceof org.bukkit.entity.ArmorStand)
        );
        if (entityHit == null || !(entityHit.getHitEntity() instanceof LivingEntity victim)) {
            player.sendActionBar(Component.text("Крюк: нет цели", NamedTextColor.GRAY));
            return;
        }

        double distance = entityHit.getHitPosition()
            .distance(player.getEyeLocation().toVector());
        RayTraceResult wall = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(), direction, MAX_RANGE,
            FluidCollisionMode.NEVER, true
        );
        if (wall != null && wall.getHitPosition().distance(player.getEyeLocation().toVector()) < distance) {
            player.sendActionBar(Component.text("Крюк: цель за препятствием", NamedTextColor.RED));
            return;
        }

        if (victim instanceof Player other) {
            Team own = player.getScoreboard().getEntryTeam(player.getName());
            Team their = other.getScoreboard().getEntryTeam(other.getName());
            if (own != null && own == their && !own.allowFriendlyFire()) {
                player.sendActionBar(Component.text("Крюк: союзник защищён", NamedTextColor.GRAY));
                return;
            }
            if (other.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || other.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                player.sendActionBar(Component.text("Крюк: недоступная цель", NamedTextColor.GRAY));
                return;
            }
        }

        lastUse.put(player.getUniqueId(), now + COOLDOWN_MS);
        damageItem(player, held);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 0.9f, 0.8f);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.4f);
        drawChain(player, victim);

        UUID shooterId = player.getUniqueId();
        UUID victimId = victim.getUniqueId();
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                Player shooter = Bukkit.getPlayer(shooterId);
                Entity entity = Bukkit.getEntity(victimId);
                if (shooter == null || !(entity instanceof LivingEntity target)
                    || !target.isValid() || target.isDead()
                    || shooter.isDead() || !shooter.getWorld().equals(target.getWorld())
                    || ticks++ > 32) {
                    cancel();
                    return;
                }
                Vector toward = shooter.getLocation().add(0, 1, 0).toVector()
                    .subtract(target.getLocation().toVector());
                double d = toward.length();
                if (d < 2.4) {
                    target.setFallDistance(0);
                    shooter.getWorld().playSound(shooter.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.65f, 1.2f);
                    cancel();
                    return;
                }
                target.setVelocity(toward.normalize().multiply(Math.min(1.65, d * 0.42)));
                target.setFallDistance(0);
                if (ticks % 4 == 0) drawChain(shooter, target);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventFishing(PlayerFishEvent event) {
        if (isHook(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private void damageItem(Player player, ItemStack hook) {
        ItemMeta meta = hook.getItemMeta();
        if (!(meta instanceof Damageable damage)) return;
        int newDamage = damage.getDamage() + DAMAGE_PER_USE;
        if (newDamage >= Material.FISHING_ROD.getMaxDurability()) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            player.sendActionBar(Component.text("Крюк сломался", NamedTextColor.RED));
            return;
        }
        damage.setDamage(newDamage);
        hook.setItemMeta(meta);
        player.getInventory().setItemInMainHand(hook);
    }

    private void drawChain(Player from, LivingEntity to) {
        World world = from.getWorld();
        Vector begin = from.getEyeLocation().toVector();
        Vector end = to.getEyeLocation().toVector();
        Vector gap = end.clone().subtract(begin);
        double length = gap.length();
        if (length < 0.1) return;
        Vector step = gap.clone().normalize().multiply(0.75);
        Vector pos = begin.clone();
        for (int i = 0; i < Math.min(50, length / 0.75); i++) {
            world.spawnParticle(Particle.END_ROD,
                pos.getX(), pos.getY(), pos.getZ(),
                1, 0, 0, 0, 0);
            pos.add(step);
        }
    }
}