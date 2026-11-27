package dev.unified;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;

final class CaveAndTips {
private final UnifiedPlugin host;
private final NamespacedKey underground=new NamespacedKey("minecraftunified","cave_seconds"), recovery=new NamespacedKey("minecraftunified","cave_recovery");
private static final int LIMIT=25*60, RECOVER=60;
private final Random random=new Random();
private int last=-1;
private final List<String> tips=List.of(
"Обычный тотем бессмертия можно скрафтить из 8 золотых блоков и 1 изумруда.",
"Введи /u recipes, чтобы открыть книгу кастомных крафтов.",
"Свиток возвращения открывает меню точек возрождения.",
"Обсидиановая трапка действует 15 секунд, перезарядка — 60 секунд.",
"Долгое пребывание глубоко под землёй вызывает пещерную болезнь. Выйди под открытое небо на минуту."
);
CaveAndTips(UnifiedPlugin host){this.host=host;}
void enable(){
Bukkit.getScheduler().runTaskTimer(host,this::tick,100L,100L);
Bukkit.getScheduler().runTaskTimer(host,this::tip,6000L,6000L);
}
private void tip(){
if(Bukkit.getOnlinePlayers().isEmpty())return;
int next;do{next=random.nextInt(tips.size());}while(next==last);last=next;
Bukkit.broadcastMessage("§6§lА вы знали? §r§f"+tips.get(next));
}
private void tick(){
for(Player p:Bukkit.getOnlinePlayers()){
if(p.isDead()||p.getGameMode()!=GameMode.SURVIVAL||host.seasonLocked(p)||p.getWorld().getEnvironment()!=World.Environment.NORMAL||p.getWorld().getName().equals(TestWorld.WORLD))continue;
Location l=p.getLocation();var data=p.getPersistentDataContainer();
int seconds=data.getOrDefault(underground,PersistentDataType.INTEGER,0);
int sky=data.getOrDefault(recovery,PersistentDataType.INTEGER,0);
int surface=l.getWorld().getHighestBlockYAt(l.getBlockX(),l.getBlockZ());
boolean open=l.getBlockY()>=surface && p.getEyeLocation().getBlock().getLightFromSky()==15;
boolean deep=surface-l.getBlockY()>=8 && p.getEyeLocation().getBlock().getLightFromSky()<8;
if(open){
sky=Math.min(RECOVER,sky+5);
if(sky>=RECOVER){if(seconds>=LIMIT)p.sendMessage("§aПещерная болезнь отступила.");seconds=0;}
}else{sky=0;if(deep){int before=seconds;seconds=Math.min(LIMIT,seconds+5);if(before<LIMIT&&seconds==LIMIT)p.sendMessage("§5Пещерная болезнь! §7Проведи минуту под открытым небом.");}}
data.set(underground,PersistentDataType.INTEGER,seconds);
data.set(recovery,PersistentDataType.INTEGER,sky);
if(seconds>=LIMIT){apply(p,PotionEffectType.SLOWNESS);apply(p,PotionEffectType.NAUSEA);}
}
}
private void apply(Player p,PotionEffectType type){
PotionEffect old=p.getPotionEffect(type);
if(old==null||old.getAmplifier()==0&&old.getDuration()<120)p.addPotionEffect(new PotionEffect(type,140,0,true,true,true));
}
}
