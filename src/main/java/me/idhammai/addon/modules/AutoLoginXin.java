package me.idhammai.addon.modules;

import me.idhammai.addon.EasyAddon;
import me.idhammai.addon.utils.math.Timer;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.ServerConnectBeginEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class AutoLoginXin extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public static AutoLoginXin INSTANCE;

    /* ---------- 计时器 ---------- */
    private final Timer queueTimer   = new Timer();
    private final Timer timer        = new Timer();
    private final Timer containerTimer = new Timer();

    /* ---------- 状态标记 ---------- */
    private boolean login = false;
    private boolean hasAttemptedJoinQueue = false;
    private boolean needSwapCompass = false;   // 是否等待切槽完成
    private int targetCompassSlot = -1;        // 目标指南针槽位

    /* ---------- 配置项 ---------- */
    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
        .name("登录密码")
        .description("Xin服登录密码")
        .defaultValue("123456")
        .build()
    );

    public final Setting<Integer> afterLoginTime = sgGeneral.add(new IntSetting.Builder()
        .name("输入密码延迟")
        .description("输入密码前等待的时间，单位秒")
        .defaultValue(2)
        .min(0).max(10).sliderMin(0).sliderMax(10)
        .build()
    );

    public final Setting<Integer> joinQueueDelay = sgGeneral.add(new IntSetting.Builder()
        .name("加入队列延迟")
        .description("右键指南针加入队列等待的时间，单位秒")
        .defaultValue(2)
        .min(0).max(10).sliderMin(0).sliderMax(10)
        .build()
    );

    public final Setting<Integer> containerClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("容器点击延迟")
        .description("在容器中点击指南针的延迟时间，单位秒")
        .defaultValue(2)
        .min(0).max(10).sliderMin(0).sliderMax(10)
        .build()
    );

    public AutoLoginXin() {
        super(EasyAddon.CATEGORY, "auto-login-xin", "auto-login-xin");
        INSTANCE = this;
        MeteorClient.EVENT_BUS.subscribe(new StaticListener());
    }

    /* ====================== 逻辑 ====================== */
    private boolean isInLoginLobby() {
        if (mc.player == null) return false;
        var pos = mc.player.getBlockPos();
        return pos.getX() == 8 && pos.getY() == 5 && pos.getZ() == 8;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        /* 1. 发送密码 */
        if (login && timer.passedS(afterLoginTime.get())) {
            mc.getNetworkHandler().sendChatCommand("login " + password.get());
            login = false;
        }

        /* 2. 登录完成后才处理队列逻辑 */
        if (!login && isInLoginLobby()) {

            /* 2.1 容器内点击指南针（与之前相同） */
            if (mc.currentScreen instanceof GenericContainerScreen
                && containerTimer.passedS(containerClickDelay.get())) {

                GenericContainerScreen containerScreen = (GenericContainerScreen) mc.currentScreen;
                var handler = containerScreen.getScreenHandler();
                for (int i = 0; i < handler.slots.size(); i++) {
                    var slot = handler.slots.get(i);
                    if (slot.hasStack() && slot.getStack().getItem() == Items.COMPASS) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0,
                                SlotActionType.PICKUP, mc.player);
                        containerTimer.reset();
                        break;
                    }
                }
            }

            /* 2.2 热栏指南针：两步执行，防止切槽未同步 */
            if (!hasAttemptedJoinQueue) {
                /* --- 第一步：需要切换时，先切换 --- */
                if (!needSwapCompass
                    && InvUtils.find(Items.COMPASS).isHotbar()
                    && queueTimer.passedS(joinQueueDelay.get())) {

                    int slot = InvUtils.find(Items.COMPASS).slot();
                    if (mc.player.getInventory().selectedSlot != slot) {
                        needSwapCompass = true;
                        targetCompassSlot = slot;
                        InvUtils.swap(slot, false);   // 切换到指南针
                        queueTimer.reset();        // 给客户端 0.5 秒同步
                        return;                       // 本 tick 不再继续
                    }
                }

                /* --- 第二步：切槽完成后右键使用 --- */
                if (needSwapCompass && queueTimer.passedS(0.5)) {
                    needSwapCompass = false;
                    targetCompassSlot = -1;
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    hasAttemptedJoinQueue = true;
                    queueTimer.reset();
                }
            }
        }
    }

    /* ====================== 重连重置 ====================== */
    private class StaticListener {
        @EventHandler
        private void onGameJoined(ServerConnectBeginEvent event) {
            login = true;
            hasAttemptedJoinQueue = false;
            needSwapCompass = false;
            targetCompassSlot = -1;
            timer.reset();
            containerTimer.reset();
            queueTimer.reset();
        }
    }
}
