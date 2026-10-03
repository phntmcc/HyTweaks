package dev.phntm.hytweaks.inventory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ActiveSlotInventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fires for in-place changes to a survival player's held hotbar and utility slots (and, if asked, worn armor):
 * using, breaking or wearing an item. Cursor moves arrive as move transactions, not slot
 * transactions, so they never reach {@link #onChange}.
 */
abstract class ActiveSlotSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
    private final boolean armor;

    ActiveSlotSystem() {
        this(false);
    }

    ActiveSlotSystem(boolean armor) {
        super(InventoryChangeEvent.class);
        this.armor = armor;
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
            @Nonnull InventoryChangeEvent event
    ) {
        if (!(event.getTransaction() instanceof SlotTransaction change) || !change.succeeded()) {
            return;
        }
        boolean held = (event.getComponentType() == InventoryComponent.Hotbar.getComponentType()
                || event.getComponentType() == InventoryComponent.Utility.getComponentType())
                && event.getInventory() instanceof ActiveSlotInventoryComponent active
                && change.getSlot() == active.getActiveSlot();
        if (!held && !(armor && event.getComponentType() == InventoryComponent.Armor.getComponentType())) {
            return;
        }
        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null || player.getGameMode() == GameMode.Creative) {
            return;
        }
        onChange(chunk.getReferenceTo(index), commandBuffer, event.getItemContainer(),
                change.getSlot(), change.getSlotBefore(), change.getSlotAfter());
    }

    abstract void onChange(
            @Nonnull Ref<EntityStore> player,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull ItemContainer container,
            short slot,
            @Nullable ItemStack before,
            @Nullable ItemStack after
    );
}
