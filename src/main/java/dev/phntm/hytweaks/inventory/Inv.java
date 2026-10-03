package dev.phntm.hytweaks.inventory;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Inventory lookups shared by the inventory tweaks. */
final class Inv {
    private Inv() {
    }

    record Slot(ItemContainer container, short index, ItemStack stack) {
    }

    /**
     * The best stack matching {@code match} in storage, backpack, then the rest of the hotbar
     * (never {@code hotbarSlot} itself); earlier wins ties.
     */
    @Nullable
    static Slot find(
            @Nonnull ComponentAccessor<EntityStore> store,
            @Nonnull Ref<EntityStore> player,
            short hotbarSlot,
            @Nonnull Predicate<ItemStack> match,
            @Nonnull Comparator<ItemStack> better
    ) {
        Slot best = null;
        for (ComponentType<EntityStore, ? extends InventoryComponent> type : List.of(
                InventoryComponent.Storage.getComponentType(), InventoryComponent.Backpack.getComponentType(),
                InventoryComponent.Hotbar.getComponentType())) {
            InventoryComponent component = store.getComponent(player, type);
            if (component == null) {
                continue;
            }
            boolean hotbar = type == InventoryComponent.Hotbar.getComponentType();
            ItemContainer container = component.getInventory();
            for (short i = 0; i < container.getCapacity(); i++) {
                if (hotbar && i == hotbarSlot) {
                    continue;
                }
                ItemStack stack = container.getItemStack(i);
                if (!ItemStack.isEmpty(stack) && match.test(stack)
                        && (best == null || better.compare(stack, best.stack()) > 0)) {
                    best = new Slot(container, i, stack);
                }
            }
        }
        return best;
    }
}
