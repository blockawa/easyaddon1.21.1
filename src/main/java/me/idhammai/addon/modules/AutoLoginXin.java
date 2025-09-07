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

    /* 状态标记 */
    private boolean login = false;
    private boolean hasLoggedIn = false;
    private boolean hasJoinedQueue = false;
    private boolean hasClickedCompassInContainer = false;

    /* 计时器 */
    private final Timer loginTimer = new Timer();
    private final Timer queueTimer = new Timer();
    private final Timer containerTimer = new Timer();

    /* 设置项 */
    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
        .name("登录密码")
        .description("Xin服登录密码")
        .defaultValue("123456")
        .build()
    );

    private final Setting<Integer> afterLoginTime = sgGeneral.add(new IntSetting.Builder()
        .name("输入密码延迟")
        .description("输入密码前等待的时间，单位秒")
        .defaultValue(2).min(0).max(10).sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> joinQueueDelay = sgGeneral.add(new IntSetting.Builder()
        .name("加入队列延迟")
        .description("右键指南针加入队列等待的时间，单位秒")
        .defaultValue(2).min(0).max(10).sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> containerClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("容器点击延迟")
        .description("在容器中点击指南针的延迟时间，单位秒")
        .defaultValue(2).min(0).max(10).sliderRange(0, 10)
        .build()
    );

    public AutoLoginXin() {
        super(EasyAddon.CATEGORY, "auto-login-xin", " Xin服自动登录 + 自动加入队列");
        INSTANCE = this;
        MeteorClient.EVENT_BUS.subscribe(new StaticListener());
    }

    /* 登录大厅坐标判断（可选） */
    private boolean isInLoginLobby() {
        if (mc.player == null) return false;
        var pos = mc.player.getBlockPos();
        return pos.getX() == 8 && pos.getY() == 5 && pos.getZ() == 8;
    }

    /* 是否处于登录后阶段：只要还没标记已登录，或者仍在登录大厅，都允许继续 */
    private boolean isPostLoginPhase() {
        return !hasLoggedIn || isInLoginLobby();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        /* 1. 发送登录命令 */
        if (!hasLoggedIn && login && loginTimer.passedS(afterLoginTime.get())) {
            mc.getNetworkHandler().sendChatCommand("login " + password.get());
            hasLoggedIn = true;   // 标记已发送
            login = false;
        }

        /* 2. 登录后阶段：容器点击 + 加入队列 */
        if (isPostLoginPhase()) {

            /* 2.1 容器内点击指南针 */
            if (!hasClickedCompassInContainer
                && mc.currentScreen instanceof GenericContainerScreen
                && containerTimer.passedS(containerClickDelay.get())) {

                var handler = ((GenericContainerScreen) mc.currentScreen).getScreenHandler();
                for (int i = 0; i < handler.slots.size(); i++) {
                    var slot = handler.slots.get(i);
                    if (slot.hasStack() && slot.getStack().getItem() == Items.COMPASS) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        hasClickedCompassInContainer = true;
                        containerTimer.reset();
                        break;
                    }
                }
            }

            /* 2.2 主手右键指南针加入队列 */
            if (!hasJoinedQueue
                && InvUtils.find(Items.COMPASS).isHotbar()
                && queueTimer.passedS(joinQueueDelay.get())) {

                InvUtils.swap(InvUtils.find(Items.COMPASS).slot(), false);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                hasJoinedQueue = true;
                queueTimer.reset();
            }
        }
    }

    /* 静态内部类：监听重连事件 */
    private class StaticListener {
        @EventHandler
        private void onGameJoined(ServerConnectBeginEvent event) {
            /* 全部状态重置 */
            login = true;
            hasLoggedIn = false;
            hasJoinedQueue = false;
            hasClickedCompassInContainer = false;
            loginTimer.reset();
            queueTimer.reset();
            containerTimer.reset();
        }
    }
}
