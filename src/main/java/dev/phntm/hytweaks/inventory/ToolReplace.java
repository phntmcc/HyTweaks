package dev.phntm.hytweaks.inventory;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.phntm.hytweaks.HyTweaks;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * When the held tool or weapon breaks, swap in a working one from storage or backpack: the same item
 * first, otherwise the highest-level one of the same kind. The broken one goes back where the replacement
 * was, so it can be repaired later.
 */
public final class ToolReplace extends ActiveSlotSystem implements Feature {
    @Nonnull
    @Override
    public String id() {
        return "toolReplace";
    }

    @Override
    public void register(@Nonnull HyTweaks plugin, @Nonnull Config.Section config) {
        plugin.getEntityStoreRegistry().registerSystem(this);
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
        if (ItemStack.isEmpty(before) || before.isBroken() || ItemStack.isEmpty(after) || !after.isBroken()) {
            return;
        }
        String id = after.getItemId();
        String kind = kind(after.getItem(), id);
        Comparator<ItemStack> better = Comparator
                .comparing((ItemStack s) -> s.getItemId().equals(id))
                .thenComparingInt(s -> s.getItem().getItemLevel())
                .thenComparingDouble(ItemStack::getDurability);
        commandBuffer.run(store -> {
            ItemStack broken = hotbar.getItemStack(slot);
            if (ItemStack.isEmpty(broken) || !broken.isBroken()) {
                return;
            }
            Inv.Slot source = Inv.find(store, player, slot,
                    s -> !s.isBroken() && (s.getItemId().equals(id) || kind != null && kind.equals(kind(s.getItem(), s.getItemId()))),
                    better);
            if (source != null) {
                hotbar.setItemStackForSlot(slot, source.stack());
                source.container().setItemStackForSlot(source.index(), broken);
            }
        });
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
