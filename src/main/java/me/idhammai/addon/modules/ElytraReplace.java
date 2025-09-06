package me.idhammai.addon.modules;

import me.idhammai.addon.EasyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ElytraReplace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /* 1.21.1 兼容：用 getDefaultStack().getMaxDamage() 代替已移除的 getMaxDamage() */
    private final int ELYTRA_MAX_DURABILITY = Items.ELYTRA.getDefaultStack().getMaxDamage();

    private final Setting<Integer> replaceDurability = sgGeneral.add(new IntSetting.Builder()
        .name("耐久度阈值")
        .description("鞘翅替换的耐久度阈值")
        .defaultValue(2)
        .range(1, ELYTRA_MAX_DURABILITY - 1)
        .sliderRange(1, ELYTRA_MAX_DURABILITY - 1)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("消息提醒")
        .description("鞘翅替换时发送消息提醒")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWhenFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("仅飞行时替换")
        .description("仅在鞘翅飞行时替换")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> temporaryDisableInventoryTweaks = sgGeneral.add(new BoolSetting.Builder()
        .name("InventoryTweaks兼容模式")
        .description("鞘翅替换时临时关闭InventoryTweaks模块")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> reEnableDelay = sgGeneral.add(new IntSetting.Builder()
        .name("兼容模式延迟")
        .description("鞘翅替换后重新启用InventoryTweaks的延迟（tick）")
        .defaultValue(10)
        .range(1, 60)
        .sliderMax(60)
        .visible(temporaryDisableInventoryTweaks::get)
        .build()
    );

    private boolean inventoryTweaksWasActive = false;
    private int reEnableCountdown = 0;

    public ElytraReplace() {
        super(EasyAddon.CATEGORY, "elytra-replace", "自动替换损坏的鞘翅");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (reEnableCountdown > 0) {
            reEnableCountdown--;
            if (reEnableCountdown == 0 && inventoryTweaksWasActive) {
                reEnableInventoryTweaks();
            }
        }

        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chestStack.getItem() == Items.ELYTRA) {
            int remainingDurability = chestStack.getMaxDamage() - chestStack.getDamage();
            checkAndReplaceElytra(chestStack, remainingDurability);
        }
    }

    private void checkAndReplaceElytra(ItemStack chestStack, int remainingDurability) {
        if (onlyWhenFlying.get() && !mc.player.isFallFlying()) return;
        if (remainingDurability > replaceDurability.get()) return;

        FindItemResult elytra = InvUtils.find(stack -> {
            if (stack.getItem() != Items.ELYTRA) return false;
            int stackDurability = stack.getMaxDamage() - stack.getDamage();
            return stackDurability > replaceDurability.get();
        });

        if (!elytra.found()) {
            if (chatFeedback.get()) {
                warning("未找到耐久度 > %d 的替换鞘翅", replaceDurability.get());
            }
            return;
        }

        if (temporaryDisableInventoryTweaks.get()) {
            temporaryDisableInventoryTweaks();
        }

        InvUtils.move().from(elytra.slot()).toArmor(2);

        if (chatFeedback.get()) {
            info("已替换鞘翅 (耐久度: %d -> %d)",
                remainingDurability,
                mc.player.getEquippedStack(EquipmentSlot.CHEST).getMaxDamage() -
                    mc.player.getEquippedStack(EquipmentSlot.CHEST).getDamage());
        }

        if (temporaryDisableInventoryTweaks.get() && inventoryTweaksWasActive) {
            reEnableCountdown = reEnableDelay.get();
        }
    }

    private void temporaryDisableInventoryTweaks() {
        InventoryTweaks inventoryTweaks = Modules.get().get(InventoryTweaks.class);
        if (inventoryTweaks != null && inventoryTweaks.isActive()) {
            inventoryTweaksWasActive = true;
            inventoryTweaks.toggle();
            if (chatFeedback.get()) {
                info("临时关闭InventoryTweaks模块");
            }
        } else {
            inventoryTweaksWasActive = false;
        }
    }

    private void reEnableInventoryTweaks() {
        InventoryTweaks inventoryTweaks = Modules.get().get(InventoryTweaks.class);
        if (inventoryTweaks != null && !inventoryTweaks.isActive()) {
            inventoryTweaks.toggle();
            if (chatFeedback.get()) {
                info("重新启用InventoryTweaks模块");
            }
        }
        inventoryTweaksWasActive = false;
    }

    @Override
    public void onDeactivate() {
        if (inventoryTweaksWasActive) {
            reEnableInventoryTweaks();
        }
        reEnableCountdown = 0;
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (chestStack.getItem() != Items.ELYTRA) return "无鞘翅";
        int remainingDurability = chestStack.getMaxDamage() - chestStack.getDamage();
        return String.valueOf(remainingDurability);
    }
}
