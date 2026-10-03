package dev.phntm.hytweaks.core;

import dev.phntm.hytweaks.HyTweaks;

import javax.annotation.Nonnull;

/** One tweak. Adding a feature = implement this and list it in {@link HyTweaks}. */
public interface Feature {
    /** Config section name. */
    @Nonnull
    String id();

    /** Called once at setup, only when the feature is enabled in config. */
    void register(@Nonnull HyTweaks plugin, @Nonnull Config.Section config);
}
