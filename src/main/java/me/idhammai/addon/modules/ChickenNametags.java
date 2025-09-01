package me.idhammai.addon.modules;

import me.idhammai.addon.EasyAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.*;

public class ChickenNametags extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // 基本设置
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("缩放大小")
        .description("名称标签的缩放大小。")
        .defaultValue(1.1)
        .min(0.1)
        .build()
    );

    private final Setting<Boolean> displayHealth = sgGeneral.add(new BoolSetting.Builder()
        .name("血量显示")
        .description("显示鸡的血量。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> displayDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("距离显示")
        .description("显示与鸡的距离。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> maxRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("渲染距离")
        .description("只渲染此距离内的鸡名称标签。")
        .defaultValue(64)
        .min(1)
        .sliderMax(256)
        .build()
    );

    // 渲染设置
    private final Setting<SettingColor> background = sgRender.add(new ColorSetting.Builder()
        .name("标签背景色")
        .description("名称标签背景颜色。")
        .defaultValue(new SettingColor(0, 0, 0, 75))
        .build()
    );

    private final Setting<SettingColor> nameColor = sgRender.add(new ColorSetting.Builder()
        .name("名字颜色")
        .description("名称文字颜色。")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> distanceColor = sgRender.add(new ColorSetting.Builder()
        .name("距离文字颜色")
        .description("距离文字的颜色。")
        .defaultValue(new SettingColor(150, 150, 150))
        .visible(displayDistance::get)
        .build()
    );

    // 颜色常量
    private final Color RED = new Color(255, 25, 25);
    private final Color AMBER = new Color(255, 105, 25);
    private final Color GREEN = new Color(25, 252, 25);

    private final Vector3d pos = new Vector3d();
    private final List<ChickenEntity> chickenList = new ArrayList<>();

    public ChickenNametags() {
        super(EasyAddon.CATEGORY, "chicken-nametags", "显示鸡实体的自定义名称标签。");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        chickenList.clear();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        // 遍历世界中的所有实体，找到鸡
        for (Entity entity : mc.world.getEntities()) {
            if (entity.getType() != EntityType.CHICKEN) continue;

            ChickenEntity chicken = (ChickenEntity) entity;
            double distance = PlayerUtils.distanceToCamera(chicken);

            // 检查距离限制
            if (distance <= maxRange.get()) {
                chickenList.add(chicken);
            }
        }

        // 按距离排序，近的在前
        chickenList.sort(Comparator.comparing(e -> e.squaredDistanceTo(cameraPos)));
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        boolean shadow = Config.get().customFont.get();

        for (ChickenEntity chicken : chickenList) {
            Utils.set(pos, chicken, event.tickDelta);
            pos.add(0, getHeight(chicken), 0);

            if (NametagUtils.to2D(pos, scale.get())) {
                renderChickenNametag(chicken, shadow);
            }
        }
    }

    private double getHeight(Entity entity) {
        return entity.getEyeHeight(entity.getPose()) + 0.5;
    }

    private void renderChickenNametag(ChickenEntity chicken, boolean shadow) {
        TextRenderer text = TextRenderer.get();
        NametagUtils.begin(pos);

        // 名称 - 修改这部分逻辑
        String nameText = "鸡"; // 默认显示"鸡"
        if (chicken.hasCustomName()) {
            nameText = chicken.getCustomName().getString(); // 如果有自定义名称，显示自定义名称
        }

        // 血量
        String healthText = "";
        Color healthColor = GREEN;
        if (displayHealth.get()) {
            float absorption = chicken.getAbsorptionAmount();
            int health = Math.round(chicken.getHealth() + absorption);
            double healthPercentage = health / (chicken.getMaxHealth() + absorption);

            healthText = " " + health;

            if (healthPercentage <= 0.333) healthColor = RED;
            else if (healthPercentage <= 0.666) healthColor = AMBER;
            else healthColor = GREEN;
        }

        // 距离
        String distanceText = "";
        if (displayDistance.get()) {
            double dist = Math.round(PlayerUtils.distanceToCamera(chicken) * 10.0) / 10.0;
            distanceText = " [" + dist + "m]";
        }

        // 计算宽度
        double nameWidth = text.getWidth(nameText, shadow);
        double healthWidth = displayHealth.get() ? text.getWidth(healthText, shadow) : 0;
        double distanceWidth = displayDistance.get() ? text.getWidth(distanceText, shadow) : 0;
        double heightDown = text.getHeight(shadow);

        double width = nameWidth + healthWidth + distanceWidth;
        double widthHalf = width / 2;

        // 绘制背景
        drawBg(-widthHalf, -heightDown, width, heightDown);

        // 渲染文字
        text.beginBig();
        double hX = -widthHalf;
        double hY = -heightDown;

        // 渲染名称
        hX = text.render(nameText, hX, hY, nameColor.get(), shadow);

        // 渲染血量
        if (displayHealth.get()) {
            hX = text.render(healthText, hX, hY, healthColor, shadow);
        }

        // 渲染距离
        if (displayDistance.get()) {
            text.render(distanceText, hX, hY, distanceColor.get(), shadow);
        }

        text.end();
        NametagUtils.end();
    }

    private void drawBg(double x, double y, double width, double height) {
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x - 1, y - 1, width + 2, height + 2, background.get());
        Renderer2D.COLOR.render(null);
    }

    @Override
    public String getInfoString() {
        return Integer.toString(chickenList.size());
    }
}
