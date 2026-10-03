package dev.phntm.hytweaks.inventory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.phntm.hytweaks.HyTweaks;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;
import dev.phntm.hytweaks.core.Players;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Map;

/** When the held stack is used up, pull the same item from storage, backpack or the rest of the hotbar into the slot. */
public final class Refill extends ActiveSlotSystem implements Feature {
    /** The hotbar slot each player last dropped from, until their next held-slot change; dropping the last item must not refill. */
    private final Map<Ref<EntityStore>, Short> drops = Players.map();

    @Nonnull
    @Override
    public String id() {
        return "refill";
    }

    @Override
    public void register(@Nonnull HyTweaks plugin, @Nonnull Config.Section config) {
        plugin.getEntityStoreRegistry().registerSystem(this);
        plugin.getEntityStoreRegistry().registerSystem(new DropWatcher());
    }

    @Override
    void onChange(
            @Nonnull Ref<EntityStore> player,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull ItemContainer hotbar,
            short slot,
            @Nullable ItemStack before,
            @Nullable ItemStack after
    ) {
        Short dropped = drops.remove(player);
        // Used up = the last single item vanished. Whole stacks vanishing are moves or drops.
        if (ItemStack.isEmpty(before) || before.getQuantity() != 1 || !ItemStack.isEmpty(after)
                || dropped != null && dropped == slot) {
            return;
        }
        String itemId = before.getItemId();
        commandBuffer.run(store -> {
            if (!ItemStack.isEmpty(hotbar.getItemStack(slot))) {
                return;
            }
            // Smallest stack first, so stray partial stacks get used up.
            Inv.Slot source = Inv.find(store, player, slot, s -> s.getItemId().equals(itemId),
                    Comparator.comparingInt(ItemStack::getQuantity).reversed());
            if (source != null) {
                source.container().moveItemStackFromSlotToSlot(
                        source.index(), source.stack().getQuantity(), hotbar, slot);
            }
        });
    }

    private final class DropWatcher extends EntityEventSystem<EntityStore, DropItemEvent.PlayerRequest> {
        DropWatcher() {
            super(DropItemEvent.PlayerRequest.class);
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
                @Nonnull DropItemEvent.PlayerRequest event
        ) {
            if (event.getInventorySectionId() == InventoryComponent.HOTBAR_SECTION_ID) {
                drops.put(chunk.getReferenceTo(index), event.getSlotId());
            }
        }
    }
}
