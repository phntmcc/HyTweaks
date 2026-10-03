package dev.phntm.hytweaks.sleep;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSleep;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.systems.world.CanSleepInWorld;
import com.hypixel.hytale.builtin.beds.sleep.systems.world.StartSlumberSystem;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.gameplay.sleep.SleepConfig;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.phntm.hytweaks.HyTweaks;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Skips the night once enough players are in bed. Vanilla waits for everyone, so when everyone
 * is asleep this stays out of the way and lets the vanilla slumber play.
 */
public final class SleepPercentage extends DelayedSystem<EntityStore> implements Feature {
    private double required;

    public SleepPercentage() {
        super(0.5f);
    }

    @Nonnull
    @Override
    public String id() {
        return "sleepPercentage";
    }

    @Override
    public void register(@Nonnull HyTweaks plugin, @Nonnull Config.Section config) {
        required = Math.clamp(config.number("percent", 50) / 100.0, 0.0, 1.0);
        plugin.getEntityStoreRegistry().registerSystem(this);
    }

    static boolean enough(int sleeping, int online, double required) {
        return sleeping > 0 && sleeping < online && sleeping >= Math.ceil(online * required - 1e-9);
    }

    @Override
    public void delayedTick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        WorldSomnolence somnolence = store.getResource(WorldSomnolence.getResourceType());
        if (CanSleepInWorld.check(world).isNegative() || somnolence.getState() != WorldSleep.Awake.INSTANCE) {
            return;
        }
        int online = 0;
        int sleeping = 0;
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                online++;
                if (StartSlumberSystem.isReadyToSleep(store, ref)) {
                    sleeping++;
                }
            }
        }
        if (!enough(sleeping, online, required)) {
            return;
        }
        SleepConfig sleep = world.getGameplayConfig().getWorldConfig().getSleepConfig();
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        // Sleepers can still read as ready on the tick after a skip; never skip a second day.
        double hour = LocalDateTime.ofInstant(time.getGameTime(), ZoneOffset.UTC).toLocalTime().toSecondOfDay() / 3600.0;
        if (hour >= sleep.getWakeUpHour() && hour < 12) {
            return;
        }
        time.setGameTime(nextWakeUp(time.getGameTime(), sleep.getWakeUpHour()), world, store);
        wakeAll(store, sleep.getSounds().getSuccessIndex());
        HyTweaks.LOG.atInfo().log("Skipped night in %s (%d/%d asleep)", world.getName(), sleeping, online);
    }

    static Instant nextWakeUp(@Nonnull Instant now, float wakeUpHour) {
        LocalDateTime current = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        LocalDateTime wake = current.toLocalDate().atStartOfDay().plusMinutes(Math.round(wakeUpHour * 60));
        return (current.isBefore(wake) ? wake : wake.plusDays(1)).toInstant(ZoneOffset.UTC);
    }

    private static void wakeAll(@Nonnull Store<EntityStore> store, int sound) {
        store.forEachEntityParallel(PlayerSomnolence.getComponentType(), (index, chunk, commandBuffer) -> {
            PlayerSomnolence state = chunk.getComponent(index, PlayerSomnolence.getComponentType());
            if (state == null || state.getSleepState() instanceof PlayerSleep.FullyAwake) {
                return;
            }
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            commandBuffer.putComponent(ref, PlayerSomnolence.getComponentType(), PlayerSomnolence.AWAKE);
            MountedComponent mount = chunk.getComponent(index, MountedComponent.getComponentType());
            if (mount != null && mount.getControllerType() == MountController.BlockMount) {
                commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType());
            }
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null) {
                SoundUtil.playSoundEvent2dToPlayer(player, sound, SoundCategory.UI);
            }
        });
    }
}
