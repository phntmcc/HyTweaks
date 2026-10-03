package dev.phntm.hytweaks.building;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pure geometry for slab placement, kept free of engine types so it is unit tested.
 *
 * <p>The face you aim at is split into a centre square and four edge trapezoids. The centre
 * places the slab flat against the face (vanilla); an edge places it against that edge of the
 * cell in front of the face.
 */
final class SlabZones {
    /** Width of each edge band as a fraction of the face. */
    static final double EDGE = 0.25;
    private static final double EPS = 1e-6;

    private SlabZones() {
    }

    /** Hitboxes of the block in a cell as {@code [minX, minY, minZ, maxX, maxY, maxZ]} relative to the cell, or null if empty. */
    @FunctionalInterface
    interface Cells {
        @Nullable
        double[][] boxes(int x, int y, int z);
    }

    /**
     * A ray hit on a hitbox face.
     *
     * @param cell   the block that was hit
     * @param normal unit vector out of the hit face, towards the cell the slab goes into
     * @param point  world-space hit point
     * @param min    world-space min corner of the box that was hit
     * @param max    world-space max corner of the box that was hit
     */
    record Hit(int[] cell, int[] normal, double[] point, double[] min, double[] max) {
        int[] target() {
            return new int[]{cell[0] + normal[0], cell[1] + normal[1], cell[2] + normal[2]};
        }

        /** True when the face lies inside its cell, i.e. the open face of a slab. */
        boolean interior() {
            int axis = axis(normal);
            double local = point[axis] - cell[axis];
            return local > EPS && local < 1 - EPS;
        }
    }

    /** Nearest hitbox face along the ray within {@code reach}, walking cells with a voxel DDA. */
    @Nullable
    static Hit raycast(@Nonnull Cells cells, @Nonnull double[] origin, @Nonnull double[] dir, double reach) {
        int[] cell = new int[3];
        int[] step = new int[3];
        double[] tMax = new double[3];
        double[] tDelta = new double[3];
        for (int i = 0; i < 3; i++) {
            cell[i] = (int) Math.floor(origin[i]);
            step[i] = dir[i] > 0 ? 1 : dir[i] < 0 ? -1 : 0;
            tDelta[i] = step[i] == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dir[i]);
            double boundary = step[i] > 0 ? cell[i] + 1 : cell[i];
            tMax[i] = step[i] == 0 ? Double.POSITIVE_INFINITY : (boundary - origin[i]) / dir[i];
        }
        double t = 0;
        while (t <= reach) {
            Hit hit = hitCell(cells, cell, origin, dir, reach);
            if (hit != null) {
                return hit;
            }
            int axis = tMax[0] < tMax[1] ? (tMax[0] < tMax[2] ? 0 : 2) : (tMax[1] < tMax[2] ? 1 : 2);
            t = tMax[axis];
            tMax[axis] += tDelta[axis];
            cell[axis] += step[axis];
        }
        return null;
    }

    @Nullable
    private static Hit hitCell(Cells cells, int[] cell, double[] origin, double[] dir, double reach) {
        double[][] boxes = cells.boxes(cell[0], cell[1], cell[2]);
        if (boxes == null) {
            return null;
        }
        Hit best = null;
        double bestT = reach;
        for (double[] box : boxes) {
            double[] min = {cell[0] + box[0], cell[1] + box[1], cell[2] + box[2]};
            double[] max = {cell[0] + box[3], cell[1] + box[4], cell[2] + box[5]};
            double near = Double.NEGATIVE_INFINITY;
            double far = Double.POSITIVE_INFINITY;
            int nearAxis = -1;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(dir[i]) < EPS) {
                    if (origin[i] < min[i] || origin[i] > max[i]) {
                        near = Double.POSITIVE_INFINITY;
                        break;
                    }
                    continue;
                }
                double t1 = (min[i] - origin[i]) / dir[i];
                double t2 = (max[i] - origin[i]) / dir[i];
                if (Math.min(t1, t2) > near) {
                    near = Math.min(t1, t2);
                    nearAxis = i;
                }
                far = Math.min(far, Math.max(t1, t2));
            }
            // near < 0 means the eye is inside the box: no face to aim at.
            if (nearAxis < 0 || near < 0 || near > far || near > bestT) {
                continue;
            }
            int[] normal = new int[3];
            normal[nearAxis] = dir[nearAxis] > 0 ? -1 : 1;
            double[] point = {origin[0] + dir[0] * near, origin[1] + dir[1] * near, origin[2] + dir[2] * near};
            best = new Hit(cell.clone(), normal, point, min, max);
            bestT = near;
        }
        return best;
    }

    /** The half of the target cell the slab fills, as a unit vector. */
    @Nonnull
    static int[] half(@Nonnull Hit hit) {
        int normalAxis = axis(hit.normal());
        int edgeAxis = -1;
        double edge = 0;
        for (int i = 0; i < 3; i++) {
            double size = hit.max()[i] - hit.min()[i];
            if (i == normalAxis || size < EPS) {
                continue;
            }
            double offset = (hit.point()[i] - hit.min()[i]) / size - 0.5;
            if (Math.abs(offset) > Math.abs(edge)) {
                edge = offset;
                edgeAxis = i;
            }
        }
        int[] half = new int[3];
        if (edgeAxis < 0 || Math.abs(edge) < 0.5 - EDGE) {
            half[normalAxis] = -hit.normal()[normalAxis];
        } else {
            half[edgeAxis] = edge > 0 ? 1 : -1;
        }
        return half;
    }

    /** Cell-relative {@code [min..., max...]} of a half (by {@link #index}), or the whole cell for -1. */
    @Nonnull
    static double[] box(int half) {
        double[] box = {0, 0, 0, 1, 1, 1};
        if (half >= 0) {
            box[half / 2 + (half % 2 == 0 ? 3 : 0)] = 0.5;
        }
        return box;
    }

    /** 0..5 for -X, +X, -Y, +Y, -Z, +Z. The opposite half is {@code index ^ 1}. */
    static int index(@Nonnull int[] unit) {
        int axis = axis(unit);
        return axis * 2 + (unit[axis] > 0 ? 1 : 0);
    }

    static int axis(@Nonnull int[] unit) {
        return unit[0] != 0 ? 0 : unit[1] != 0 ? 1 : 2;
    }
}
