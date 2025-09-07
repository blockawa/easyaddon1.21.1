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

    /* ---------------- 配置项 ---------------- */

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
            .min(0).max(10)
            .sliderMin(0).sliderMax(10)
            .build()
    );

    public final Setting<Integer> joinQueueDelay = sgGeneral.add(new IntSetting.Builder()
            .name("加入队列延迟")
            .description("右键指南针加入队列等待的时间，单位秒")
            .defaultValue(2)
            .min(0).max(10)
            .sliderMin(0).sliderMax(10)
            .build()
    );

    public final Setting<Integer> containerClickDelay = sgGeneral.add(new IntSetting.Builder()
            .name("容器点击延迟")
            .description("在容器中点击指南针的延迟时间，单位秒")
            .defaultValue(2)
            .min(0).max(10)
            .sliderMin(0).sliderMax(10)
            .build()
    );

    /* -------------- 构造器 ---------------- */

    public AutoLoginXin() {
        super(EasyAddon.CATEGORY, "auto-login-xin", "自动登录+点击指南针加入队列");
        INSTANCE = this;
        MeteorClient.EVENT_BUS.subscribe(new StaticListener());
    }

    /* -------------- 主逻辑 ---------------- */

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        /* 1. 发送登录命令 */
        if (login && timer.passedS(afterLoginTime.get())) {
            mc.getNetworkHandler().sendChatCommand("login " + password.get());
            login = false;          // 登录命令已发出
        }

        /* 2. 容器界面点击指南针 */
        if (mc.currentScreen instanceof GenericContainerScreen) {
            GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
            var handler = screen.getScreenHandler();

            boolean found = false;
            for (int i = 0; i < handler.slots.size(); i++) {
                var slot = handler.slots.get(i);
                if (slot.hasStack() && slot.getStack().getItem() == Items.COMPASS) {
                    found = true;
                    if (containerTimer.passedS(containerClickDelay.get())) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                        containerTimer.reset();   // 点完再重新开始计时
                    }
                    break;
                }
            }
            if (!found) containerTimer.reset();   // 没找到指南针也重置
        } else {
            containerTimer.reset();               // 界面关了立即重置
        }

        /* 3. 热键栏右键指南针加入队列 */
        var compass = InvUtils.find(Items.COMPASS);
        if (compass.isHotbar()) {
            if (queueTimer.passedS(joinQueueDelay.get())) {
                InvUtils.swap(compass.slot(), false);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                queueTimer.reset();
            }
        } else {
            queueTimer.reset();                   // 指南针不在热键栏立即重置
        }
    }

    /* -------------- 小工具 ---------------- */

    private boolean isInLoginLobby() {
        if (mc.player == null) return false;
        var pos = mc.player.getBlockPos();
        return pos.getX() == 8 && pos.getY() == 5 && pos.getZ() == 8;
    }

    /* -------------- 事件监听 -------------- */

    private class StaticListener {
        @EventHandler
        private void onGameJoined(ServerConnectBeginEvent event) {
            login = true;
            timer.reset();
            queueTimer.reset();
            containerTimer.reset();
        }
    }
}
