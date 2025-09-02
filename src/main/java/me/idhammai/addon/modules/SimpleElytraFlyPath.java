package me.idhammai.addon.modules;

// 导入必要的类和接口

import me.idhammai.addon.EasyAddon;
import me.idhammai.addon.events.impl.MoveEvent;
import me.idhammai.addon.events.impl.TravelEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class SimpleElytraFlyPath extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // 设置组：将相关设置项分组管理，提高用户界面的组织性
    private final SettingGroup sgTarget = settings.createGroup("目标坐标");
    private final SettingGroup sgFlight = settings.createGroup("飞行设置");
    private boolean isArrive = false;

    // ==================== 目标坐标设置 ====================
    // 目标X坐标：玩家要飞行到的水平位置（东西方向）
    public final Setting<Integer> globalX = sgTarget.add(new IntSetting.Builder()
        .name("目标X坐标")
        .description("目标X坐标")
        .defaultValue(0)
        .range(-30000000, 30000000)
        .sliderRange(-30000000, 30000000)
        .build()
    );

    // 目标Z坐标：玩家要飞行到的水平位置（南北方向）
    public final Setting<Integer> globalZ = sgTarget.add(new IntSetting.Builder()
        .name("目标Z坐标")
        .description("目标Z坐标")
        .defaultValue(0)
        .range(-30000000, 30000000)
        .sliderRange(-30000000, 30000000)
        .build()
    );

    // ==================== 飞行参数设置 ====================
    // 自动停止：在未加载区块中自动停止移动，防止触发反作弊系统
    public final Setting<Boolean> autoStop = sgFlight.add(new BoolSetting.Builder()
        .name("未加载区块时停止")
        .description("当区块没加载时停止飞行")
        .defaultValue(true)
        .build()
    );

    // 飞行速度：控制鞘翅飞行的移动速度
    public final Setting<Double> speed = sgFlight.add(new DoubleSetting.Builder()
        .name("飞行速度")
        .description("飞行的水平速度")
        .defaultValue(0.5)
        .min(0.1)
        .sliderMin(0.1)
        .max(3)
        .sliderMax(3)
        .build()
    );

    // 自动退出服务器：到达目标后是否自动断开连接
    public final Setting<Boolean> autoQuitServer = sgFlight.add(new BoolSetting.Builder()
        .name("到达后自动退出服务器")
        .description("到达目标点后自动退出服务器")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> autoTakeoff = sgFlight.add(new BoolSetting.Builder()
        .name("空中起飞")
        .description("空中起飞")
        .defaultValue(true)
        .build()
    );

    // ==================== 到达判断设置 ====================
    // 2D距离到达阈值：控制水平方向（X-Z平面）的到达判断精度
    // 这个设置决定了玩家在水平面上距离目标多近时被认为"已到达"
    public final Setting<Double> arrivalDistance2D = sgFlight.add(new DoubleSetting.Builder()
        .name("到达距离阈值")
        .description("距离目标点多少格才算到达")
        .defaultValue(1)
        .min(1)
        .sliderMin(0.1)
        .max(Integer.MAX_VALUE)
        .sliderMax(256)
        .build()
    );


    // ==================== 状态变量 ====================
    private BlockPos target;                // 目标位置的坐标（改用BlockPos）

    /**
     * 构造函数：初始化模块基本信息
     */
    public SimpleElytraFlyPath() {
        super(EasyAddon.CATEGORY, "simple-elytra-fly-path", "鞘翅自动寻路飞行");
    }

    /**
     * 模块激活时的处理逻辑
     * 设置目标位置，初始化状态变量，开始导航
     */
    @Override
    public void onActivate() {
        // 安全检查：确保玩家存在
        if (mc.player == null || mc.world == null || !checkElytra()) {
            toggle();
            return;
        }

        // Y坐标高度检测
        if (!checkValidHeight()) {
            ChatUtils.error("只能在各维度高度上限之上使用：地狱(Y>128)、主世界(Y>320)、末地(Y>256)");
            toggle();
            return;
        }

        // 禁用创造模式飞行，确保使用鞘翅飞行
        if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
        mc.player.getAbilities().flying = false;

        if (autoTakeoff.get() && !mc.player.isFallFlying()) {
            recastElytra(mc.player);
        }

        // 向玩家显示开始导航的信息
        ChatUtils.info("开始寻路到坐标: X=%d, Z=%d", globalX.get(), globalZ.get());
    }

    /**
     * 模块停用时的清理逻辑
     * 重置所有状态变量，停止飞行
     */
    @Override
    public void onDeactivate() {
        target = null;
        isArrive = false;

        // 如果玩家存在，禁用飞行能力
        if (mc.player != null) {
            if (!mc.player.isCreative()) mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
    }

    /**
     * 玩家移动事件处理
     * 主要用于在未加载区块中停止移动，防止触发反作弊系统
     */
    @EventHandler
    public void onPlayerMove(MoveEvent event) {
        if (mc.player == null) return;

        // 如果玩家正在鞘翅飞行
        if (mc.player.isFallFlying()) {
            // 计算玩家当前所在的区块坐标
            // Minecraft中每个区块是16x16格，所以除以16得到区块坐标
            int chunkX = (int) ((mc.player.getX()) / 16);
            int chunkZ = (int) ((mc.player.getZ()) / 16);

            // 如果启用了自动停止功能
            if (autoStop.get()) {
                // 检查当前区块是否已加载
                if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    // 在未加载区块中停止所有移动，防止触发反作弊
                    event.setX(0);
                    event.setY(0);
                    event.setZ(0);
                }
            }
        }

    }


    @EventHandler
    public void onTick(TickEvent.Pre event) {
        // 基础检查：玩家存在且处于导航模式
        if (mc.player == null) return;

        // 自动起飞逻辑
        if (autoTakeoff.get() && !mc.player.isFallFlying()) {
            recastElytra(mc.player);
        }

        if (isArrive && autoQuitServer.get()) {
            mc.world.disconnect();
        }
    }


    /**
     * 玩家移动事件处理（TravelEvent）
     * 这是核心的飞行控制逻辑，负责计算飞行方向和速度
     */
    @EventHandler
    public void onMove(TravelEvent event) {
        // 基础安全检查
        if (mc.player == null || mc.world == null || !mc.player.isFallFlying() || !checkElytra() || event.isPost()) {
            return;
        }

        // 根据设置的坐标创建目标位置（改用BlockPos）
        target = new BlockPos(globalX.get(), 0, globalZ.get());

        // 玩家坐标
        Vec3d playerPos = mc.player.getPos();
        // 目标方块坐标 - 修改这里
        Vec3d targetPos = target.toCenterPos();
        // 计算玩家到目标方块的距离差
        double deltaX = targetPos.x - playerPos.x;
        double deltaY = targetPos.y - playerPos.y;
        double deltaZ = targetPos.z - playerPos.z;

        // 计算2D距离（水平距离）
        double distance2D = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        // 将方向向量标准化为单位向量
        Vec3d direction = new Vec3d(deltaX, deltaY, deltaZ).normalize();

        if (distance2D > arrivalDistance2D.get()) { // 需要水平移动
            setX(direction.x * speed.get());
            setY(0);
            setZ(direction.z * speed.get());
        }else{
            setX(0);
            setY(0);
            setZ(0);

            isArrive = true;
        }

        // 应用空气阻力
        setY(getY() * 0.9900000095367432D);
        setX(getX() * 0.9800000190734863D);
        setZ(getZ() * 0.9900000095367432D);

        // 应用移动
        event.cancel();
        mc.player.move(MovementType.SELF, mc.player.getVelocity());
    }

    // ==================== 工具方法 ====================

    /**
     * 检查鞘翅装备状态（改进版本，包含耐久度检查）
     */
    private boolean checkElytra() {
        // 检测玩家是否装备了鞘翅并且可用
        for (ItemStack is : mc.player.getArmorItems()) {
            if (is.getItem() instanceof ElytraItem && ElytraItem.isUsable(is)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查玩家是否在允许的高度范围内
     * 必须在各维度高度上限之上
     * 地狱: Y > 128 (基岩层之上)
     * 主世界: Y > 320 (建筑高度上限之上)
     * 末地: Y > 256 (高度上限之上)
     */
    private boolean checkValidHeight() {
        if (mc.player == null || mc.world == null) return false;

        double playerY = mc.player.getY();
        String dimensionName = mc.world.getRegistryKey().getValue().toString();

        switch (dimensionName) {
            case "minecraft:the_nether":
                // 地狱顶层基岩层之上 (Y > 128)
                return playerY > 128;
            case "minecraft:overworld":
                // 主世界建筑高度上限之上 (Y > 320)
                return playerY > 320;
            case "minecraft:the_end":
                // 末地高度上限之上 (Y > 256)
                return playerY > 256;
            default:
                return false;
        }
    }

    /**
     * 重新激活鞘翅飞行
     * 发送网络包给服务器，请求开始鞘翅飞行
     */
    public static boolean recastElytra(ClientPlayerEntity player) {
        if (checkConditions(player) && ignoreGround(player)) {
            // 发送开始鞘翅飞行的网络包
            player.networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return true;
        }
        return false;
    }

    /**
     * 检查鞘翅飞行的前置条件
     * 确保玩家可以安全地开始鞘翅飞行
     */
    public static boolean checkConditions(ClientPlayerEntity player) {
        ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
        return (!player.getAbilities().flying &&    // 不在创造模式飞行
            !player.hasVehicle() &&                 // 不在载具中
            !player.isClimbing() &&                 // 不在攀爬
            itemStack.isOf(Items.ELYTRA) &&         // 装备了鞘翅
            ElytraItem.isUsable(itemStack));        // 鞘翅可用（有耐久度）
    }

    /**
     * 忽略地面检测，强制启动鞘翅飞行
     * 即使在地面上也可以启动鞘翅飞行
     */
    private static boolean ignoreGround(ClientPlayerEntity player) {
        if (!player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.LEVITATION)) {
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
                player.startFallFlying(); // 开始鞘翅飞行
                return true;
            }
        }
        return false;
    }


    // ==================== 速度获取和设置方法 ====================

    /**
     * 获取玩家当前的X轴速度
     */
    private double getX() {
        return mc.player.getVelocity().x;
    }

    /**
     * 获取玩家当前的Y轴速度
     */
    private double getY() {
        return mc.player.getVelocity().y;
    }

    /**
     * 获取玩家当前的Z轴速度
     */
    private double getZ() {
        return mc.player.getVelocity().z;
    }

    /**
     * 设置玩家的X轴速度
     * 保持Y和Z轴速度不变
     */
    private void setX(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        Vec3d newVel = new Vec3d(f, currentVel.y, currentVel.z);
        mc.player.setVelocity(newVel);
    }

    /**
     * 设置玩家的Y轴速度
     * 保持X和Z轴速度不变
     */
    private void setY(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        Vec3d newVel = new Vec3d(currentVel.x, f, currentVel.z);
        mc.player.setVelocity(newVel);
    }

    /**
     * 设置玩家的Z轴速度
     * 保持X和Y轴速度不变
     */
    private void setZ(double f) {
        Vec3d currentVel = mc.player.getVelocity();
        Vec3d newVel = new Vec3d(currentVel.x, currentVel.y, f);
        mc.player.setVelocity(newVel);
    }


}
