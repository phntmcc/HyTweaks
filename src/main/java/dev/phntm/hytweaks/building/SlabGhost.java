package dev.phntm.hytweaks.building;

import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.io.PacketHandler;
import org.joml.Matrix4d;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/** A translucent box showing where the held slab will go, drawn with debug shapes sent to that player only. */
final class SlabGhost {
    private static final Vector3f COLOR = new Vector3f(1, 1, 1);
    /** Shapes outlive this, so redraw well before it runs out. */
    private static final float LIFETIME_S = 60;
    private static final long REDRAW_NANOS = 30_000_000_000L;

    private final float opacity;

    SlabGhost(float opacity) {
        this.opacity = opacity;
    }

    /**
     * Replaces the box for {@code shown} (drawn at {@code drawnAt}) with one for {@code plan}.
     *
     * @return when the box now showing was drawn
     */
    long update(@Nonnull PacketHandler client, @Nullable Slabs.Plan shown, long drawnAt, @Nullable Slabs.Plan plan, long now) {
        if (Objects.equals(shown, plan) && (plan == null || now - drawnAt < REDRAW_NANOS)) {
            return drawnAt;
        }
        if (shown != null) {
            // Debug shapes cannot be removed one by one; clear only when ours is showing.
            client.write(new ClearDebugShapes());
        }
        if (plan == null) {
            return 0;
        }
        double[] box = SlabZones.box(plan.half());
        Matrix4d transform = new Matrix4d()
                .translate(plan.x() + (box[0] + box[3]) / 2, plan.y() + (box[1] + box[4]) / 2, plan.z() + (box[2] + box[5]) / 2)
                .scale(box[3] - box[0], box[4] - box[1], box[5] - box[2]);
        client.write(new DisplayDebug(DebugShape.Cube, Matrix4dUtil.asFloatData(transform), COLOR,
                LIFETIME_S, (byte) 0, null, opacity));
        return now;
    }
}
