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

    private final Timer queueTimer = new Timer();
    private final Timer timer = new Timer();
    private final Timer containerTimer = new Timer();

    private boolean login = false;

    // ✅ 状态变量：防止重复操作
    private boolean hasClickedCompassInContainer = false;
    private boolean hasJoinedQueue = false;

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
        .min(0)
        .max(10)
        .sliderMin(0)
        .sliderMax(10)
        .build()
    );

    public final Setting<Integer> joinQueueDelay = sgGeneral.add(new IntSetting.Builder()
        .name("加入队列延迟")
        .description("右键指南针加入队列等待的时间，单位秒")
        .defaultValue(2)
        .min(0)
        .max(10)
        .sliderMin(0)
        .sliderMax(10)
        .build()
    );

    public final Setting<Integer> containerClickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("容器点击延迟")
        .description("在容器中点击指南针的延迟时间，单位秒")
        .defaultValue(2)
        .min(0)
        .max(10)
        .sliderMin(0)
        .sliderMax(10)
        .build()
    );

    public AutoLoginXin() {
        super(EasyAddon.CATEGORY, "auto-login-xin", "自动登录 Xin 服并加入队列");
        INSTANCE = this;
        MeteorClient.EVENT_BUS.subscribe(new StaticListener());
    }

    private boolean isInLoginLobby() {
        if (mc.player == null) return false;
        var pos = mc.player.getBlockPos();
        return pos.getX() == 8 && pos.getY() == 5 && pos.getZ() == 8;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // ✅ 登录命令
        if (login && timer.passedS(afterLoginTime.get())) {
            System.out.println("login " + password.get());
            mc.getNetworkHandler().sendChatCommand("login " + password.get());
            login = false;
        }

        // ✅ 只在登录大厅执行
        if (isInLoginLobby()) {
            // ✅ 容器内点击指南针（仅一次）
            if (mc.currentScreen instanceof GenericContainerScreen
                && !hasClickedCompassInContainer
                && containerTimer.passedS(containerClickDelay.get())) {

                var handler = ((GenericContainerScreen) mc.currentScreen).getScreenHandler();
                for (int i = 0; i < handler.slots.size(); i++) {
                    var slot = handler.slots.get(i);
                    if (slot.hasStack() && slot.getStack().getItem() == Items.COMPASS) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        containerTimer.reset();
                        hasClickedCompassInContainer = true;
                        break;
                    }
                }
            }

            // ✅ 右键指南针加入队列（仅一次）
            if (!hasJoinedQueue
                && InvUtils.find(Items.COMPASS).isHotbar()
                && queueTimer.passedS(joinQueueDelay.get())) {

                InvUtils.swap(InvUtils.find(Items.COMPASS).slot(), false);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                queueTimer.reset();
                hasJoinedQueue = true;
            }
        }
    }

    private class StaticListener {
        @EventHandler
        private void onGameJoined(ServerConnectBeginEvent event) {
            login = true;
            timer.reset();
            containerTimer.reset();
            queueTimer.reset();

            // ✅ 重置状态
            hasClickedCompassInContainer = false;
            hasJoinedQueue = false;
        }
    }
}
