package me.idhammai.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import me.idhammai.addon.EasyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;

public class NetherElytraPath extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private State currenState;

    // 目标坐标设置
    private final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
            .name("目标X坐标")
            .description("目标X坐标")
            .defaultValue(0)
            .range(-30000000, 30000000)
            .sliderRange(-30000000, 30000000)
            .build());

    private final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
            .name("目标Z坐标")
            .description("目标Z坐标")
            .defaultValue(0)
            .range(-30000000, 30000000)
            .sliderRange(-30000000, 30000000)
            .build());

    public NetherElytraPath() {
        super(EasyAddon.CATEGORY, "nether-elytra-path", "地狱鞘翅自动寻路飞行");
    }

    @Override
    public void onActivate() {
        if (mc.player == null)
            return;

        currenState = State.Pathing;

        // 设置Baritone API参数（xin服最佳参数）
        Settings settings = BaritoneAPI.getSettings();
        settings.elytraTermsAccepted.value = true;
        settings.elytraNetherSeed.value = 3763250021837776656L;
        settings.elytraPredictTerrain.value = true;
        settings.elytraAutoJump.value = true;

        // 屏蔽自动降落参数
        settings.elytraMinFireworksBeforeLanding.value = -1;
        settings.elytraMinimumDurability.value = -1;

        // 执行Baritone命令
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager()
                .execute("goal " + targetX.get() + " ~ " + targetZ.get());
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
    }

    @Override
    public void onDeactivate() {
        // 取消Baritone寻路
        BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();

        currenState = State.Completed;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null)
            return;

        if (currenState == State.Pathing) {
            // 检查烟花数量
            int fireworkCount = InvUtils.find(Items.FIREWORK_ROCKET).count();

            // 如果烟花数量小于等于5，改变目标坐标为当前玩家位置，让Baritone自动降落
            if (fireworkCount <= 5) {
                int currentX = mc.player.getBlockX();
                int currentZ = mc.player.getBlockZ();

                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager()
                        .execute("goal " + currentX + " ~ " + currentZ);

                currenState = State.Landing;
            }
        }

        if (currenState == State.Landing && !BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            currenState = State.Replenishing;
            return;
        }

        if (currenState == State.Replenishing) {
            // 在背包中寻找有烟花的潜影盒 -> 拿在手上 -> 放到地上 -> 打开 -> 拿取烟花 -> 关闭容器界面 -> 挖潜影盒 -> 捡起潜影盒(如果为空潜影盒不捡起)
        }

    }

    private enum State {
        Pathing,
        Landing,
        Replenishing,
        Completed
    }
}
