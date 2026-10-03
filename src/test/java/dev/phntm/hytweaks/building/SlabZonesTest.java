package dev.phntm.hytweaks.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlabZonesTest {
    private static final double[][] CUBE = {{0, 0, 0, 1, 1, 1}};
    private static final double[][] BOTTOM_SLAB = {{0, 0, 0, 1, 0.5, 1}};

    /** A single block at the origin; everything else is air. */
    private static SlabZones.Cells only(double[][] boxes) {
        return (x, y, z) -> x == 0 && y == 0 && z == 0 ? boxes : null;
    }

    /** Looks at the +X face of the origin block from x = 3, aiming at (y, z) on the face. */
    private static SlabZones.Hit wall(double[][] boxes, double y, double z) {
        return SlabZones.raycast(only(boxes), new double[]{3, y, z}, new double[]{-1, 0, 0}, 5);
    }

    @Test
    void hitsNearestFaceWithOutwardNormal() {
        SlabZones.Hit hit = wall(CUBE, 0.5, 0.5);
        assertNotNull(hit);
        assertArrayEquals(new int[]{0, 0, 0}, hit.cell());
        assertArrayEquals(new int[]{1, 0, 0}, hit.normal());
        assertArrayEquals(new int[]{1, 0, 0}, hit.target());
        assertEquals(1.0, hit.point()[0], 1e-9);
        assertFalse(hit.interior());
    }

    @Test
    void missesBeyondReachAndThroughGaps() {
        assertNull(SlabZones.raycast(only(CUBE), new double[]{9, 0.5, 0.5}, new double[]{-1, 0, 0}, 5));
        assertNull(wall(BOTTOM_SLAB, 0.75, 0.5));
    }

    @Test
    void wallCentreKeepsVanillaVerticalSlab() {
        assertArrayEquals(new int[]{-1, 0, 0}, SlabZones.half(wall(CUBE, 0.5, 0.5)));
        assertArrayEquals(new int[]{-1, 0, 0}, SlabZones.half(wall(CUBE, 0.7, 0.3)));
    }

    @Test
    void wallTopAndBottomEdgesPlaceFlatSlabs() {
        assertArrayEquals(new int[]{0, 1, 0}, SlabZones.half(wall(CUBE, 0.9, 0.5)));
        assertArrayEquals(new int[]{0, -1, 0}, SlabZones.half(wall(CUBE, 0.1, 0.5)));
    }

    @Test
    void wallSideEdgesPlacePerpendicularVerticalSlabs() {
        assertArrayEquals(new int[]{0, 0, 1}, SlabZones.half(wall(CUBE, 0.5, 0.9)));
        assertArrayEquals(new int[]{0, 0, -1}, SlabZones.half(wall(CUBE, 0.5, 0.1)));
    }

    @Test
    void cornersSplitAlongTheDiagonals() {
        assertArrayEquals(new int[]{0, 1, 0}, SlabZones.half(wall(CUBE, 0.95, 0.85)));
        assertArrayEquals(new int[]{0, 0, 1}, SlabZones.half(wall(CUBE, 0.85, 0.95)));
    }

    @Test
    void floorCentrePlacesBottomSlabAndEdgesPlaceVertical() {
        SlabZones.Hit centre = SlabZones.raycast(only(CUBE), new double[]{0.5, 3, 0.5}, new double[]{0, -1, 0}, 5);
        assertArrayEquals(new int[]{0, -1, 0}, SlabZones.half(centre));
        SlabZones.Hit edge = SlabZones.raycast(only(CUBE), new double[]{0.95, 3, 0.5}, new double[]{0, -1, 0}, 5);
        assertArrayEquals(new int[]{1, 0, 0}, SlabZones.half(edge));
    }

    @Test
    void ceilingCentrePlacesTopSlab() {
        SlabZones.Hit hit = SlabZones.raycast(only(CUBE), new double[]{0.5, -3, 0.5}, new double[]{0, 1, 0}, 5);
        assertArrayEquals(new int[]{0, 1, 0}, SlabZones.half(hit));
    }

    @Test
    void zonesScaleToTheVisibleHalfFace() {
        // Side of a bottom slab spans y 0..0.5, so y = 0.45 is its top edge band.
        assertArrayEquals(new int[]{0, 1, 0}, SlabZones.half(wall(BOTTOM_SLAB, 0.45, 0.5)));
        assertArrayEquals(new int[]{-1, 0, 0}, SlabZones.half(wall(BOTTOM_SLAB, 0.25, 0.5)));
    }

    @Test
    void slabOpenFaceIsInterior() {
        SlabZones.Hit hit = SlabZones.raycast(only(BOTTOM_SLAB), new double[]{0.5, 3, 0.5}, new double[]{0, -1, 0}, 5);
        assertNotNull(hit);
        assertEquals(0.5, hit.point()[1], 1e-9);
        assertTrue(hit.interior());
    }

    @Test
    void walksCellsAlongDiagonalRays() {
        double[] dir = {-1, -1, -1};
        double len = Math.sqrt(3);
        SlabZones.Hit hit = SlabZones.raycast(only(CUBE), new double[]{2.6, 2.5, 2.4},
                new double[]{dir[0] / len, dir[1] / len, dir[2] / len}, 5);
        assertNotNull(hit);
        assertArrayEquals(new int[]{0, 0, 0}, hit.cell());
    }

    @Test
    void halfBoxesFillTheRightHalf() {
        assertArrayEquals(new double[]{0, 0, 0, 1, 0.5, 1}, SlabZones.box(2));
        assertArrayEquals(new double[]{0, 0.5, 0, 1, 1, 1}, SlabZones.box(3));
        assertArrayEquals(new double[]{0.5, 0, 0, 1, 1, 1}, SlabZones.box(1));
        assertArrayEquals(new double[]{0, 0, 0, 1, 1, 1}, SlabZones.box(-1));
    }

    @Test
    void halfIndexCoversAllSixDirections() {
        assertEquals(0, SlabZones.index(new int[]{-1, 0, 0}));
        assertEquals(3, SlabZones.index(new int[]{0, 1, 0}));
        assertEquals(5, SlabZones.index(new int[]{0, 0, 1}));
    }
}
