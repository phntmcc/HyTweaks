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
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.gameplay.sleep.SleepConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.phntm.hytweaks.HyTweaks;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;
import dev.phntm.hytweaks.core.Players;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Skips the night once enough players are in bed. Vanilla waits for everyone, so when everyone
 * is asleep this stays out of the way and lets the vanilla slumber play. AFK and Creative players
 * don't count towards the percentage unless they are in bed.
 */
public final class SleepPercentage extends DelayedSystem<EntityStore> implements Feature {
    private final Map<Ref<EntityStore>, Pose> poses = Players.map();
    private double required;
    private long afkNanos;

    /** Where a player stood and looked, and since when. */
    private record Pose(double x, double y, double z, float yaw, float pitch, long since) {
    }

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
        afkNanos = Math.round(Math.max(0, config.number("afkMinutes", 5)) * 60e9);
        plugin.getEntityStoreRegistry().registerSystem(this);
    }

    /** {@code counted} is the players the percentage applies to; {@code online} is everyone vanilla waits for. */
    static boolean enough(int sleeping, int counted, int online, double required) {
        return sleeping > 0 && sleeping < online && sleeping >= Math.ceil(counted * required - 1e-9);
    }

    @Override
    public void delayedTick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        WorldSomnolence somnolence = store.getResource(WorldSomnolence.getResourceType());
        if (CanSleepInWorld.check(world).isNegative() || somnolence.getState() != WorldSleep.Awake.INSTANCE) {
            return;
        }
        long now = System.nanoTime();
        int online = 0;
        int counted = 0;
        int sleeping = 0;
        for (PlayerRef player : world.getPlayerRefs()) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                online++;
                boolean active = active(store, ref, now);
                if (StartSlumberSystem.isReadyToSleep(store, ref)) {
                    sleeping++;
                    counted++;
                } else if (active) {
                    counted++;
                }
            }
        }
        if (!enough(sleeping, counted, online, required)) {
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
        HyTweaks.LOG.atInfo().log("Skipped night in %s (%d/%d asleep, %d online)", world.getName(), sleeping, counted, online);
    }

    /** False for Creative players and for players who haven't moved or looked around for the AFK time. */
    private boolean active(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, long now) {
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent body = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (player != null && player.getGameMode() == GameMode.Creative) {
            return false;
        }
        if (afkNanos == 0 || body == null || head == null) {
            return true;
        }
        Vector3d at = body.getPosition();
        Rotation3f look = head.getRotation();
        Pose last = poses.get(ref);
        if (last == null || last.x() != at.x || last.y() != at.y || last.z() != at.z
                || last.yaw() != look.yaw() || last.pitch() != look.pitch()) {
            poses.put(ref, new Pose(at.x, at.y, at.z, look.yaw(), look.pitch(), now));
            return true;
        }
        return now - last.since() < afkNanos;
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
