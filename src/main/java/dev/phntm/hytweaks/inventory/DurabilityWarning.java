package dev.phntm.hytweaks.inventory;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.phntm.hytweaks.HyTweaks;
import dev.phntm.hytweaks.core.Config;
import dev.phntm.hytweaks.core.Feature;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Warn once, with a sound, as the held tool's durability crosses the threshold. */
public final class DurabilityWarning extends ActiveSlotSystem implements Feature {
    private double threshold;
    private String sound;

    @Nonnull
    @Override
    public String id() {
        return "durabilityWarning";
    }

    @Override
    public void register(@Nonnull HyTweaks plugin, @Nonnull Config.Section config) {
        threshold = config.number("threshold", 0.10);
        sound = config.string("sound", "SFX_Item_Break");
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
        if (ItemStack.isEmpty(before) || ItemStack.isEmpty(after) || after.isBroken()
                || !before.getItemId().equals(after.getItemId()) || after.getMaxDurability() <= 0
                || before.getDurability() / before.getMaxDurability() <= threshold
                || after.getDurability() / after.getMaxDurability() > threshold) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(player, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        int percent = (int) Math.ceil(100 * after.getDurability() / after.getMaxDurability());
        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw("Low durability"),
                Message.raw(percent + "% left"),
                null,
                // A fresh copy as the icon: the real item's durability bar covers the text.
                new ItemStack(after.getItemId()).toPacket(),
                NotificationStyle.Warning
        );
        int soundIndex = SoundEvent.getAssetMap().getIndex(sound);
        if (soundIndex != SoundEvent.EMPTY_ID) {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, SoundCategory.UI, 0.6f, 1.3f);
        }
    }
}
