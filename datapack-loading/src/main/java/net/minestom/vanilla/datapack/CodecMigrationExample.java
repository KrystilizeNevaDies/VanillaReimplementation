package net.minestom.vanilla.datapack;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.vanilla.datapack.DatapackCodecs;

import java.io.IOException;

/**
 * Example demonstration of how legacy JSON parsing can be converted to use Minecraft codecs.
 * This shows both the old approach and the new codec-based approach working side by side.
 */
public class CodecMigrationExample {

    /**
     * Example showing how to migrate from legacy Moshi parsing to codec-based parsing.
     */
    public void migrationExample() throws IOException {
        String jsonMaterial = "\"minecraft:diamond\"";
        String jsonKey = "\"minecraft:stone\"";

        // OLD WAY (legacy parsing - now replaced with codec system)
        // This demonstrates how the old system would have worked
        
        // NEW WAY (codec-based)
        // More efficient and consistent with Minecraft's internal systems
        
        var materialJsonElement = com.google.gson.JsonParser.parseString(jsonMaterial);
        var keyJsonElement = com.google.gson.JsonParser.parseString(jsonKey);
        
        Material codecMaterial = DatapackLoader.parseJsonElement(materialJsonElement, DatapackCodecs.MATERIAL_CODEC);
        Key codecKey = DatapackLoader.parseJsonElement(keyJsonElement, DatapackCodecs.KEY_CODEC);

        System.out.println("Codec material: " + codecMaterial);
        System.out.println("Codec key: " + codecKey);
    }

    /**
     * Example showing advanced codec features that weren't available in legacy parsing.
     */
    public void advancedCodecFeatures() throws IOException {
        // Test legacy material mapping - automatically handled by codec
        String legacyScute = "\"minecraft:scute\"";
        Material scute = DatapackLoader.parseJson(legacyScute, DatapackCodecs.MATERIAL_CODEC);
        
        // This automatically maps to the correct modern material
        assert scute == Material.TURTLE_SCUTE;
        
        // Tag reference parsing - handles # prefix automatically
        String tagReference = "\"#minecraft:logs\"";
        Key tagKey = DatapackLoader.parseJson(tagReference, DatapackCodecs.KEY_CODEC);
        
        // The # prefix is automatically stripped
        assert tagKey.equals(Key.key("minecraft:logs"));
        
        System.out.println("Legacy scute mapped to: " + scute);
        System.out.println("Tag reference parsed as: " + tagKey);
    }
}