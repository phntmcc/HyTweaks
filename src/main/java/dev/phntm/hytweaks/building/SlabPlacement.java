package dev.phntm.hytweaks.building;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.BlockSoundEvent;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocksound.config.BlockSoundSet;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ActiveSlotInventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.phntm.hytweaks.HyTweaks;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;
import dev.phntm.hytweaks.core.Players;
import dev.phntm.hytweaks.map.MapRefresh;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Places slabs by where on the face you aim, and completes a slab when you aim into its empty half.
 *
 * <p>Merges follow what the player was aiming at just before the click. When the target cell
 * already holds a slab the client never asks to place a block, and for faces vanilla's own merge
 * recognises, vanilla has already completed the slab (silently) before any system sees the click.
 */
public final class SlabPlacement extends EntityEventSystem<EntityStore, PlaceBlockEvent> implements Feature {
    public SlabPlacement() {
        super(PlaceBlockEvent.class);
    }

    @Nonnull
    @Override
    public String id() {
        return "slabPlacement";
    }

    @Override
    public void register(@Nonnull HyTweaks plugin, @Nonnull Config.Section config) {
        SlabGhost ghost = config.bool("preview", true) ? new SlabGhost((float) config.number("previewOpacity", 0.15)) : null;
        plugin.getEntityStoreRegistry().registerSystem(this);
        plugin.getEntityStoreRegistry().registerSystem(new Aim(ghost));
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
            @Nonnull PlaceBlockEvent event
    ) {
        Ref<EntityStore> player = chunk.getReferenceTo(index);
        Slabs.Plan plan = Slabs.plan(store.getExternalData().getWorld(), player, commandBuffer, event.getItemInHand());
        if (plan == null) {
            return;
        }
        if (plan.merge()) {
            // The click completes a slab instead; see Clicks.
            event.setCancelled(true);
        } else if (new Vector3i(plan.x(), plan.y(), plan.z()).equals(event.getTargetBlock())) {
            // Trust the client's cell; only the orientation is ours.
            event.setRotation(RotationTuple.get(plan.rotation()));
        }
    }

    /**
     * Each tick, per player: what a held slab would do (shown by the ghost), and whether a new
     * right-click started, which then acts on the previous tick's plan.
     */
    private static final class Aim extends EntityTickingSystem<EntityStore> {
        private final ComponentType<EntityStore, InteractionManager> managers =
                InteractionModule.get().getInteractionManagerComponent();
        private final Set<Dependency<EntityStore>> beforeVanilla =
                Set.of(new SystemDependency<>(Order.BEFORE, InteractionSystems.TickInteractionManagerSystem.class));
        private final Map<Ref<EntityStore>, State> states = Players.map();
        @Nullable
        private final SlabGhost ghost;

        /** Interaction chain ids seen last tick, the plan then, and when the ghost was drawn. */
        private record State(Set<Integer> chains, @Nullable Slabs.Plan plan, long drawnAt) {
        }

        Aim(@Nullable SlabGhost ghost) {
            this.ghost = ghost;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(Player.getComponentType(), PlayerRef.getComponentType(), managers);
        }

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return beforeVanilla;
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            Ref<EntityStore> player = chunk.getReferenceTo(index);
            World world = store.getExternalData().getWorld();
            State last = states.get(player);
            Slabs.Plan plan = Slabs.plan(world, player, commandBuffer, InventoryComponent.getItemInHand(commandBuffer, player));
            var chains = chunk.getComponent(index, managers).getChains();
            if (last == null && plan == null && chains.isEmpty()) {
                return;
            }
            Set<Integer> ids = chains.isEmpty() ? Set.of() : new HashSet<>(chains.keySet());
            boolean click = false;
            for (var chain : chains.int2ObjectEntrySet()) {
                click |= chain.getValue().getType() == InteractionType.Secondary
                        && (last == null || !last.chains().contains(chain.getIntKey()));
            }
            Slabs.Plan previous = last == null ? null : last.plan();
            if (click && previous != null && previous.merge()) {
                world.execute(() -> merge(world, player, previous));
            }
            long drawnAt = ghost == null ? 0 : ghost.update(chunk.getComponent(index, PlayerRef.getComponentType()).getPacketHandler(),
                    previous, last == null ? 0 : last.drawnAt(), plan, System.nanoTime());
            if (plan == null && ids.isEmpty()) {
                states.remove(player);
            } else {
                states.put(player, new State(ids, plan, drawnAt));
            }
        }
    }

    /**
     * Completes the slab the click aimed at. Vanilla may already have done it for faces its own
     * merge recognises, silently, so the place sound plays either way.
     */
    private static void merge(@Nonnull World world, @Nonnull Ref<EntityStore> player, @Nonnull Slabs.Plan plan) {
        Vector3i at = new Vector3i(plan.x(), plan.y(), plan.z());
        BlockType current = world.getBlockType(at);
        if (!player.isValid() || current == null) {
            return;
        }
        Store<EntityStore> store = player.getStore();
        if (current != plan.block()) {
            if (current.getBlockForState(Slabs.FULL_STATE) != plan.block()) {
                return;
            }
            world.setBlockInteractionState(at, current, Slabs.FULL_STATE);
            MapRefresh.changed(world, at.x, at.z);
            Player state = store.getComponent(player, Player.getComponentType());
            if (state != null && state.getGameMode() != GameMode.Creative
                    && store.getComponent(player, InventoryComponent.Hotbar.getComponentType()) instanceof ActiveSlotInventoryComponent hotbar) {
                hotbar.getInventory().removeItemStackFromSlot(hotbar.getActiveSlot(), 1);
            }
        }
        BlockSoundSet sounds = BlockSoundSet.getAssetMap().getAsset(plan.block().getBlockSoundSetIndex());
        if (sounds != null && sounds.getSoundEventIndices().containsKey(BlockSoundEvent.Build)) {
            SoundUtil.playSoundEvent3d(sounds.getSoundEventIndices().getInt(BlockSoundEvent.Build),
                    SoundCategory.SFX, at.x + 0.5, at.y + 0.5, at.z + 0.5, store);
        }
    }
}
