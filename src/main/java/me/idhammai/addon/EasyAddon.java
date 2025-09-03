package me.idhammai.addon;

import me.idhammai.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

import org.slf4j.Logger;

public class EasyAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("EasyAddon");

    @Override
    public void onInitialize() {

        LOG.info("Initializing EasyAddon.");

        // Modules
        Modules.get().add(new ElytraFlyXin());
        Modules.get().add(new ElytraReplace());
        Modules.get().add(new SimpleElytraFlyPath());
        Modules.get().add(new ChickenNametags());
        Modules.get().add(new AutoLoginXin());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "me.idhammai.addon";
    }
}
