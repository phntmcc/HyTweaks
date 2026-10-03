package dev.phntm.hytweaks.inventory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.phntm.hytweaks.HyTweaks;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;
import dev.phntm.hytweaks.core.Players;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * When the held tool, weapon or shield breaks, swap in a working one from storage or backpack: the same item
 * first, otherwise the highest-level one of the same kind. The broken one goes back where the replacement
 * was, so it can be repaired later.
 */
public final class ToolReplace extends ActiveSlotSystem implements Feature {
    private final ComponentType<EntityStore, InteractionManager> managers =
            InteractionModule.get().getInteractionManagerComponent();
    private final Map<Ref<EntityStore>, Pending> pending = Players.map();

    /** Waits for the chains running at the break, which may still write the broken item back (a sickle, per crop). */
    private record Pending(ItemContainer container, short slot, List<InteractionChain> chains) {
    }

    @Nonnull
    @Override
    public String id() {
        return "toolReplace";
    }

    @Override
    public void register(@Nonnull HyTweaks plugin, @Nonnull Config.Section config) {
        plugin.getEntityStoreRegistry().registerSystem(this);
        plugin.getEntityStoreRegistry().registerSystem(new Swap());
    }

    @Override
    void onChange(
            @Nonnull Ref<EntityStore> player,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull ItemContainer container,
            short slot,
            @Nullable ItemStack before,
            @Nullable ItemStack after
    ) {
        if (ItemStack.isEmpty(before) || before.isBroken() || ItemStack.isEmpty(after) || !after.isBroken()) {
            return;
        }
        InteractionManager manager = commandBuffer.getComponent(player, managers);
        pending.put(player, new Pending(container, slot, manager == null ? List.of() : List.copyOf(manager.getChains().values())));
    }

    private final class Swap extends EntityTickingSystem<EntityStore> {
        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(Player.getComponentType(), managers);
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
            Pending swap = pending.get(player);
            if (swap == null) {
                return;
            }
            // A chain leaves the map only once its forks are done too.
            var chains = chunk.getComponent(index, managers).getChains();
            for (InteractionChain chain : swap.chains()) {
                if (chains.get(chain.getChainId()) == chain) {
                    return;
                }
            }
            pending.remove(player);
            commandBuffer.run(s -> replace(s, player, swap.container(), swap.slot()));
        }
    }

    private static void replace(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> player,
                                @Nonnull ItemContainer container, short slot) {
        ItemStack broken = container.getItemStack(slot);
        if (ItemStack.isEmpty(broken) || !broken.isBroken()) {
            return;
        }
        String id = broken.getItemId();
        String kind = kind(broken.getItem(), id);
        Inv.Slot source = Inv.find(store, player, container, slot,
                s -> !s.isBroken() && (s.getItemId().equals(id) || kind != null && kind.equals(kind(s.getItem(), s.getItemId()))),
                Comparator.comparing((ItemStack s) -> s.getItemId().equals(id))
                        .thenComparingInt(s -> s.getItem().getItemLevel())
                        .thenComparingDouble(ItemStack::getDurability));
        if (source != null) {
            container.setItemStackForSlot(slot, source.stack());
            source.container().setItemStackForSlot(source.index(), broken);
        }
    }

    /** "Tool_Pickaxe_Iron" -> "Tool_Pickaxe". Null for items that are neither tools nor weapons. */
    @Nullable
    static String kind(@Nonnull Item item, @Nonnull String itemId) {
        if (item.getTool() == null && item.getWeapon() == null) {
            return null;
        }
        int first = itemId.indexOf('_');
        int second = first < 0 ? -1 : itemId.indexOf('_', first + 1);
        return second < 0 ? itemId : itemId.substring(0, second);
    }
}
