package dev.landminimap.bridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LandMinimapBridge extends JavaPlugin {
    public static final String CHANNEL = "landminimap:team";
    private Plugin clans;
    private Method areClanmates;

    @Override public void onEnable() {
        clans = Bukkit.getPluginManager().getPlugin("MinecraftUnified");
        try {
            if (clans == null || !clans.isEnabled()) throw new IllegalStateException("MinecraftUnified missing");
            areClanmates = clans.getClass().getMethod("areClanmates", UUID.class, UUID.class);
        } catch (ReflectiveOperationException | RuntimeException error) {
            getLogger().severe("Cannot verify clan membership; no positions will be sent: " + error);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        Bukkit.getScheduler().runTaskTimer(this, this::broadcast, 20L, 10L);
    }
    private boolean teammate(Player viewer, Player target) {
        if (viewer == target || !target.isOnline() || target.isDead()
                || viewer.getWorld() != target.getWorld() || !clans.isEnabled()) return false;
        try {
            return Boolean.TRUE.equals(areClanmates.invoke(clans, viewer.getUniqueId(), target.getUniqueId()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false; // Never substitute a broad player list or unverified membership.
        }
    }
    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }
    private void broadcast() {
        if (!clans.isEnabled()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getListeningPluginChannels().contains(CHANNEL)) continue;
            List<Player> team = viewer.getWorld().getPlayers().stream()
                    .filter(target -> teammate(viewer, target)).limit(16).toList();
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream out = new DataOutputStream(bytes)) {
                    out.writeInt(1);
                    uuid(out, viewer.getUniqueId());
                    uuid(out, viewer.getWorld().getUID());
                    out.writeByte(team.size());
                    for (Player target : team) {
                        uuid(out, target.getUniqueId());
                        byte[] name = target.getName().getBytes(StandardCharsets.UTF_8);
                        out.writeByte(name.length); out.write(name);
                        Location pos = target.getLocation();
                        out.writeDouble(pos.getX()); out.writeDouble(pos.getY()); out.writeDouble(pos.getZ());
                    }
                }
                viewer.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
            } catch (IOException | RuntimeException error) {
                getLogger().warning("Team update failed: " + error.getClass().getSimpleName());
            }
        }
    }
    @Override public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }
}
