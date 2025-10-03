package net.minestom.vanilla.datapack;

import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import net.minestom.vanilla.datapack.json.JsonUtils;

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

        // OLD WAY (legacy Moshi-based)
        // This still works and is preserved for backward compatibility
        var legacyMaterialParser = DatapackLoader.moshi(Material.class);
        var legacyKeyParser = DatapackLoader.moshi(Key.class);
        
        var materialJsonReader = JsonUtils.jsonReader(jsonMaterial);
        var keyJsonReader = JsonUtils.jsonReader(jsonKey);
        
        Material legacyMaterial = legacyMaterialParser.apply(materialJsonReader);
        Key legacyKey = legacyKeyParser.apply(keyJsonReader);

        // NEW WAY (codec-based)
        // More efficient and consistent with Minecraft's internal systems
        var codecMaterialParser = DatapackLoader.codec(DatapackCodecs.MATERIAL_CODEC);
        var codecKeyParser = DatapackLoader.codec(DatapackCodecs.KEY_CODEC);
        
        var materialJsonReader2 = JsonUtils.jsonReader(jsonMaterial);
        var keyJsonReader2 = JsonUtils.jsonReader(jsonKey);
        
        Material codecMaterial = codecMaterialParser.apply(materialJsonReader2);
        Key codecKey = codecKeyParser.apply(keyJsonReader2);

        // Results are equivalent
        assert legacyMaterial.equals(codecMaterial);
        assert legacyKey.equals(codecKey);
        
        System.out.println("Legacy material: " + legacyMaterial);
        System.out.println("Codec material: " + codecMaterial);
        System.out.println("Results match: " + legacyMaterial.equals(codecMaterial));
    }

    /**
     * Example showing advanced codec features that weren't available in legacy parsing.
     */
    public void advancedCodecFeatures() throws IOException {
        // Legacy material mapping - automatically handled by codec
        String legacyScute = "\"minecraft:scute\"";
        var materialParser = DatapackLoader.codec(DatapackCodecs.MATERIAL_CODEC);
        Material scute = materialParser.apply(JsonUtils.jsonReader(legacyScute));
        
        // This automatically maps to the correct modern material
        assert scute == Material.TURTLE_SCUTE;
        
        // Tag reference parsing - handles # prefix automatically
        String tagReference = "\"#minecraft:logs\"";
        var keyParser = DatapackLoader.codec(DatapackCodecs.KEY_CODEC);
        Key tagKey = keyParser.apply(JsonUtils.jsonReader(tagReference));
        
        // The # prefix is automatically stripped
        assert tagKey.equals(Key.key("minecraft:logs"));
        
        System.out.println("Legacy scute mapped to: " + scute);
        System.out.println("Tag reference parsed as: " + tagKey);
    }
}