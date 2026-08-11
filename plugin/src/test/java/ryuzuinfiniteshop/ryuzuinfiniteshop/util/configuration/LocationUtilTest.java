package ryuzuinfiniteshop.ryuzuinfiniteshop.util.configuration;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

class LocationUtilTest {

    private static ServerMock server;
    private static WorldMock world;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    // ========== toStringFromLocation ==========

    @Test
    void toStringFromLocationReturnsCorrectFormat() {
        Location loc = new Location(world, 10.7, 20.2, 30.9);
        // Uses block coordinates
        assertEquals("test_world,10,20,30", LocationUtil.toStringFromLocation(loc));
    }

    @Test
    void toStringFromLocationWithNegative() {
        Location loc = new Location(world, -5.3, 0, 15.8);
        assertEquals("test_world,-6,0,15", LocationUtil.toStringFromLocation(loc));
    }

    @Test
    void toStringFromLocationThrowsWhenWorldNull() {
        Location loc = new Location(null, 1, 2, 3);
        assertThrows(RuntimeException.class, () -> LocationUtil.toStringFromLocation(loc));
    }

    // ========== toLocationFromString ==========

    @Test
    void toLocationFromStringParsesCorrectly() {
        Location loc = LocationUtil.toLocationFromString("test_world,10,20,30");
        assertNotNull(loc);
        assertEquals("test_world", loc.getWorld().getName());
        assertEquals(10.0, loc.getX());
        assertEquals(20.0, loc.getY());
        assertEquals(30.0, loc.getZ());
    }

    @Test
    void toLocationFromStringWithNegativeCoordinates() {
        Location loc = LocationUtil.toLocationFromString("test_world,-5,0,15");
        assertEquals(-5.0, loc.getX());
        assertEquals(0.0, loc.getY());
        assertEquals(15.0, loc.getZ());
    }

    // ========== isLocationString ==========

    @Test
    void isLocationStringValidFormat() {
        assertTrue(LocationUtil.isLocationString("world,1,2,3"));
    }

    @Test
    void isLocationStringWithNegativeCoordinates() {
        assertTrue(LocationUtil.isLocationString("world,-1,-2,-3"));
    }

    @Test
    void isLocationStringWithDecimals() {
        // isLocationString only cares that the last 3 are valid doubles
        assertTrue(LocationUtil.isLocationString("world,1.5,2.5,3.5"));
    }

    @Test
    void isLocationStringTooFewParts() {
        assertFalse(LocationUtil.isLocationString("world,1,2"));
    }

    @Test
    void isLocationStringTooManyParts() {
        // split gives more than 4 parts -> datas.length != 4
        assertFalse(LocationUtil.isLocationString("world,1,2,3,4"));
    }

    @Test
    void isLocationStringNonNumericCoordinate() {
        assertFalse(LocationUtil.isLocationString("world,abc,2,3"));
    }

    @Test
    void isLocationStringEmptyString() {
        // split(",") on "" gives [""], length = 1
        assertFalse(LocationUtil.isLocationString(""));
    }

    @Test
    void isLocationStringNull() {
        assertThrows(NullPointerException.class, () -> LocationUtil.isLocationString(null));
    }

    // ========== toBlockLocationFromLocation ==========

    @Test
    void toBlockLocationFromLocationCentersInBlock() {
        Location loc = new Location(world, 10.3, 20.7, 30.1);
        Location blockLoc = LocationUtil.toBlockLocationFromLocation(loc);
        assertEquals(10.5, blockLoc.getX());
        assertEquals(20.0, blockLoc.getY());
        assertEquals(30.5, blockLoc.getZ());
    }

    @Test
    void toBlockLocationFromLocationAlreadyOnBlock() {
        Location loc = new Location(world, 5, 10, 15);
        Location blockLoc = LocationUtil.toBlockLocationFromLocation(loc);
        assertEquals(5.5, blockLoc.getX());
        assertEquals(10.0, blockLoc.getY());
        assertEquals(15.5, blockLoc.getZ());
    }

    @Test
    void toBlockLocationFromLocationWithNegative() {
        Location loc = new Location(world, -1.3, 0, -2.7);
        Location blockLoc = LocationUtil.toBlockLocationFromLocation(loc);
        assertEquals(-1.5, blockLoc.getX());
        assertEquals(0.0, blockLoc.getY());
        assertEquals(-2.5, blockLoc.getZ());
    }

    // ========== getMiddleLocation ==========

    @Test
    void getMiddleLocationCentersXAndZ() {
        Location loc = new Location(world, 10.3, 20.7, 30.1);
        Location middle = LocationUtil.getMiddleLocation(loc);
        assertEquals(10.5, middle.getX());
        assertEquals(20.0, middle.getY());
        assertEquals(30.5, middle.getZ());
        // Unlike toBlockLocationFromLocation, getMiddleLocation returns a new Location
        // that preserves the original's world
        assertEquals(world, middle.getWorld());
    }

    @Test
    void getMiddleLocationAtOrigin() {
        Location loc = new Location(world, 0, 0, 0);
        Location middle = LocationUtil.getMiddleLocation(loc);
        assertEquals(0.5, middle.getX());
        assertEquals(0.0, middle.getY());
        assertEquals(0.5, middle.getZ());
    }

    @Test
    void getMiddleLocationOriginalIsNotModified() {
        Location loc = new Location(world, 10.3, 20.7, 30.1);
        LocationUtil.getMiddleLocation(loc);
        assertEquals(10.3, loc.getX(), "original X should not be modified");
        assertEquals(20.7, loc.getY(), "original Y should not be modified");
        assertEquals(30.1, loc.getZ(), "original Z should not be modified");
    }

    // ========== Round-trip consistency ==========

    @Test
    void toStringThenToLocationRoundTrip() {
        Location original = new Location(world, 15, 25, 35);
        String str = LocationUtil.toStringFromLocation(original);
        Location parsed = LocationUtil.toLocationFromString(str);
        assertEquals(original.getBlockX(), (int) parsed.getX());
        assertEquals(original.getBlockY(), (int) parsed.getY());
        assertEquals(original.getBlockZ(), (int) parsed.getZ());
        assertEquals(original.getWorld().getName(), parsed.getWorld().getName());
    }
}
