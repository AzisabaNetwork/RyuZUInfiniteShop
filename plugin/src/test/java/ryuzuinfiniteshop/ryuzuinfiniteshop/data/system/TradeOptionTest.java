package ryuzuinfiniteshop.ryuzuinfiniteshop.data.system;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradeOptionTest {

    // ========== Default constructor ==========

    @Test
    void defaultConstructorSetsDefaults() {
        TradeOption option = new TradeOption();
        assertFalse(option.isGive());
        assertEquals(0.0, option.getMoney());
        assertEquals(0, option.getLimit());
        assertFalse(option.isHide());
        assertEquals(100, option.getRate());
    }

    // ========== Parameterized constructor ==========

    @Test
    void parameterizedConstructorSetsValues() {
        TradeOption option = new TradeOption(true, 50.0, 5, true, 75);
        assertTrue(option.isGive());
        assertEquals(50.0, option.getMoney());
        assertEquals(5, option.getLimit());
        assertTrue(option.isHide());
        assertEquals(75, option.getRate());
    }

    // ========== isNoData ==========

    @Test
    void isNoDataForDefaults() {
        assertTrue(new TradeOption().isNoData());
    }

    @Test
    void isNoDataFalseWhenMoneySet() {
        assertFalse(new TradeOption(false, 10.0, 0, false, 100).isNoData());
    }

    @Test
    void isNoDataFalseWhenLimitSet() {
        assertFalse(new TradeOption(false, 0, 3, false, 100).isNoData());
    }

    @Test
    void isNoDataFalseWhenRateChanged() {
        assertFalse(new TradeOption(false, 0, 0, false, 50).isNoData());
    }

    @Test
    void isNoDataFalseWhenGiveSet() {
        // give with money=0 should still return true (give is only relevant with money)
        assertTrue(new TradeOption(true, 0, 0, false, 100).isNoData());
    }

    @Test
    void isNoDataFalseWhenHideSet() {
        // hide with rate=100 should still return true
        assertTrue(new TradeOption(false, 0, 0, true, 100).isNoData());
    }

    // ========== serialize ==========

    @Test
    void serializeDefaultProducesEmptyMap() {
        TradeOption option = new TradeOption();
        Map<String, Object> serialized = option.serialize();
        assertTrue(serialized.isEmpty());
    }

    @Test
    void serializeIncludesMoneyAndGive() {
        TradeOption option = new TradeOption(true, 100.0, 0, false, 100);
        Map<String, Object> serialized = option.serialize();
        assertEquals(2, serialized.size());
        assertEquals(true, serialized.get("give"));
        assertEquals(100.0, serialized.get("money"));
    }

    @Test
    void serializeIncludesLimit() {
        TradeOption option = new TradeOption(false, 0, 10, false, 100);
        Map<String, Object> serialized = option.serialize();
        assertEquals(1, serialized.size());
        assertEquals(10, serialized.get("limit"));
    }

    @Test
    void serializeIncludesRateAndHide() {
        TradeOption option = new TradeOption(false, 0, 0, true, 50);
        Map<String, Object> serialized = option.serialize();
        assertEquals(2, serialized.size());
        assertEquals(true, serialized.get("hide"));
        assertEquals(50, serialized.get("rate"));
    }

    @Test
    void serializeMultipleFields() {
        TradeOption option = new TradeOption(false, 25.5, 1, false, 75);
        Map<String, Object> serialized = option.serialize();
        // money != 0 => includes give + money (2)
        // limit != 0 => includes limit (1)
        // rate != 100 => includes hide + rate (2)
        assertEquals(5, serialized.size());
        assertEquals(25.5, serialized.get("money"));
        assertEquals(1, serialized.get("limit"));
        assertEquals(75, serialized.get("rate"));
    }

    // ========== deserialize ==========

    @Test
    void deserializeEmptyMap() {
        TradeOption option = TradeOption.deserialize(new LinkedHashMap<>());
        assertFalse(option.isGive());
        assertEquals(0.0, option.getMoney());
        assertEquals(0, option.getLimit());
        assertFalse(option.isHide());
        assertEquals(100, option.getRate());
    }

    @Test
    void deserializeAllFields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("give", true);
        map.put("money", 200.0);
        map.put("limit", 5);
        map.put("hide", true);
        map.put("rate", 25);

        TradeOption option = TradeOption.deserialize(map);
        assertTrue(option.isGive());
        assertEquals(200.0, option.getMoney());
        assertEquals(5, option.getLimit());
        assertTrue(option.isHide());
        assertEquals(25, option.getRate());
    }

    @Test
    void deserializePartialFields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("limit", 3);

        TradeOption option = TradeOption.deserialize(map);
        assertFalse(option.isGive());
        assertEquals(0.0, option.getMoney());
        assertEquals(3, option.getLimit());
        assertFalse(option.isHide());
        assertEquals(100, option.getRate());
    }

    // ========== Serialization round-trip ==========

    @Test
    void serializeDeserializeRoundTrip() {
        TradeOption original = new TradeOption(true, 99.99, 7, true, 30);
        Map<String, Object> serialized = original.serialize();
        TradeOption deserialized = TradeOption.deserialize(serialized);
        assertEquals(original, deserialized);
    }

    @Test
    void serializeDeserializeRoundTripDefault() {
        TradeOption original = new TradeOption();
        Map<String, Object> serialized = original.serialize();
        TradeOption deserialized = TradeOption.deserialize(serialized);
        assertEquals(original, deserialized);
    }

    @Test
    void serializeDeserializeRoundTripMoneyOnly() {
        TradeOption original = new TradeOption(false, 500.0, 0, false, 100);
        Map<String, Object> serialized = original.serialize();
        TradeOption deserialized = TradeOption.deserialize(serialized);
        assertEquals(original, deserialized);
    }

    // ========== equals and hashCode ==========

    @Test
    void equalsSameValues() {
        TradeOption a = new TradeOption(true, 10.0, 2, false, 80);
        TradeOption b = new TradeOption(true, 10.0, 2, false, 80);
        assertEquals(a, b);
    }

    @Test
    void equalsDifferentValues() {
        TradeOption a = new TradeOption(true, 10.0, 2, false, 80);
        TradeOption b = new TradeOption(false, 10.0, 2, false, 80);
        assertNotEquals(a, b);
    }

    @Test
    void hashCodeEqualsForEqualObjects() {
        TradeOption a = new TradeOption(false, 0, 5, false, 100);
        TradeOption b = new TradeOption(false, 0, 5, false, 100);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ========== chain setters ==========

    @Test
    void chainSetters() {
        TradeOption option = new TradeOption()
                .setGive(true)
                .setMoney(30.0)
                .setLimit(10)
                .setHide(true)
                .setRate(60);
        assertTrue(option.isGive());
        assertEquals(30.0, option.getMoney());
        assertEquals(10, option.getLimit());
        assertTrue(option.isHide());
        assertEquals(60, option.getRate());
    }
}
