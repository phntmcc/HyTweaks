package dev.phntm.hytweaks.inventory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Part of {@link Refill}: items added to the hotbar, storage or backpack (picked up or crafted) top up
 * matching stacks already in the utility slots, never empty ones. Vanilla fills partial stacks before
 * empty slots, but never looks at the utility slots. Hand moves arrive as move transactions, not adds,
 * so taking items out never bounces them back.
 */
final class UtilityPickup extends EntityEventSystem<EntityStore, InventoryChangeEvent> {
    UtilityPickup() {
        super(InventoryChangeEvent.class);
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
        var type = event.getComponentType();
        if (!(event.getTransaction() instanceof ItemStackTransaction add) || add.getAction() != ActionType.ADD || !add.succeeded()
                || type != InventoryComponent.Hotbar.getComponentType() && type != InventoryComponent.Storage.getComponentType()
                && type != InventoryComponent.Backpack.getComponentType()) {
            return;
        }
        Ref<EntityStore> player = chunk.getReferenceTo(index);
        ItemContainer from = event.getItemContainer();
        for (ItemStackSlotTransaction slot : add.getSlotTransactions()) {
            ItemStack before = slot.getSlotBefore();
            ItemStack after = slot.getSlotAfter();
            int added = ItemStack.isEmpty(after) ? 0 : after.getQuantity() - (ItemStack.isEmpty(before) ? 0 : before.getQuantity());
            if (slot.succeeded() && added > 0) {
                commandBuffer.run(s -> topUp(s, player, from, slot.getSlot(), added));
            }
        }
    }

    private static void topUp(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> player,
                              @Nonnull ItemContainer from, short slot, int added) {
        InventoryComponent utility = store.getComponent(player, InventoryComponent.Utility.getComponentType());
        ItemStack picked = from.getItemStack(slot);
        if (utility == null || ItemStack.isEmpty(picked)) {
            return;
        }
        ItemContainer to = utility.getInventory();
        int left = Math.min(added, picked.getQuantity());
        for (short i = 0; i < to.getCapacity() && left > 0; i++) {
            ItemStack stack = to.getItemStack(i);
            if (ItemStack.isEmpty(stack) || !stack.isStackableWith(picked)) {
                continue;
            }
            int moved = Math.min(left, stack.getItem().getMaxStack() - stack.getQuantity());
            if (moved > 0 && from.moveItemStackFromSlotToSlot(slot, moved, to, i).succeeded()) {
                left -= moved;
            }
        }
    }
}
