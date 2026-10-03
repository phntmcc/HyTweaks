package dev.phntm.hytweaks;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.phntm.hytweaks.building.SlabPlacement;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;
import dev.phntm.hytweaks.core.Players;
import dev.phntm.hytweaks.inventory.DurabilityWarning;
import dev.phntm.hytweaks.inventory.Refill;
import dev.phntm.hytweaks.inventory.ToolReplace;
import dev.phntm.hytweaks.map.MapRefresh;
import dev.phntm.hytweaks.sleep.SleepPercentage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class HyTweaks extends JavaPlugin {
    public static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    public HyTweaks(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        List<Feature> features = List.of(
                new Refill(),
                new ToolReplace(),
                new DurabilityWarning(),
                new SleepPercentage(),
                new SlabPlacement(),
                new MapRefresh()
        );
        getEntityStoreRegistry().registerSystem(new Players());
        Config config = Config.load(getDataDirectory().resolve("config.json"));
        List<String> enabled = new ArrayList<>();
        for (Feature feature : features) {
            Config.Section section = config.section(feature.id());
            if (section.bool("enabled", true)) {
                feature.register(this, section);
                enabled.add(feature.id());
            }
        }
        config.save();
        LOG.atInfo().log("HyTweaks enabled: %s", enabled);
    }
}
