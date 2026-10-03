package dev.phntm.hytweaks.map;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.phntm.hytweaks.HyTweaks;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Keeps the world map current with player builds. Vanilla caches each chunk's map image until it
 * ages out, so edits only show after a rejoin. This collects the chunks players change and, every
 * few seconds, refreshes just those the way vanilla's builder tools do after an edit.
 */
public final class MapRefresh implements Feature {
    /** Chunks changed since the last refresh, per world; each set is only touched on its world's thread. */
    private static final Map<World, LongSet> DIRTY = new ConcurrentHashMap<>();
    private static volatile boolean enabled;

    @Nonnull
    @Override
    public String id() {
        return "mapRefresh";
    }

    @Override
    public void register(@Nonnull HyTweaks plugin, @Nonnull Config.Section config) {
        enabled = true;
        var registry = plugin.getEntityStoreRegistry();
        registry.registerSystem(new Flush((float) config.number("intervalSeconds", 2)));
        // Systems are registered by class, so each event gets its own (anonymous) subclass.
        registry.registerSystem(new OnBlock<>(PlaceBlockEvent.class, PlaceBlockEvent::getTargetBlock) {
        });
        registry.registerSystem(new OnBlock<>(BreakBlockEvent.class, BreakBlockEvent::getTargetBlock) {
        });
    }

    /** Marks the map at a block position for refresh; also used for changes made without an event. */
    public static void changed(@Nonnull World world, int x, int z) {
        if (enabled) {
            DIRTY.computeIfAbsent(world, w -> new LongOpenHashSet()).add(ChunkUtil.indexChunkFromBlock(x, z));
        }
    }

    /** Re-renders the batched chunks and re-sends them to players that have them loaded. */
    private static final class Flush extends DelayedSystem<EntityStore> {
        Flush(float intervalSeconds) {
            super(intervalSeconds);
        }

        @Override
        public void delayedTick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
            World world = store.getExternalData().getWorld();
            LongSet chunks = DIRTY.remove(world);
            if (chunks == null || !world.getWorldMapManager().isWorldMapEnabled()) {
                return;
            }
            world.getWorldMapManager().clearImagesInChunks(chunks);
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                Ref<EntityStore> ref = playerRef.getReference();
                Player player = ref == null || !ref.isValid() ? null : store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getWorldMapTracker().clearChunks(chunks);
                }
            }
        }
    }

    private static class OnBlock<E extends EcsEvent> extends EntityEventSystem<EntityStore, E> {
        private final Function<E, Vector3i> position;

        OnBlock(@Nonnull Class<E> event, @Nonnull Function<E, Vector3i> position) {
            super(event);
            this.position = position;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Player.getComponentType();
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull E event
        ) {
            Vector3i at = position.apply(event);
            if (at != null) {
                changed(store.getExternalData().getWorld(), at.x, at.z);
            }
        }
    }
}
