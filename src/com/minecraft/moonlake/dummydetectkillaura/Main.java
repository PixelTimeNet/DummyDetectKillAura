/*
 * Copyright (C) 2017 The MoonLake Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.minecraft.moonlake.dummydetectkillaura;

import com.minecraft.moonlake.MoonLakeAPI;
import com.minecraft.moonlake.MoonLakePlugin;
import com.minecraft.moonlake.api.event.MoonLakeListener;
import com.minecraft.moonlake.api.packet.listener.PacketListenerFactory;
import com.minecraft.moonlake.api.packet.listener.handler.PacketHandler;
import com.minecraft.moonlake.api.packet.listener.handler.PacketOption;
import com.minecraft.moonlake.api.packet.listener.handler.PacketReceived;
import com.minecraft.moonlake.api.packet.listener.handler.PacketSent;
import com.minecraft.moonlake.encrypt.md5.MD5Encrypt;
import com.minecraft.moonlake.manager.RandomManager;
import com.minecraft.moonlake.reflect.Reflect;
import com.minecraft.moonlake.util.StringUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

public class Main extends JavaPlugin implements MoonLakeListener {

    private Vector[] LOCATION_OFFSET;
    private PacketHandler PACKETHANDLER;
    private Object ENUMENTITYUSEACTION_ATTACK;
    private Map<String, Map<Integer, Boolean>> DDKAMAP;
    private Map<String, Long> DDKATIMEMAP;
    private List<EntityPlayer> DUMMYLIST;
    private String PREFIX;

    private Class<?> CLASS_ENUMENTITYUSEACTION;
    private Method METHOD_VALUEOF;

    public Main() {
    }

    @Override
    public void onEnable() {
        if(!setupMoonLake()) {
            this.getLogger().log(Level.SEVERE, "前置月色之湖核心API插件加载失败.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.initFolder();
        this.initReflect();
        this.initPacketListener();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getLogger().info("假人检测杀戮光环 DummyDetectKillAura 插件 v" + getDescription().getVersion() + " 成功加载.");
    }

    @Override
    public void onDisable() {
        // 清除数据包监听器以及释放变量
        closeDetectDummy(); // 清除所有玩家的测试假人数据
        PacketListenerFactory.removeHandler(PACKETHANDLER);
        ENUMENTITYUSEACTION_ATTACK = null;
        CLASS_ENUMENTITYUSEACTION = null;
        METHOD_VALUEOF = null;
        LOCATION_OFFSET = null;
        PACKETHANDLER = null;
        DDKATIMEMAP.clear();
        DDKATIMEMAP = null;
        DUMMYLIST.clear();
        DUMMYLIST = null;
        DDKAMAP.clear();
        DDKAMAP = null;
        PREFIX = null;
    }

    private void initReflect() {
        // 初始化反射
        try {
            CLASS_ENUMENTITYUSEACTION = Reflect.PackageType.MINECRAFT_SERVER.getClass("PacketPlayInUseEntity$EnumEntityUseAction");
            METHOD_VALUEOF = Reflect.getMethod(CLASS_ENUMENTITYUSEACTION, "valueOf", String.class);
            ENUMENTITYUSEACTION_ATTACK = METHOD_VALUEOF.invoke(null, "ATTACK");
        } catch (Exception e) {
            // exception
            getLogger().log(Level.SEVERE, "初始化反射源数据时错误, 异常信息:", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initPacketListener() {
        // 初始化数据包监听器
        PACKETHANDLER = new PacketHandler(this) {
            @Override
            public void onSend(PacketSent packet) {
            }

            @Override
            @PacketOption(forcePlayer = true)
            public void onReceive(PacketReceived packet) {
                // 处理服务器接收数据包
                if(!packet.hasPlayer()) return; // 没有玩家则返回
                if(!packet.getPacketName().equals("PacketPlayInUseEntity")) return; // 不是使用实体则返回
                if(!packet.getPacketValue("action").equals(ENUMENTITYUSEACTION_ATTACK)) return; // 不是攻击实体则返回
                // 获取攻击实体的 id 和攻击者玩家并处理
                attackDetectDummy(packet.getPlayer(), (Integer) packet.getPacketValue("a"));
            }
        };
        PacketListenerFactory.addHandler(PACKETHANDLER);
        // 初始化变量
        DDKAMAP = new HashMap<>();
        DUMMYLIST = new ArrayList<>();
        DDKATIMEMAP = new HashMap<>();
        LOCATION_OFFSET = new Vector[] {
                new Vector(1d, 0d, 1d),
                new Vector(1d, 0d, -1d),
                new Vector(-1d, 0d, -1d),
                new Vector(-1d, 0d, 1d)
        };
    }

    private void detectTarget(Player target) {
        // 利用测试假人开始探测指定目标玩家
        detectTarget(target, null, 0, null);
    }

    private void detectTarget(Player target, @Nullable CommandSender owner, int option, @Nullable Boolean invisible) {
        // 利用测试假人开始探测指定目标玩家
        DDKAMAP.put(target.getName(), spawnDetectDummy(target, owner, option, invisible));

        // debug
        //target.sendMessage(getMessage("ddka -> detecting"));
        //target.sendMessage(getMessage("ddka -> dummySize: " + DUMMYLIST.size()));
    }

    private void attackDetect(Player target) {
        // 攻击检测
        if(target.hasPermission("moonlake.ddka.ignore")) return; // 玩家拥有权限则不进行检测
        int attackChance = getConfig().getInt("AutoDetect.AttackChance", 10);
        if(attackChance <= 0) return; // 值小于等于 0 则不处理
        // 获取随机几率值
        int value = (int) (Math.random() * 100);
        if(attackChance >= 100 || value <= attackChance) {
            // 设置的几率大于等于100或者值符合几率则进行检测
            detectTarget(target);
        }
    }

    private void attackDetectDummy(Player player, int entityId) {
        // 指定玩家攻击测试假人

        // debug
        //player.sendMessage(getMessage("ddka -> attackId: " + entityId));

        Map<Integer, Boolean> STATEMAP = DDKAMAP.get(player.getName());
        if(STATEMAP == null) {
            // 为 null 说明检测缓存 map 不包含此玩家
            return;
        }
        if(!STATEMAP.containsKey(entityId)) return; // 如果假人状态缓存 map 不包含攻击的实体则返回
        STATEMAP.replace(entityId, true); // 否则将假人的状态设置为 true 也就是已经被攻击
        destroyDetectDummy(player, entityId); // 并清除测试假人

        // 检测是否全部假人被攻击
        if(!STATEMAP.containsValue(Boolean.FALSE)) {
            // 不包含 false 表示全部假人都已被攻击则处理事件戳
            Long startTimestamp = DDKATIMEMAP.get(player.getName());
            if(startTimestamp != null) {
                // 不为 null 则处理用时
                long timestamp = -(System.currentTimeMillis() - startTimestamp);
                DDKATIMEMAP.put(player.getName(), timestamp);
            }
        }

        if(isAttackAutoDetect()) {
            // 如果开启了攻击自动检测则判断以及破坏的测试假人数量
            int destroyAmount = getConfig().getInt("AutoDetect.DestroyAmount", 3);
            int nowDestroyAmount = 0;
            // 遍历测试假人状态缓存 map 如果为 true 则破坏数量自加1
            for(Boolean state : STATEMAP.values())
                if(state == Boolean.TRUE)
                    nowDestroyAmount++;
            // 判断已经破坏的数量是否大于等于攻击自动检测的数量
            if(nowDestroyAmount >= destroyAmount) {
                // 则视为疑似使用杀戮光环并进行处理
                killAuraHandler(player);
                return;
            }
        }
    }

    private void killAuraHandler(Player target) {
        // 处理指定疑似杀戮光环的玩家

        // debug
        //if(target.isOp()) return;

        // 处理自定义命令
        List<String> cmdList = getConfig().getStringList("AutoDetect.KillAuraHandlerCmd");
        if(cmdList != null && !cmdList.isEmpty()) {
            // 不为 null 并且不为空则执行自定义命令
            for(String cmd : cmdList) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replaceAll("%player%", target.getName()));
            }
        }
        // 处理 KICK 或 BAN
        String handler = getConfig().getString("AutoDetect.KillAuraHandler", "KICK:疑似使用杀戮光环, 已被服务器踢出!");
        if(handler.startsWith("KICK:")) {
            // 踢出疑似杀戮光环的玩家
            MoonLakeAPI.runTaskLater(this, () -> target.kickPlayer(getMessage(handler.substring(5))), 1L);
        } else if(handler.startsWith("BAN:")) {
            // 封禁疑似杀戮光环的玩家
            MoonLakeAPI.runTaskLater(this, () -> {
                String message = handler.substring(4);
                target.kickPlayer(getMessage(message));
                Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), message, null, null);
            }, 1L);
        }
    }

    private Map<Integer, Boolean> spawnDetectDummy(Player target, @Nullable CommandSender owner, int option, @Nullable Boolean invisible) {
        // 将指定玩家生成测试假人
        if(DDKAMAP.containsKey(target.getName()))
            // 如果缓存 map 已经拥有状态测试假人则删除
            removeDetectDummy(target);

        // 否则创建新的状态测试假人数据
        Map<Integer, Boolean> STATEMAP = new HashMap<>();
        Vector[] offset = option == 0 ? LOCATION_OFFSET : option > 0 ?
                new Vector[] { LOCATION_OFFSET[0], LOCATION_OFFSET[1] } : // 前面
                new Vector[] { LOCATION_OFFSET[2], LOCATION_OFFSET[3] } ; // 后面

        for(int i = 0, offsetIndex = 0; i < getDetectDummyAmount(); i++) {
            // 生成测试假人
            if(offsetIndex >= offset.length) offsetIndex = 0; // 如果位置偏移索引超过长度则设置为 0
            Location finalLocation = target.getLocation().add(offset[offsetIndex++]); // 获取最终位置
            EntityPlayer dummy = spawnDetectDummy(target.getWorld(), getDetectDummyDisplayName(target), finalLocation);
            sendDummyPacket(target, dummy, invisible); // 发送测试假人数据包到玩家客户端
            STATEMAP.put(dummy.getId(), false); // 将测试假人的 Id 添加到缓存 map
            DUMMYLIST.add(dummy); // 将测试假人添加到缓存集合
        }
        // 添加统计时间戳并返回测试假人状态 map
        DDKATIMEMAP.put(target.getName(), System.currentTimeMillis());
        // 新建一个 task 指定时间后用于移除测试假人
        MoonLakeAPI.runTaskLater(this, () -> laterRemoveDetectDummy(target, owner), getDetectDummyDestroyTick());
        return STATEMAP;
    }

    private EntityPlayer spawnDetectDummy(World world, String displayName, Location location) {
        // 生成测试假人
        WorldServer worldServer = ((CraftWorld) world).getHandle();
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), displayName);
        PlayerInteractManager interactManager = new PlayerInteractManager(worldServer);
        EntityPlayer dummy = new EntityPlayer(server, worldServer, gameProfile, interactManager);
        dummy.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        return dummy;
    }

    private void closeDetectDummy() {
        // 关闭所有玩家的测试假人数据
        for(Player player : Bukkit.getServer().getOnlinePlayers())
            removeDetectDummy(player);
    }

    private void laterRemoveDetectDummy(Player target, @Nullable CommandSender owner) {
        // 延迟删除指定玩家的测试假人数据
        if(owner != null) {
            // 给拥有者发送测试结果
            Long timestamp = DDKATIMEMAP.get(target.getName());
            if(timestamp == null) timestamp = getDetectDummyDestroyTick(); // 为 null 则使用测试假人破坏的时间
            else if(timestamp < 0) timestamp = -timestamp; // 为负数说明没有超过破坏时间就把假人破坏完则翻转成正数
            else timestamp = System.currentTimeMillis() - timestamp; // 否则当前时间戳减去开始的世界戳

            // 统计测试信息
            Map<Integer, Boolean> STATEMAP = DDKAMAP.get(target.getName());
            int totalKilledAmount = 0;
            // 遍历测试假人状态缓存 map 如果为 true 则破坏数量自加1
            for(Boolean state : STATEMAP.values())
                if(state == Boolean.TRUE)
                    totalKilledAmount++;
            // 判断发起测试者是否为玩家, 但是没有在线则发送到控制台
            if(owner instanceof Player && !((Player) owner).isOnline()) owner = Bukkit.getConsoleSender();
            // 给发起测试者发生统计信息
            owner.sendMessage(getMessage("&6>>> 测试目标玩家 &7(&b" + target.getName() + "&7) &6的统计信息"));
            owner.sendMessage(getMessage("&6>>> 破坏假人数量: &a" + totalKilledAmount + "&8/&2" + getDetectDummyAmount()));
            owner.sendMessage(getMessage("&6>>> 测试总共用时: &a" + timestamp + "ms&8/&2" + (getDetectDummyDestroyTick() * 50) + "ms"));
        }
        removeDetectDummy(target);
    }

    private void removeDetectDummy(Player target) {
        // 删除指定玩家的测试假人数据
        if(DDKAMAP.containsKey(target.getName())) {
            // 删除玩家的测试假人
            Map<Integer, Boolean> STATEMAP = DDKAMAP.remove(target.getName());
            // 遍历玩家的测试假人状态并破坏掉
            for(Integer dummyId : STATEMAP.keySet())
                destroyDetectDummy(target, dummyId);
        }
        if(DDKATIMEMAP.containsKey(target.getName()))
            // 删除玩家的状态测试假人时间戳
            DDKATIMEMAP.remove(target.getName());

        // debug
        //target.sendMessage(getMessage("ddka -> removed"));
        //target.sendMessage(getMessage("ddka -> dummySize: " + DUMMYLIST.size()));
    }

    private void destroyDetectDummy(Player target, int dummyId) {
        // 破坏指定测试假人
        PlayerConnection playerConnection = ((CraftPlayer) target).getHandle().playerConnection;

        // 筛选测试假人集合判断是否存在对应 id 的假人则删除
        for(EntityPlayer dummy : DUMMYLIST) {
            if(dummy.getId() == dummyId) {
                playerConnection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, dummy));
                DUMMYLIST.remove(dummy);
                break;
            }
        }
        // 发送破坏实体数据包
        playerConnection.sendPacket(new PacketPlayOutEntityDestroy(dummyId));
    }

    private void sendDummyPacket(Player target, EntityPlayer dummy, @Nullable Boolean invisible) {
        // 将指定测试假人发送到指定玩家客户端
        PlayerConnection playerConnection = ((CraftPlayer) target).getHandle().playerConnection;
        playerConnection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, dummy));

        if(isDetectDummyInvisibility())
            // 测试假人拥有隐形能力则设置隐形
            dummy.setInvisible(true);

        if(invisible != null)
            // 不为 null 则设置自定义是否隐形
            dummy.setInvisible(invisible);

        // 发送测试假人生成数据包到客户端
        playerConnection.sendPacket(new PacketPlayOutNamedEntitySpawn(dummy));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 玩家加入事件判断玩家是否为此插件作者则提示消息
        Player player = event.getPlayer();
        if(player.getName().equals("Month_Light")) {
            player.sendMessage(getMessage("&aSuccess DummyDetectKillAura v" + getDescription().getVersion()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 玩家退出事件则清除测试假人数据
        removeDetectDummy(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        // 处理玩家攻击事件
        if(!(event.getEntity() instanceof Player)) return; // 如果受伤者不是玩家则返回
        if(!(event.getDamager() instanceof Player)) return; // 如果攻击者不是玩家则返回
        if(!isAttackAutoDetect()) return; // 没有开启攻击自动检测则返回
        // 否则开启了攻击自动检测则处理
        attackDetect((Player) event.getDamager());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 处理命令
        if(!sender.hasPermission("moonlake.ddka.use")) {
            // 没有权限使用
            sender.sendMessage(getMessage("&c你没有使用这个命令的权限."));
            return true;
        }
        if(args.length < 2) {
            // 错误命令
            sender.sendMessage(getMessage("&c错误, 请使用: &6/ddka <target> <front|back|all> [invisible]"));
            return true;
        }
        Player target = Bukkit.getServer().getPlayer(args[0]);
        if(target == null) {
            // 测试的目标玩家没在线
            sender.sendMessage(getMessage("&c错误, 待测试的目标玩家没有在线."));
            return true;
        }
        int option; // 测试假人位置操作
        if(args[1].equalsIgnoreCase("all")) option = 0; // 前面+后面
        else if(args[1].equalsIgnoreCase("front")) option = 1; // 前面
        else if(args[1].equalsIgnoreCase("back")) option = -1; // 后面
        else option = 0; // 前面 + 后面
        // 测试假人是否隐形
        Boolean invisible = args.length >= 3 && args[2].equalsIgnoreCase("invisible");
        // 一切都 ok 则进行测试
        detectTarget(target, sender, option, invisible);
        return true;
    }

    private void initFolder() {
        if(!getDataFolder().exists())
            getDataFolder().mkdirs();
        File config = new File(getDataFolder(), "config.yml");
        if(!config.exists())
            saveDefaultConfig();
        this.PREFIX = getConfig().getString("Prefix", "&f[&6DDKA&f] ");
    }

    private String getMessage(String message) {
        return StringUtil.toColor(PREFIX + message);
    }

    private int getDetectDummyAmount() {
        // 获取测试假人的数量
        return getConfig().getInt("DetectDummy.Amount", 4);
    }

    private String getDetectDummyDisplayName(Player target) {
        // 获取测试假人的显示名称
        String displayName = getConfig().getString("DetectDummy.DisplayName", "DDKA");
        if(displayName.length() > 16) displayName = "DDKA"; // 如果长度超过 16 则强制使用 DDKA
        // 应用测试假人的显示名称选项
        int option = getConfig().getInt("DetectDummy.DisplayNameOption", -1);
        if(option == -1) return StringUtil.toColor(displayName); // 为 -1 则什么也不操作
        // 处理测试假人名称选项
        String finalName = displayName;

        if(option == 0) {
            // 随机字符串 (不建议使用, 容易和真实玩家名冲突 (冲突几率较大))
            finalName = RandomManager.nextString(16); // 随机 16 位字符串
        } else if(option == 1) {
            // 被检测玩家名 + 随机数字 (简单安全, 可能会被高级作弊端屏蔽)
            finalName = target.getName() + RandomManager.getRandom().nextInt(99999); // 随机数 0~99999
        } else if(option == 2) {
            // 随机 UUID 截取中间 16 位 (建议使用此方式, 但是截取可能会重复)
            finalName = getDetectDummyUniqueId().substring(4, 20);
        } else if(option == 3) {
            // 随机 UUID 并 MD5 加密 (强烈建议此方式, 几乎无重复 (缺点: 运算较大))
            finalName = new MD5Encrypt(getDetectDummyUniqueId()).encrypt().to16Bit();
        }
        return finalName;
    }

    private String getDetectDummyUniqueId() {
        // 获取测试假人随机 UUID
        return RandomManager.nextUUID().toString().replace("-", "");
    }

    private boolean isDetectDummyInvisibility() {
        // 获取测试假人是否为隐形
        return getConfig().getBoolean("DetectDummy.Invisibility", true);
    }

    private long getDetectDummyDestroyTick() {
        // 获取测试假人的破坏时间
        return getConfig().getLong("DetectDummy.StayTime", 20L);
    }

    private boolean isAttackAutoDetect() {
        // 获取是否开启攻击自动检测
        return getConfig().getBoolean("AutoDetect.Enable", true);
    }

    private boolean setupMoonLake() {
        Plugin plugin = this.getServer().getPluginManager().getPlugin("MoonLake");
        return plugin != null && plugin instanceof MoonLakePlugin;
    }
}
