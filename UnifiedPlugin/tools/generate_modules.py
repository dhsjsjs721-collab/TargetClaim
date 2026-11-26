#!/usr/bin/env python3
"""Compile-time conversion of 5 existing Paper plugins into one host plugin.

No duplicated/stale Java files are stored in this branch. Instead the build reads
the latest source commits from the 4 original branches, keeping all code/behavior,
plugin namespaced PDC tags, recipes, and per-plugin data folders compatible.
Requires a full checkout (actions/checkout fetch-depth: 0).
"""
from pathlib import Path
import re
import subprocess

root=Path(__file__).resolve().parents[2]
base=root/"UnifiedPlugin"/"build"
src=base/"generated-sources"
resources=base/"generated-resources"/"module-defaults"

MODULES=[
("origin/main","src/main/java/dev/targetclaim/TargetClaimPlugin.java","targetclaim","TargetClaim"),
("origin/feature/enchantmentforge-soulbound-triple-shot","EnchantmentForge/src/main/java/dev/enchantmentforge/EnchantmentForgePlugin.java","enchantmentforge","EnchantmentForge"),
("0bcbba5500159637a7205e31a9e44c022f74bd6a","SwampSky/src/main/java/dev/swampsky/SwampSkyPlugin.java","swampsky","SwampSky"),
("origin/main","ImageMaps/src/main/java/dev/imagemaps/ImageMapsPlugin.java","imagemaps","ImageMaps"),
("origin/talismanforge-v2","TalismanForge/src/main/java/dev/talismanforge/GrapplingHookListener.java","talismanforge","GrapplingHookListener"),
("origin/feature/enchantmentforge-soulbound-triple-shot","EnchantmentForge/src/main/java/dev/enchantmentforge/WanderingTraderFeature.java","enchantmentforge","WanderingTraderFeature"),
]

def git(ref,path):
    completed=subprocess.run(["git","show",f"{ref}:{path}"],cwd=root,capture_output=True,text=True)
    if completed.returncode:
        raise RuntimeError(f"git show {ref}:{path}: {completed.stderr}")
    return completed.stdout

def swap(code,old,new,label,required=True):
    number=code.count(old)
    if required and number==0:raise ValueError(f"Missing expected {label} occurrence: {old}")
    return code.replace(old,new)

def convert(code,namespace,name,path):
    plugin_class=name+"Plugin"
    if name=="TargetClaim":
        code=swap(code,'meta.setDisplayName("§d§l✦ Ядро привата ✦");','meta.setDisplayName("§d§l✦ Ядро привата ✦");meta.setItemModel(new NamespacedKey("targetclaim","claim_core"));',"core model")
        code=swap(code,'meta.setDisplayName("§6§lНезависимый приват");','meta.setDisplayName("§6§lНезависимый приват");meta.setItemModel(new NamespacedKey("targetclaim","independent_core"));',"independent core model")
    if name=="EnchantmentForge":
        code=swap(code,'meta.setDisplayName("§dКнига: "+enchant.title);','meta.setDisplayName("§dКнига: "+enchant.title);meta.setItemModel(new NamespacedKey("enchantmentforge",enchant.id));',"book model")
    if name=="WanderingTraderFeature":
        code=swap(code,'meta.setDisplayName("§bОберег возвращения");','meta.setDisplayName("§bОберег возвращения");meta.setItemModel(new NamespacedKey("enchantmentforge","return_charm"));',"charm model")
        code=swap(code,'meta.setDisplayName("§6§lНезависимый приват");','meta.setDisplayName("§6§lНезависимый приват");meta.setItemModel(new NamespacedKey("targetclaim","independent_core"));',"trader core model")
        code=swap(code,"for (Offer offer:selected) recipes.add(recipe(offer));","for (Offer offer:selected) recipes.add(recipe(offer));\nif(random.nextDouble()<.05)recipes.add(dev.unified.Verity.offer(random));","rare Verity trade")
    if name in ("GrapplingHookListener","WanderingTraderFeature"):
        if name=="GrapplingHookListener":
            code=code.replace("import org.bukkit.plugin.java.JavaPlugin;","import org.bukkit.plugin.Plugin;")
            code=code.replace("JavaPlugin plugin","Plugin plugin")
            code=code.replace("new NamespacedKey(plugin,","new NamespacedKey(\"talismanforge\",")
        else:
            code=code.replace("import org.bukkit.plugin.java.JavaPlugin;","")
            code=code.replace("JavaPlugin plugin","EnchantmentForgePlugin plugin")
            code=code.replace("new NamespacedKey(plugin,","new NamespacedKey(\"enchantmentforge\",")
            code=code.replace("registerEvents(this,plugin)","registerEvents(this,plugin.host())")
            code=re.sub(r"runTask(Later|Timer|Asynchronously)?\(plugin,",r"runTask\1(plugin.host(),",code)
        return code

    old=re.search(r"public final class "+re.escape(plugin_class)+r" extends (?:org\.bukkit\.plugin\.java\.)?JavaPlugin implements ",code)
    if not old:raise ValueError(f"Missing plugin class declaration for {name}")
    code=code[:old.start()]+old.group().replace("extends org.bukkit.plugin.java.JavaPlugin","extends ModuleBridge").replace("extends JavaPlugin","extends ModuleBridge")+code[old.end():]
    code=code.replace("package dev."+namespace+";","package dev."+namespace+";\n\nimport dev.unified.ModuleBridge;\nimport org.bukkit.plugin.java.JavaPlugin;")
    brace=code.index("{",code.index("public final class "+plugin_class))
    code=code[:brace+1]+"\npublic "+plugin_class+"(JavaPlugin plugin){super(plugin,\""+name+"\");}\n"+code[brace+1:]
    code=code.replace("new NamespacedKey(this,","new NamespacedKey(\""+namespace+"\",")
    code=code.replace("registerEvents(this,this)","registerEvents(this,host())")
    code=code.replace("registerEvents(hookListener,this)","registerEvents(hookListener,host())")
    code=re.sub(r"runTask(Later|Timer|Asynchronously)?\(this,",r"runTask\1(host(),",code)
    code=code.replace("new GrapplingHookListener(this)","new GrapplingHookListener(host())")
    if name=="ImageMaps":
        # If storage initialization fails, throw into the unified module
        # loader instead of disabling the entire host plugin.
        code=code.replace("getPluginManager().disablePlugin(this);return;",
            'getLogger().severe("ImageMaps storage unavailable");throw new IllegalStateException("ImageMaps storage initialization failed",ex);')
    code=code.replace("getPluginManager().disablePlugin(this)","getPluginManager().disablePlugin(host())")

    if name=="TargetClaim":
        code=re.sub(r'org\.bukkit\.plugin\.Plugin tf=Bukkit\.getPluginManager\(\)\.getPlugin\("TalismanForge"\);\nif\(tf!=null.*?catch\(ReflectiveOperationException ignored\)\{\}', 'for(Location egg:((dev.unified.UnifiedPlugin)host()).getKoscheiEggLocations())if(egg!=null&&egg.getWorld()!=null&&egg.getWorld().getUID().equals(c.world)&&egg.getBlockX()>=left&&egg.getBlockX()<=right&&egg.getBlockY()>=bottom&&egg.getBlockY()<=top&&egg.getBlockZ()>=front&&egg.getBlockZ()<=back)return true;', code, count=1)
        if 'getKoscheiEggLocations()' not in code:raise ValueError("Missing hosted egg/claim integration")

        # Persist claim size policy per core. Old claims retain their existing protected area.
        code=swap(code,"boolean teamBuild=false,independent=false;","boolean teamBuild=false,independent=false,spawnSized=false;","claim spawn flag")
        code=swap(code,"int minX(){return x-SIZE[level]/2;}","int width(int l){return spawnSized?SIZE[l]/2:SIZE[l];}\nint height(int l){return spawnSized?HEIGHT[l]/2:HEIGHT[l];}\nint minX(){return x-width(level)/2;}","claim dimensions")
        for old,new in [
            ("return minX()+SIZE[level]-1;","return minX()+width(level)-1;"),
            ("return z-SIZE[level]/2;","return z-width(level)/2;"),
            ("return minZ()+SIZE[level]-1;","return minZ()+width(level)-1;"),
            ("return y-HEIGHT[level]/2;","return y-height(level)/2;"),
            ("return minY()+HEIGHT[level]-1;","return minY()+height(level)-1;"),
            ("return c.x-SIZE[level]/2;","return c.x-c.width(level)/2;"),
            ("return c.z-SIZE[level]/2;","return c.z-c.width(level)/2;"),
            ("return c.y-HEIGHT[level]/2;","return c.y-c.height(level)/2;"),
            ("return minX(c,level)+SIZE[level]-1;","return minX(c,level)+c.width(level)-1;"),
            ("return minZ(c,level)+SIZE[level]-1;","return minZ(c,level)+c.width(level)-1;"),
            ("return minY(c,level)+HEIGHT[level]-1;","return minY(c,level)+c.height(level)-1;"),
            ('c.teamBuild=y.getBoolean(root+"teamBuild",false);','c.teamBuild=y.getBoolean(root+"teamBuild",false);c.spawnSized=y.getBoolean(root+"spawn-sized",false);'),
            ('y.set(r+"independent",c.independent);','y.set(r+"independent",c.independent);y.set(r+"spawn-sized",c.spawnSized);'),
            ("c.independent=special;","c.independent=special;c.spawnSized=((dev.unified.UnifiedPlugin)host()).isPeace(b.getLocation());"),
            ("private boolean overlap(Claim c){return wouldOverlap(c,c.level);}","private boolean overlap(Claim c){return wouldOverlap(c,4);}"),
            ("int left=minX(c,level),right=maxX(c,level),front=minZ(c,level),back=maxZ(c,level);","int left=minX(c,4),right=maxX(c,4),front=minZ(c,4),back=maxZ(c,4);"),
            ("int bottom=minY(c,level),top=maxY(c,level);","int bottom=minY(c,4),top=maxY(c,4);"),
            ("for(Claim other:regionClaims(c.world,left,right,front,back)){\nif(other.owner.equals(c.owner))continue;\nif(left<=other.maxX()&&right>=other.minX()&&bottom<=other.maxY()&&top>=other.minY()&&front<=other.maxZ()&&back>=other.minZ())return true;\n}","for(Claim other:claims.values()){\nif(other.id.equals(c.id)||!other.world.equals(c.world))continue;\nlong dx=Math.abs((long)c.x-other.x),dz=Math.abs((long)c.z-other.z);\nint distance=(c.width(4)+other.width(4)+1)/2;\nif(dx<distance&&dz<distance)return true;\n}"),
            ("Приват пересекается с другой территорией.","Слишком близко к другому приватному ядру или яйцу Кощея. Нужен запас на максимальный размер."),
            ('private String dimension(int level){return SIZE[level]+" × "+HEIGHT[level]+" × "+SIZE[level];}','private String dimension(Claim c,int level){return c.width(level)+" × "+c.height(level)+" × "+c.width(level);}')
        ]:
            code=swap(code,old,new,"claim policy")
        code=code.replace("dimension(claim.level)","dimension(claim,claim.level)").replace("dimension(own.level)","dimension(own,own.level)").replace("dimension(c.level)","dimension(c,c.level)").replace("dimension(4)","dimension(claim,4)").replace("dimension(claim.level+1)","dimension(claim,claim.level+1)")
        code=code.replace('SIZE[c.level]+"×"+HEIGHT[c.level]+"×"+SIZE[c.level]','c.width(c.level)+"×"+c.height(c.level)+"×"+c.width(c.level)')
        code=code.replace("SIZE[c.level]/2.","c.width(c.level)/2.").replace("HEIGHT[c.level]/2.","c.height(c.level)/2.")
        # Old overlapping cores may not expand; existing areas remain untouched.
        code=swap(code,"private File dataFile;","private File dataFile;\n"+"\nprivate record ChatStamp(String message,long at){}\nprivate final Map<UUID,ChatStamp> chatStamps=new HashMap<>();\nprivate final Map<UUID,UUID> lastPrivate=new HashMap<>();\nprivate final Map<UUID,Long> inviteCooldown=new HashMap<>();\nprivate final Map<UUID,Long> clanChangeCooldown=new HashMap<>();\nprivate final Map<UUID,Map<UUID,String>> tabCache=new HashMap<>();\nprivate String shortTag(Group group){\nString name=group.name.replaceAll(\"[_-]\",\"\");return name.codePoints().limit(3).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString().toUpperCase(Locale.ROOT);\n}\nprivate net.kyori.adventure.text.format.TextColor clanColor(Group group){\nreturn net.kyori.adventure.text.format.TextColor.color(group==null?0xE8E8E8:switch(group.color){case BLACK->0x000000;case DARK_BLUE->0x0000AA;case DARK_GREEN->0x00AA00;case DARK_AQUA->0x00AAAA;case DARK_RED->0xAA0000;case DARK_PURPLE->0xAA00AA;case GOLD->0xFFAA00;case GRAY->0xAAAAAA;case DARK_GRAY->0x555555;case BLUE->0x5555FF;case GREEN->0x55FF55;case AQUA->0x55FFFF;case RED->0xFF5555;case LIGHT_PURPLE->0xFF55FF;case YELLOW->0xFFFF55;default->0xFFFFFF;});\n}\nprivate net.kyori.adventure.text.Component gradient(String text,int first,int second){\nvar out=net.kyori.adventure.text.Component.empty();int[] cp=text.codePoints().toArray();\nfor(int i=0;i<cp.length;i++){double t=cp.length<=1?0:i/(double)(cp.length-1);int r=(int)(((first>>16)&255)*(1-t)+((second>>16)&255)*t),g=(int)(((first>>8)&255)*(1-t)+((second>>8)&255)*t),b=(int)((first&255)*(1-t)+(second&255)*t);out=out.append(net.kyori.adventure.text.Component.text(new String(Character.toChars(cp[i])),net.kyori.adventure.text.format.TextColor.color(r,g,b)));}return out;\n}\nprivate net.kyori.adventure.text.Component clanIdentity(Player p,boolean distant){\nGroup g=groupOf(p.getUniqueId());var color=clanColor(g);int rgb=color.value();\nif(distant){int r=(int)(((rgb>>16)&255)*.5+130*.5),gr=(int)(((rgb>>8)&255)*.5+130*.5),b=(int)((rgb&255)*.5+130*.5);rgb=(r<<16)|(gr<<8)|b;color=net.kyori.adventure.text.format.TextColor.color(rgb);}\nvar out=net.kyori.adventure.text.Component.empty();\nif(g!=null)out=out.append(gradient(\"[\"+shortTag(g)+\"] \",distant?0x888888:0xEEEEEE,rgb));\nreturn out.append(net.kyori.adventure.text.Component.text(p.getName(),color));\n}\nprivate dev.unified.WorldTabStyle worldTab;\nprivate int tabTicks;\nprivate void refreshClanTab(){\nif(worldTab==null){worldTab=new dev.unified.WorldTabStyle(host());worldTab.enable();}\nSet<UUID> online=new HashSet<>();boolean reset=false;\nfor(Player p:Bukkit.getOnlinePlayers()){online.add(p.getUniqueId());var name=clanIdentity(p,false);if(!name.equals(p.playerListName())){p.playerListName(name);reset=true;}}\nif(reset||++tabTicks%5==0)tabCache.clear();\ntabCache.keySet().retainAll(online);\nfor(Player viewer:Bukkit.getOnlinePlayers()){\nMap<UUID,String> cache=tabCache.computeIfAbsent(viewer.getUniqueId(),id->new HashMap<>());Map<UUID,net.kyori.adventure.text.Component> changes=new HashMap<>();\nfor(Player target:Bukkit.getOnlinePlayers()){\nif(!viewer.canSee(target)){cache.remove(target.getUniqueId());continue;}\nboolean distant=viewer.getWorld()!=target.getWorld();\nvar name=clanIdentity(target,distant);String key=name.toString();\nif(!key.equals(cache.get(target.getUniqueId()))){changes.put(target.getUniqueId(),name);cache.put(target.getUniqueId(),key);}\n}\ncache.keySet().retainAll(online);if(!worldTab.send(viewer,changes))cache.clear();\n}\n}\n\nprivate boolean allowChat(Player p,String msg){\nlong now=System.currentTimeMillis();ChatStamp old=chatStamps.get(p.getUniqueId());\nif(old!=null&&(now-old.at()<900||old.message().equalsIgnoreCase(msg)&&now-old.at()<6000)){tell(p,\"§cНе спамь сообщениями.\");return false;}\nchatStamps.put(p.getUniqueId(),new ChatStamp(msg,now));return true;\n}\n@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)\npublic void clanChat(io.papermc.paper.event.player.AsyncChatEvent event){\nString raw=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());UUID sender=event.getPlayer().getUniqueId();\nevent.setCancelled(true);\nBukkit.getScheduler().runTask(host(),()->{Player p=Bukkit.getPlayer(sender);if(p!=null)routeChat(p,raw);});\n}\nprivate void routeChat(Player p,String raw){\nString msg=raw.strip();if(msg.isEmpty())return;\nchar mode=msg.charAt(0);if(mode=='!'||mode=='.')msg=msg.substring(1).strip();else mode='L';\nif(msg.isEmpty())return;Group group=groupOf(p.getUniqueId());\nif(mode=='.'&&group==null){tell(p,\"§cТы не в клане.\");return;}\nif(!allowChat(p,msg))return;\nvar prefix=mode=='!'?gradient(\"[G] \",0xFFBB6B,0xF7D885):mode=='.'?gradient(\"[КЛАН] \",0xE4ACFF,0xA4D3FF):gradient(\"[L] \",0xA3B6A9,0xC1DDD0);\nvar line=prefix.append(clanIdentity(p,false)).append(net.kyori.adventure.text.Component.text(\" » \"+msg,net.kyori.adventure.text.format.NamedTextColor.WHITE));\nint recipients=0;for(Player v:Bukkit.getOnlinePlayers()){\nif(mode=='.'&&(groupOf(v.getUniqueId())!=group))continue;\nif(mode=='L'&&(p.getWorld()!=v.getWorld()||p.getLocation().distanceSquared(v.getLocation())>10000))continue;\nv.sendMessage(line);if(v!=p)recipients++;\n}\nif(mode!='.')Bukkit.getConsoleSender().sendMessage(line);\nif(mode=='L'&&recipients==0)tell(p,\"§7Рядом никого нет. Для глобального чата используй !сообщение.\");\n}\npublic boolean privateChat(CommandSender sender,String[] args,boolean reply){\nif(!(sender instanceof Player p)){tell(sender,\"§cТолько для игроков.\");return true;}\nif(args.length<(reply?1:2)){tell(p,reply?\"§e/r <сообщение>\":\"§e/msg <ник> <сообщение>\");return true;}\nPlayer target=reply?Bukkit.getPlayer(lastPrivate.getOrDefault(p.getUniqueId(),p.getUniqueId())):Bukkit.getPlayerExact(args[0]);\nif(target==null||target==p){tell(p,\"§cСобеседник недоступен.\");return true;}\nString msg=String.join(\" \",Arrays.copyOfRange(args,reply?0:1,args.length)).strip();\nif(msg.isEmpty()||!allowChat(p,msg))return true;\nvar sep=net.kyori.adventure.text.Component.text(\" » \"+msg,net.kyori.adventure.text.format.NamedTextColor.WHITE);\np.sendMessage(gradient(\"[ЛС →] \",0xD3A8F7,0x95B9F4).append(clanIdentity(target,false)).append(sep));\ntarget.sendMessage(gradient(\"[ЛС ←] \",0xD3A8F7,0x95B9F4).append(clanIdentity(p,false)).append(sep));\nlastPrivate.put(p.getUniqueId(),target.getUniqueId());lastPrivate.put(target.getUniqueId(),p.getUniqueId());return true;\n}\npublic List<String> privateTabs(String[] args){return args.length==1?starts(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),args[0]):List.of();}\n","clan chat")
        code=swap(code,'getLogger().info("TargetClaim 1.7.0:', 'Bukkit.getScheduler().runTaskTimer(host(),this::refreshClanTab,20L,40L);\ngetLogger().info("TargetClaim 1.7.0:',"clan tab")
        code=swap(code,'case "color" -> {\nif(g==null','case "color" -> {\nif(g==null',"clan color")
        code=swap(code,'g.color=color;updateScoreboard(g);saveAll();','tell(p,"§cЦвет клана выбирается при создании и больше не меняется.");break;',"immutable clan color")
        code=swap(code,'broadcast(g,"§aЦвет команды изменён: "+g.color+g.name);','',"remove color broadcast")
        code=swap(code,'invites.put(target.getUniqueId(),new Invite(g.id,System.currentTimeMillis()+120000));\ntell(target,"§dПриглашение в команду §r"+g.color+g.name+"§d. Принять: §f/u team accept §7(2 минуты)");',"long time=System.currentTimeMillis();Invite previous=invites.get(target.getUniqueId());\nif(previous!=null&&previous.expires>time||time-inviteCooldown.getOrDefault(p.getUniqueId(),0L)<10000){tell(p,\"§cПриглашение уже отправлено или действует задержка 10 секунд.\");break;}\ninviteCooldown.put(p.getUniqueId(),time);invites.put(target.getUniqueId(),new Invite(g.id,time+120000));\ntarget.sendMessage(gradient(\"Приглашение в клан \",0xE3BBFF,0x8FBCFF).append(net.kyori.adventure.text.Component.text(g.name,clanColor(g))).append(net.kyori.adventure.text.Component.text(\"  \")).append(net.kyori.adventure.text.Component.text(\"[ПРИНЯТЬ]\",net.kyori.adventure.text.format.NamedTextColor.GREEN).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(\"/clan accept\"))).append(net.kyori.adventure.text.Component.text(\"  \")).append(net.kyori.adventure.text.Component.text(\"[ОТКЛОНИТЬ]\",net.kyori.adventure.text.format.NamedTextColor.RED).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(\"/clan deny\"))));","clickable invite")
        code=swap(code,'private void showTeamDialog(Player player){openClaimMenu(player,selectedClaim(player),"team");}','private void showTeamDialog(Player player){openClaimMenu(player,null,"team");}',"standalone clan GUI")
        code=swap(code,'case "team"->"Команда"','case "team"->"Клан"',"clan title")
        code=swap(code,'group.leader.equals(player.getUniqueId())?"§7Нажми для переключения цвета":"§7Цвет меняет только лидер"','"§7Цвет фиксируется при создании"',"clan color lore")
        code=swap(code,'if(slot==24){\nGroup group=groupOf(player.getUniqueId());','if(slot==24){\nGroup group=groupOf(player.getUniqueId());',"clan menu")
        code=code.replace('team.setPrefix(g.color.toString());','team.prefix(gradient("["+shortTag(g)+"] ",0xEEEEEE,clanColor(g).value()));')
        code=code.replace('if(group==null){\ninv.setItem(13,guiItem(Material.BOOK,"§fСоздать команду","§7/u team create Название Цвет","§7Цвета: /u team colors"));','if(group==null){\ninv.setItem(13,guiItem(Material.BOOK,"§fСоздать клан","§7/clan create Название Цвет","§7Цвета: /clan colors"));')
        code=code.replace('if(slot==15){\nGroup group=groupOf(player.getUniqueId());','if(slot==13&&groupOf(player.getUniqueId())==null){Bukkit.getScheduler().runTask(host(),()->{player.closeInventory();player.sendMessage(net.kyori.adventure.text.Component.text("Нажми, чтобы ввести название клана",net.kyori.adventure.text.format.NamedTextColor.AQUA).clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/clan create ")));});}\nif(slot==15){\nGroup group=groupOf(player.getUniqueId());')
        code=code.replace('§7/u team invite Ник','§7/clan invite Ник').replace('§7/u team kick Ник','§7/clan kick Ник').replace('§7/u team disband','§7/clan disband').replace('§7/u team color <цвет>','§7Цвет и название фиксированы')
        code=code.replace('if(slot==22){\nGroup group=groupOf(player.getUniqueId());\nif(group!=null){leave(player.getUniqueId(),group);fillTeamMenu(inv,player);}\n}', 'if(slot==22){\nGroup group=groupOf(player.getUniqueId());\nif(group!=null){Bukkit.getScheduler().runTask(host(),()->{player.closeInventory();player.sendMessage(net.kyori.adventure.text.Component.text("Подтвердить выход из клана",net.kyori.adventure.text.format.NamedTextColor.RED).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/clan leave")));});}\n}')
        code=code.replace('if(meta!=null){meta.setDisplayName(title);meta.setLore(Arrays.asList(lore));stack.setItemMeta(meta);}', 'if(meta!=null){meta.setDisplayName(title);meta.setLore(Arrays.asList(lore));stack.setItemMeta(meta);}')



    if name=="TargetClaim":
        # Empty inventory cells must really be empty; never use a custom item as background.
        code=swap(code,'ItemStack black=guiItem(Material.BLACK_STAINED_GLASS_PANE," ");\nfor(int i=0;i<27;i++)inv.setItem(i,black);','inv.clear();',"plain chest menu")
        code=code.replace('guiItem(Material.BLACK_STAINED_GLASS_PANE," ")','null')
        code=swap(code,'private void fillTeamMenu(Inventory inv,Player player){','private void fillTeamMenu(Inventory inv,Player player){\nguiBase(inv);',"clear stale clan buttons")
        code=swap(code,'if(slot<0||slot>=inv.getSize())return;','if(slot<0||slot>=inv.getSize()||inv.getItem(slot)==null||inv.getItem(slot).getType().isAir())return;',"ignore blank menu slots")

    if name=="TargetClaim":
        # A core inside the peace zone (including legacy cores) cannot upgrade beyond II.
        # Keep existing protected dimensions; never silently shrink previously saved claims.
        code=swap(code,"private void upgrade(Player p,Claim c){","private int maxLevel(Claim c){\nWorld w=Bukkit.getWorld(c.world);\nreturn c.spawnSized||(w!=null&&((dev.unified.UnifiedPlugin)host()).isPeace(new Location(w,c.x,c.y,c.z)))?2:4;\n}\nprivate int reservedLevel(Claim c){return Math.max(c.level,maxLevel(c));}\nprivate void upgrade(Player p,Claim c){","spawn level cap")
        for old,new in [
            ("core.level<4","core.level<maxLevel(core)"),
            ("c.level>=4","c.level>=maxLevel(c)"),
            ("claim.level<4","claim.level<maxLevel(claim)"),
            ('tell(p,"§aПриват уже максимального уровня.");','tell(p,"§aМаксимальный уровень привата здесь: "+maxLevel(c)+".");'),
            ("return world!=null&&minY(c,level)","return level<=maxLevel(c)&&world!=null&&minY(c,level)"),
            ("return wouldOverlap(c,4);","return wouldOverlap(c,reservedLevel(c));"),
            ("minX(c,4),right=maxX(c,4),front=minZ(c,4),back=maxZ(c,4)","minX(c,reservedLevel(c)),right=maxX(c,reservedLevel(c)),front=minZ(c,reservedLevel(c)),back=maxZ(c,reservedLevel(c))"),
            ("minY(c,4),top=maxY(c,4)","minY(c,reservedLevel(c)),top=maxY(c,reservedLevel(c))"),
            ("c.width(4)+other.width(4)","c.width(reservedLevel(c))+other.width(reservedLevel(other))"),
            ("if(self&&preview&&!c.independent){","if(self&&preview&&c==mine&&!c.independent&&c.level<maxLevel(c)){"),
            ("if(c.level+1<4)drawBorder(p,c,4,","if(c.level+1<maxLevel(c))drawBorder(p,c,maxLevel(c),"),
            ("dimension(claim,4)","dimension(claim,maxLevel(claim))"),
            ('c.level+"/4"','c.level+"/"+maxLevel(c)'),
            ('claim.level+"/4"','claim.level+"/"+maxLevel(claim)'),
        ]:
            code=swap(code,old,new,"peace claim cap")

    if name=="TargetClaim":
        code=swap(code,"public boolean isClaimed(Location place){","public boolean ownsShopLocation(UUID owner,Location place){Claim c=claimAt(place);return c!=null&&c.owner.equals(owner);}\npublic boolean isClaimed(Location place){","shop chest ownership")
    if name=="EnchantmentForge" and path.endswith("WanderingTraderFeature.java"):
        code=swap(code,"return recipes;","recipes.add(dev.unified.Shopkeepers.offer());\nreturn recipes;","personal merchant offer")
        code=swap(code,"if (trader.getPersistentDataContainer().has(traderKey,PersistentDataType.BYTE)) return;","if (trader.getPersistentDataContainer().has(traderKey,PersistentDataType.BYTE)) {\nvar existing=new java.util.ArrayList<>(trader.getRecipes());\nvar shopOffer=dev.unified.Shopkeepers.offer();\nif(existing.stream().noneMatch(r->r.getResult().isSimilar(shopOffer.getResult()))){existing.add(shopOffer);trader.setRecipes(existing);}\nreturn;\n}","personal merchant migration")

    # TalismanForge's previous shield and item state keys already use the
    # original namespace, via the explicit Keys above. No data migration.
    return code

def main():
    import shutil
    if src.exists():shutil.rmtree(src)
    src.mkdir(parents=True,exist_ok=True)
    resources.mkdir(parents=True,exist_ok=True)
    for ref,path,ns,name in MODULES:
        source=git(ref,path)
        patched=convert(source,ns,name,path)
        target=src/Path(path).name
        # Put Java sources under their original package subfolders.
        target=src/"dev"/ns/Path(path).name
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(patched,encoding="utf8")
        print(f"{name}: {len(source)} -> {len(patched)} bytes ({ref})")
    for ref,config,name in [
        ("origin/main","TalismanForge/src/main/resources/config.yml","TalismanForge"),
        ("0bcbba5500159637a7205e31a9e44c022f74bd6a","SwampSky/src/main/resources/config.yml","SwampSky"),
        ("origin/feature/enchantmentforge-soulbound-triple-shot","EnchantmentForge/src/main/resources/config.yml","EnchantmentForge")
    ]:
        (resources/(name+".yml")).write_text(git(ref,config),encoding="utf8")

if __name__=="__main__":
    main()
