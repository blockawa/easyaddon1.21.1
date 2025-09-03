package me.idhammai.addon.modules;

import me.idhammai.addon.EasyAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BaseFinderXin extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgRestart = settings.createGroup("Restart");
    private ChunkPos originChunk;
    private ChunkPos targetChunk;
    private int currentCircle;
    private PathEnum currentPath;
    private boolean isBack;
    private int turnDelayTimer;

    // 设置玩家当前世界的加载的区块范围
    public final Setting<Integer> chunkLoadRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("The current server maximum distance to load chunk")
        .defaultValue(5)
        .min(2)
        .max(10)
        .sliderMin(2)
        .sliderMax(10)
        .build()
    );

    // 设置搜索圈数
    public final Setting<Integer> circleLimit = sgGeneral.add(new IntSetting.Builder()
        .name("circle-limit")
        .description("circle-limit")
        .defaultValue(50)
        .min(2)
        .max(100)
        .sliderMin(2)
        .sliderMax(100)
        .build()
    );

    // 转向延迟tick
    private final Setting<Integer> turnDelay = sgGeneral.add(new IntSetting.Builder()
        .name("turn-delay")
        .description("turn-delay")
        .defaultValue(40)
        .min(1)
        .max(100)
        .sliderMin(1)
        .sliderMax(100)
        .build()
    );

    // 修复卡顿
    private final Setting<Boolean> waitChunkLoad = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-chunk-load")
        .description("wait-chunk-load")
        .defaultValue(true)
        .build()
    );

    // 是否从上次开始
    private final Setting<Boolean> lastBegin = sgRestart.add(new BoolSetting.Builder()
        .name("last-begin")
        .description("last-begin")
        .defaultValue(false)
        .build()
    );

    // 上次的圈数
    private final Setting<Integer> lastCircle = sgRestart.add(new IntSetting.Builder()
        .name("last-circle")
        .description("last-circle")
        .visible(lastBegin::get)
        .defaultValue(0)
        .min(0)
        .max(1000)
        .sliderMin(0)
        .sliderMax(100)
        .build()
    );

    // 上次暂停的区块X
    private final Setting<Integer> lastChunkX = sgRestart.add(new IntSetting.Builder()
        .name("last-chunk-x")
        .description("last-chunk-x")
        .visible(lastBegin::get)
        .defaultValue(0)
        .min(Integer.MIN_VALUE)
        .max(Integer.MAX_VALUE)
        .sliderMin(Integer.MIN_VALUE)
        .sliderMax(Integer.MAX_VALUE)
        .build()
    );

    // 上次暂停的区块Z
    private final Setting<Integer> lastChunkZ = sgRestart.add(new IntSetting.Builder()
        .name("last-chunk-z")
        .description("last-chunk-z")
        .visible(lastBegin::get)
        .defaultValue(0)
        .min(Integer.MIN_VALUE)
        .max(Integer.MAX_VALUE)
        .sliderMin(Integer.MIN_VALUE)
        .sliderMax(Integer.MAX_VALUE)
        .build()
    );

    // 上次到哪个方向了
    private final Setting<PathEnum> lastPath = sgRestart.add(new EnumSetting.Builder<PathEnum>()
        .name("last-path")
        .description("last-path")
        .visible(lastBegin::get)
        .defaultValue(PathEnum.NEXT_CIRCLE)
        .build()
    );

    // 上次开始的原点区块X
    private final Setting<Integer> lastOriginX = sgRestart.add(new IntSetting.Builder()
        .name("last-origin-x")
        .description("last-origin-x")
        .visible(lastBegin::get)
        .defaultValue(0)
        .min(Integer.MIN_VALUE)
        .max(Integer.MAX_VALUE)
        .sliderMin(Integer.MIN_VALUE)
        .sliderMax(Integer.MAX_VALUE)
        .build()
    );

    // 上次开始的原点区块Z
    private final Setting<Integer> lastOriginZ = sgRestart.add(new IntSetting.Builder()
        .name("last-origin-z")
        .description("last-origin-z")
        .visible(lastBegin::get)
        .defaultValue(0)
        .min(Integer.MIN_VALUE)
        .max(Integer.MAX_VALUE)
        .sliderMin(Integer.MIN_VALUE)
        .sliderMax(Integer.MAX_VALUE)
        .build()
    );

    public BaseFinderXin() {
        super(EasyAddon.CATEGORY, "base-finder-xin", "base-finder-xin");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        currentCircle = lastBegin.get() ? lastCircle.get() : 0;
        currentPath = lastBegin.get() ? lastPath.get() : PathEnum.NEXT_CIRCLE;
        originChunk = lastBegin.get() ? new ChunkPos(lastOriginX.get(), lastOriginZ.get()) : mc.player.getChunkPos();

        // 如果设置了从上次开始，设置目标区块 targetChunk
        isBack = !lastBegin.get();
        if (!isBack) {
            targetChunk = new ChunkPos(lastChunkX.get(), lastChunkZ.get());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // 如果没有到达上次的位置
        if (!isBack) {
            gotoChunk(targetChunk);

            // 如果回到了上次的位置
            if (mc.player.getChunkPos().equals(targetChunk)) {
                isBack = true;
            }
            return;
        }

        // 检查圈数是否到达限制
        if (currentCircle > circleLimit.get()) {
            info("搜索完成");
            toggle();
            return;
        }

        // 检查是否完成一圈
        if (currentPath == PathEnum.NEXT_CIRCLE) {
            updateNextPath();
            currentCircle++;
            info("前往第" + currentCircle + "圈...");
        }

        // 根据当前路径设置目标区块
        switch (currentPath) {
            case CENTER_TO_LEFT, DOWN_LEFT_TO_LEFT ->
                targetChunk = new ChunkPos(originChunk.x - chunkLoadRadius.get() * currentCircle * 2, originChunk.z);
            case CENTER_LEFT_TO_UP_LEFT ->
                targetChunk = new ChunkPos(originChunk.x - chunkLoadRadius.get() * currentCircle * 2, originChunk.z - chunkLoadRadius.get() * currentCircle * 2);
            case UP_LEFT_TO_UP_RIGHT ->
                targetChunk = new ChunkPos(originChunk.x + chunkLoadRadius.get() * currentCircle * 2, originChunk.z - chunkLoadRadius.get() * currentCircle * 2);
            case UP_RIGHT_TO_DOWN_RIGHT ->
                targetChunk = new ChunkPos(originChunk.x + chunkLoadRadius.get() * currentCircle * 2, originChunk.z + chunkLoadRadius.get() * currentCircle * 2);
            case DOWN_RIGHT_TO_DOWN_LEFT ->
                targetChunk = new ChunkPos(originChunk.x - chunkLoadRadius.get() * currentCircle * 2, originChunk.z + chunkLoadRadius.get() * currentCircle * 2);
        }

        // 如果到达目标区块
        if (mc.player.getChunkPos().equals(targetChunk)) {
            if (turnDelayTimer == 0){
                turnDelayTimer = turnDelay.get();
                mc.options.forwardKey.setPressed(false);  // 立即停止移动
                return;
            }

            // 延迟结束后更新路径
            if (turnDelayTimer == 1) {  // 最后一tick
                updateNextPath();
                info("已完成第" + currentCircle + "圈的 " + currentPath);
            }

            turnDelayTimer--;  // 递减计时器
            return;  // 延迟期间不执行gotoChunk
        }

        gotoChunk(targetChunk);
    }

    // 让玩家面向目标区块
    private void gotoChunk(ChunkPos targetChunk) {
        if (mc.player == null) return;

        // 检查是否满足转向延迟条件
        if (turnDelayTimer > 0) {
            mc.options.forwardKey.setPressed(false);
            turnDelayTimer --;
            return;
        }

        // 计算目标区块的中心点
        Vec3d playerPos = mc.player.getPos();
        double targetX = (targetChunk.getStartX() + targetChunk.getEndX()) / 2.0;
        double targetZ = (targetChunk.getStartZ() + targetChunk.getEndZ()) / 2.0;

        // 计算yaw
        double deltaX = targetX - playerPos.x;
        double deltaZ = targetZ - playerPos.z;
        float targetYaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;

        // 直接设置玩家yaw角度
        mc.player.setYaw(targetYaw);

        Direction playerDirection = Direction.fromRotation(targetYaw);
        // 区块没加载完停下
        if (waitChunkLoad.get() && !AdjacentChunksLoaded(playerDirection)) {
            mc.options.forwardKey.setPressed(false);
            mc.player.sendMessage(Text.literal("停"), false);
            return;
        }

        // 按下前进键
        mc.options.forwardKey.setPressed(true);
    }

    @Override
    public void onDeactivate() {
        // 停止前进键
        mc.options.forwardKey.setPressed(false);

        info("你可以保存此信息以便于下次重新开始: ");
        info("originX（起始区块X）: " + originChunk.x);
        info("originZ（起始区块Z）: " + originChunk.z);
        info("circle（圈数）: " + currentCircle);
        info("currentPath（圈进度）: " + currentPath);

        // 保存进度信息
        lastOriginX.set(originChunk.x);
        lastOriginZ.set(originChunk.z);
        lastChunkX.set(mc.player.getChunkPos().x);
        lastChunkZ.set(mc.player.getChunkPos().z);
        lastCircle.set(currentCircle);
        lastPath.set(currentPath);

        // 还原变量
        originChunk = null;
        targetChunk = null;
        currentPath = null;
    }

    // 检查当前区块左右两边区块是否已完全加载
    private boolean AdjacentChunksLoaded(Direction direction) {
        ChunkPos currentChunk = mc.player.getChunkPos();

        for (int i = -chunkLoadRadius.get(); i <= chunkLoadRadius.get(); i++) {
            ChunkPos adjacentChunk = null;
            switch (direction) {
                case NORTH, SOUTH ->
                    adjacentChunk = new ChunkPos(currentChunk.x + i, currentChunk.z);
                case EAST, WEST -> adjacentChunk = new ChunkPos(currentChunk.x, currentChunk.z + i);
            }
            if (!mc.world.getChunkManager().isChunkLoaded(adjacentChunk.x, adjacentChunk.z)) {
                return false;
            }
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        
    }

    // 获取下一个路径的方法
    private void updateNextPath() {
        switch (currentPath) {
            case CENTER_TO_LEFT -> currentPath = PathEnum.CENTER_LEFT_TO_UP_LEFT;
            case CENTER_LEFT_TO_UP_LEFT -> currentPath = PathEnum.UP_LEFT_TO_UP_RIGHT;
            case UP_LEFT_TO_UP_RIGHT -> currentPath = PathEnum.UP_RIGHT_TO_DOWN_RIGHT;
            case UP_RIGHT_TO_DOWN_RIGHT -> currentPath = PathEnum.DOWN_RIGHT_TO_DOWN_LEFT;
            case DOWN_RIGHT_TO_DOWN_LEFT -> currentPath = PathEnum.DOWN_LEFT_TO_LEFT;
            case DOWN_LEFT_TO_LEFT -> currentPath = PathEnum.NEXT_CIRCLE;
            case NEXT_CIRCLE -> currentPath = PathEnum.CENTER_TO_LEFT;
        }
    }

    private enum PathEnum {
        NEXT_CIRCLE,   //  完成
        CENTER_TO_LEFT,   // 从中心到正左
        CENTER_LEFT_TO_UP_LEFT,  // 正左到左上
        UP_LEFT_TO_UP_RIGHT, // 左上到右上
        UP_RIGHT_TO_DOWN_RIGHT, // 右上到右下
        DOWN_RIGHT_TO_DOWN_LEFT, // 右下到左下
        DOWN_LEFT_TO_LEFT // 左下到正左
    }
}
