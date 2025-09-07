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

    /* ------------- 计时器 ------------- */
    private final Timer timer        = new Timer(); // 登录命令
    private final Timer queueTimer   = new Timer(); // 主手右键
    private final Timer containerTimer = new Timer(); // 容器点击

    /* ------------- 标记位 ------------- */
    private boolean login                     = false; // 是否要发登录命令
    private boolean hasRightClickedCompass    = false; // 是否已主手右键过指南针

    /* ------------- 配置项 ------------- */
    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
            .name("登录密码")
            .description("Xin服登录密码")
            .defaultValue("123456")
            .build());

    public final Setting<Integer> afterLoginTime = sgGeneral.add(new IntSetting.Builder()
            .name("输入密码延迟")
            .description("输入密码前等待的时间，单位秒")
            .defaultValue(2).min(0).max(10).sliderRange(0, 10)
            .build());

    public final Setting<Integer> joinQueueDelay = sgGeneral.add(new IntSetting.Builder()
            .name("加入队列延迟")
            .description("右键指南针加入队列等待的时间，单位秒")
            .defaultValue(2).min(0).max(10).sliderRange(0, 10)
            .build());

    public final Setting<Integer> containerClickDelay = sgGeneral.add(new IntSetting.Builder()
            .name("容器点击延迟")
            .description("在容器中点击指南针的延迟时间，单位秒")
            .defaultValue(2).min(0).max(10).sliderRange(0, 10)
            .build());

    /* ------------- 构造 ------------- */
    public AutoLoginXin() {
        super(EasyAddon.CATEGORY, "auto-login-xin", "自动登录 + 自动加入 Xin 服队列");
        INSTANCE = this;
        MeteorClient.EVENT_BUS.subscribe(new StaticListener());
    }

    /* ------------- 逻辑核心 ------------- */
    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        /* 1. 发送登录命令（仅一次） */
        if (login && timer.passedS(afterLoginTime.get())) {
            mc.getNetworkHandler().sendChatCommand("login " + password.get());
            login = false;
        }

        /* 2. 只在登录大厅继续 */
        if (!isInLoginLobby()) return;

        /* 3. 主手右键指南针（仅一次） */
        if (!hasRightClickedCompass && queueTimer.passedS(joinQueueDelay.get())) {
            var found = InvUtils.find(Items.COMPASS);
            if (found.isHotbar()) {
                InvUtils.swap(found.slot(), false);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                queueTimer.reset();
                hasRightClickedCompass = true;   // 标记已右键
            }
        }

        /* 4. 容器里点指南针（可反复，直到成功） */
        if (mc.currentScreen instanceof GenericContainerScreen
                && containerTimer.passedS(containerClickDelay.get())) {

            var handler = ((GenericContainerScreen) mc.currentScreen).getScreenHandler();
            for (int i = 0; i < handler.slots.size(); i++) {
                var slot = handler.slots.get(i);
                if (slot.hasStack() && slot.getStack().isOf(Items.COMPASS)) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                    containerTimer.reset();
                    return;          // 点完等下一 tick
                }
            }
        }
    }

    /* ------------- 辅助方法 ------------- */
    private boolean isInLoginLobby() {
        if (mc.player == null) return false;
        var pos = mc.player.getBlockPos();
        return pos.getX() == 8 && pos.getY() == 5 && pos.getZ() == 8;
    }

    /* ------------- 静态监听器 ------------- */
    private class StaticListener {
        @EventHandler
        private void onGameJoined(ServerConnectBeginEvent event) {
            login                  = true;
            hasRightClickedCompass = false;
            timer.reset();
            queueTimer.reset();
            containerTimer.reset();
        }
    }
}
