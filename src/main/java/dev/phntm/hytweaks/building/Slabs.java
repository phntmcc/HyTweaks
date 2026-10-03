package dev.phntm.hytweaks.building;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Where and how a held slab would be placed right now. Shared by placement and the ghost preview. */
final class Slabs {
    static final String FULL_STATE = "Block";
    private static final double REACH = 5.0;
    private static final int[] NOT_A_SLAB = new int[0];
    private static final Map<String, int[]> ROTATIONS = new ConcurrentHashMap<>();

    private Slabs() {
    }

    /**
     * @param block    block to place (the full block when merging)
     * @param rotation rotation index
     * @param half     the half of the cell it fills ({@link SlabZones#index}), or -1 for the whole cell
     * @param merge    true when completing an existing slab at {@code x, y, z} into a full block
     */
    record Plan(int x, int y, int z, BlockType block, int rotation, int half, boolean merge) {
    }

    @Nullable
    static Plan plan(
            @Nonnull World world,
            @Nonnull Ref<EntityStore> player,
            @Nonnull ComponentAccessor<EntityStore> store,
            @Nullable ItemStack held
    ) {
        if (ItemStack.isEmpty(held) || !held.getItem().hasBlockType()) {
            return null;
        }
        BlockType slab = BlockType.getAssetMap().getAsset(held.getItem().getBlockId());
        int[] rotations = slab == null ? NOT_A_SLAB : rotations(slab);
        if (rotations == NOT_A_SLAB) {
            return null;
        }
        Transform look = TargetUtil.getLook(player, store);
        Vector3d eye = look.getPosition();
        Vector3d dir = look.getDirection();
        SlabZones.Hit hit = SlabZones.raycast((x, y, z) -> boxes(world, x, y, z),
                new double[]{eye.x, eye.y, eye.z}, new double[]{dir.x, dir.y, dir.z}, REACH);
        if (hit == null) {
            return null;
        }
        int[] cell = hit.cell();
        if (hit.interior() && isSlab(blockAt(world, cell[0], cell[1], cell[2]), slab)) {
            return merge(cell, slab);
        }
        int[] target = hit.target();
        int half = SlabZones.index(SlabZones.half(hit));
        Block occupant = blockAt(world, target[0], target[1], target[2]);
        if (occupant == null) {
            return null;
        }
        if (occupant.type().getMaterial() == BlockMaterial.Empty) {
            return new Plan(target[0], target[1], target[2], slab, rotations[half], half, false);
        }
        // Aiming into the empty half of a matching slab completes it.
        boolean oppositeHalf = isSlab(occupant, slab) && occupant.rotation() == rotations[half ^ 1];
        return oppositeHalf ? merge(target, slab) : null;
    }

    private static boolean isSlab(@Nullable Block block, @Nonnull BlockType slab) {
        return block != null && slab.getId().equals(block.type().getId());
    }

    @Nullable
    private static Plan merge(@Nonnull int[] cell, @Nonnull BlockType slab) {
        BlockType full = slab.getBlockForState(FULL_STATE);
        return full == null ? null : new Plan(cell[0], cell[1], cell[2], full, 0, -1, true);
    }

    /**
     * Rotation index for each half (indexed by {@link SlabZones#index}), found by rotating the block's
     * hitbox, so any half-block shaped block works, modded ones included.
     */
    @Nonnull
    private static int[] rotations(@Nonnull BlockType type) {
        return ROTATIONS.computeIfAbsent(type.getId(), id -> {
            BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
            if (hitbox == null || type.getVariantRotation() == null) {
                return NOT_A_SLAB;
            }
            int[] byHalf = {-1, -1, -1, -1, -1, -1};
            for (RotationTuple rotation : type.getVariantRotation().getRotations()) {
                int half = halfOf(hitbox.get(rotation.index()).getBoundingBox());
                if (half >= 0 && byHalf[half] < 0) {
                    byHalf[half] = rotation.index();
                }
            }
            int unrotated = halfOf(hitbox.get(RotationTuple.NONE_INDEX).getBoundingBox());
            if (unrotated >= 0) {
                byHalf[unrotated] = RotationTuple.NONE_INDEX;
            }
            for (int rotation : byHalf) {
                if (rotation < 0) {
                    return NOT_A_SLAB;
                }
            }
            return byHalf;
        });
    }

    /** Which half of the cell a box fills, or -1 if it is not exactly half a cell. */
    private static int halfOf(@Nonnull Box box) {
        double[] min = {box.min.x, box.min.y, box.min.z};
        double[] max = {box.max.x, box.max.y, box.max.z};
        int thin = -1;
        for (int i = 0; i < 3; i++) {
            double size = max[i] - min[i];
            if (Math.abs(size - 0.5) < 1e-3) {
                if (thin >= 0) {
                    return -1;
                }
                thin = i;
            } else if (Math.abs(min[i]) > 1e-3 || Math.abs(max[i] - 1) > 1e-3) {
                return -1;
            }
        }
        if (thin < 0) {
            return -1;
        }
        return thin * 2 + (Math.abs(min[thin]) < 1e-3 ? 0 : 1);
    }

    @Nullable
    private static double[][] boxes(@Nonnull World world, int x, int y, int z) {
        Block block = blockAt(world, x, y, z);
        if (block == null || block.type().getMaterial() == BlockMaterial.Empty) {
            return null;
        }
        BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(block.type().getHitboxTypeIndex());
        if (hitbox == null) {
            return null;
        }
        BlockBoundingBoxes.RotatedVariantBoxes rotated = hitbox.get(block.rotation());
        Box[] parts = rotated.hasDetailBoxes() ? rotated.getDetailBoxes() : new Box[]{rotated.getBoundingBox()};
        double[][] out = new double[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            Box b = parts[i];
            out[i] = new double[]{b.min.x, b.min.y, b.min.z, b.max.x, b.max.y, b.max.z};
        }
        return out;
    }

    private record Block(BlockType type, int rotation) {
    }

    /**
     * The block at a position, or null if its chunk is not loaded. Reads the chunk section directly:
     * {@code World.getBlockType} may load a chunk, which crashes the world when called from a system.
     */
    @Nullable
    private static Block blockAt(@Nonnull World world, int x, int y, int z) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        BlockSection section = ref == null || !ref.isValid() ? null
                : world.getChunkStore().getStore().getComponent(ref, BlockSection.getComponentType());
        BlockType type = section == null ? null : BlockType.getAssetMap().getAsset(section.get(x, y, z));
        return type == null ? null : new Block(type, section.getRotationIndex(x, y, z));
    }
}
