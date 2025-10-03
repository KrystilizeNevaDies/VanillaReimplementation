package net.minestom.vanilla.datapack;

import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.item.Material;
import net.minestom.server.utils.Range;
import net.minestom.vanilla.datapack.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the new codec-based datapack parsing implementations.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DatapackCodecsTest {

    @Test
    public void testKeyCodec() {
        // Test regular key parsing
        Result<Key> result = DatapackCodecs.KEY_CODEC.decode(Transcoder.JSON, JsonParser.parseString("\"minecraft:stone\""));
        Key key = result.orElseThrow("Key parsing should succeed");
        assertEquals(Key.key("minecraft:stone"), key);

        // Test tag reference parsing (starts with #)
        result = DatapackCodecs.KEY_CODEC.decode(Transcoder.JSON, JsonParser.parseString("\"#minecraft:logs\""));
        key = result.orElseThrow("Tag key parsing should succeed");
        assertEquals(Key.key("minecraft:logs"), key);

        // Test encoding - let's see what we actually get
        var encoded = DatapackCodecs.KEY_CODEC.encode(Transcoder.JSON, Key.key("minecraft:diamond"));
        System.out.println("Encoded result: " + encoded);
        String expectedString = encoded.orElseThrow("Encoding should succeed").toString();
        assertEquals("\"minecraft:diamond\"", expectedString);
    }

    @Test
    public void testMaterialCodec() {
        // Test valid material
        Result<Material> result = DatapackCodecs.MATERIAL_CODEC.decode(Transcoder.JSON, JsonParser.parseString("\"minecraft:stone\""));
        Material material = result.orElseThrow("Material parsing should succeed");
        assertEquals(Material.STONE, material);

        // Test legacy material mapping
        result = DatapackCodecs.MATERIAL_CODEC.decode(Transcoder.JSON, JsonParser.parseString("\"minecraft:scute\""));
        material = result.orElseThrow("Legacy material parsing should succeed");
        assertEquals(Material.TURTLE_SCUTE, material);

        // Test invalid material - should fail gracefully
        assertThrows(Exception.class, () -> {
            Result<Material> invalidResult = DatapackCodecs.MATERIAL_CODEC.decode(Transcoder.JSON, JsonParser.parseString("\"minecraft:invalid_material\""));
            invalidResult.orElseThrow("Invalid material should throw");
        });
    }

    @Test
    public void testComponentCodec() {
        // Test simple text component
        String jsonText = "\"Hello World\"";
        Result<Component> result = DatapackCodecs.COMPONENT_CODEC.decode(Transcoder.JSON, JsonParser.parseString(jsonText));
        Component component = result.orElseThrow("Component parsing should succeed");
        assertEquals(Component.text("Hello World"), component);

        // Test complex component with formatting
        String complexJson = "\"{\\\"text\\\":\\\"Hello\\\",\\\"color\\\":\\\"red\\\",\\\"bold\\\":true}\"";
        result = DatapackCodecs.COMPONENT_CODEC.decode(Transcoder.JSON, JsonParser.parseString(complexJson));
        component = result.orElseThrow("Complex component parsing should succeed");
        Component expected = Component.text("Hello").color(NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        assertEquals(expected, component);
    }

    @Test
    public void testNbtCompoundCodec() {
        // Test simple NBT compound
        String nbtJson = "\"{\\\"test\\\":\\\"value\\\",\\\"number\\\":42}\"";
        Result<CompoundBinaryTag> result = DatapackCodecs.NBT_COMPOUND_CODEC.decode(Transcoder.JSON, JsonParser.parseString(nbtJson));
        CompoundBinaryTag nbt = result.orElseThrow("NBT compound parsing should succeed");
        
        assertEquals("value", nbt.getString("test"));
        assertEquals(42, nbt.getInt("number"));

        // Test empty NBT compound
        result = DatapackCodecs.NBT_COMPOUND_CODEC.decode(Transcoder.JSON, JsonParser.parseString("\"{}\""));
        nbt = result.orElseThrow("Empty NBT compound parsing should succeed");
        assertEquals(CompoundBinaryTag.empty(), nbt);
    }

    @Test
    public void testFloatRangeCodec() {
        // Test single float as range
        Result<Range.Float> result = DatapackCodecs.FLOAT_RANGE_CODEC.decode(Transcoder.JSON, JsonParser.parseString("5.5"));
        Range.Float range = result.orElseThrow("Single float range parsing should succeed");
        assertEquals(5.5f, range.min(), 0.001f);
        assertEquals(5.5f, range.max(), 0.001f);

        // Test float array as range
        result = DatapackCodecs.FLOAT_RANGE_CODEC.decode(Transcoder.JSON, JsonParser.parseString("[1.0, 10.0]"));
        range = result.orElseThrow("Float array range parsing should succeed");
        assertEquals(1.0f, range.min(), 0.001f);
        assertEquals(10.0f, range.max(), 0.001f);

        // Test invalid array size - should fail
        assertThrows(Exception.class, () -> {
            Result<Range.Float> invalidResult = DatapackCodecs.FLOAT_RANGE_CODEC.decode(Transcoder.JSON, JsonParser.parseString("[1.0, 2.0, 3.0]"));
            invalidResult.orElseThrow("Invalid array size should throw");
        });
    }

    @Test
    public void testDoubleListCodec() {
        // Test double list parsing
        Result<DoubleList> result = DatapackCodecs.DOUBLE_LIST_CODEC.decode(Transcoder.JSON, JsonParser.parseString("[1.0, 2.5, 3.14, 4.0]"));
        DoubleList list = result.orElseThrow("Double list parsing should succeed");
        
        assertEquals(4, list.size());
        assertEquals(1.0, list.getDouble(0), 0.001);
        assertEquals(2.5, list.getDouble(1), 0.001);
        assertEquals(3.14, list.getDouble(2), 0.001);
        assertEquals(4.0, list.getDouble(3), 0.001);

        // Test empty list
        result = DatapackCodecs.DOUBLE_LIST_CODEC.decode(Transcoder.JSON, JsonParser.parseString("[]"));
        list = result.orElseThrow("Empty double list parsing should succeed");
        assertEquals(0, list.size());
    }

    @Test
    public void testUuidCodec() {
        // UUID should currently throw an exception as it's not supported
        Result<java.util.UUID> result = DatapackCodecs.UUID_CODEC.decode(Transcoder.JSON, JsonParser.parseString("\"550e8400-e29b-41d4-a716-446655440000\""));
        assertThrows(UnsupportedOperationException.class, () -> {
            result.orElseThrow("UUID parsing should throw UnsupportedOperationException");
        });
    }

    @Test
    public void testCodecIntegration() {
        // Test that our codecs can be used with the existing DatapackLoader infrastructure
        
        // Test that we can parse a Key using our codec
        try {
            var keyFunction = DatapackLoader.codec(DatapackCodecs.KEY_CODEC);
            var jsonReader = JsonUtils.jsonReader("\"minecraft:stone\"");
            Key result = keyFunction.apply(jsonReader);
            assertEquals(Key.key("minecraft:stone"), result);
        } catch (Exception e) {
            fail("Key codec integration should work: " + e.getMessage());
        }
        
        // Test that we can parse a Material using our codec
        try {
            var materialFunction = DatapackLoader.codec(DatapackCodecs.MATERIAL_CODEC);
            var jsonReader = JsonUtils.jsonReader("\"minecraft:diamond\"");
            Material result = materialFunction.apply(jsonReader);
            assertEquals(Material.DIAMOND, result);
        } catch (Exception e) {
            fail("Material codec integration should work: " + e.getMessage());
        }
    }
}