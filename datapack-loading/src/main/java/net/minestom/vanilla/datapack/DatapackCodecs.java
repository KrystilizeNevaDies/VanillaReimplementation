package net.minestom.vanilla.datapack;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.codec.Codec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.utils.Range;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Codec-based implementations for datapack parsing, replacing legacy JSON parsing methods.
 */
public class DatapackCodecs {

    /**
     * Codec for parsing Key objects from JSON strings.
     * Handles both regular keys and tag references (starting with #).
     */
    public static final @NotNull Codec<Key> KEY_CODEC = Codec.STRING.transform(
            str -> str.startsWith("#") ? Key.key(str.substring(1)) : Key.key(str),
            Key::asString
    );

    /**
     * Codec for parsing Material objects from JSON strings.
     * Includes legacy material mappings for backward compatibility.
     */
    public static final @NotNull Codec<Material> MATERIAL_CODEC = KEY_CODEC.transform(
            key -> {
                Material mat = Material.fromKey(key);
                
                // TODO: Remove these legacy updates
                Map<Key, Material> legacy = Map.of(
                        Key.key("scute"), Material.TURTLE_SCUTE
                );
                
                if (mat == null) {
                    if (legacy.containsKey(key)) {
                        return legacy.get(key);
                    }
                    throw new IllegalStateException("Material not found: " + key);
                }
                return mat;
            },
            Material::key
    );

    /**
     * Codec for parsing EntityType objects from JSON strings.
     */
    public static final @NotNull Codec<EntityType> ENTITY_TYPE_CODEC = KEY_CODEC.transform(
            EntityType::fromKey,
            EntityType::key
    );

    /**
     * Codec for parsing Enchantment objects from JSON strings.
     */
    public static final @NotNull Codec<Enchantment> ENCHANTMENT_CODEC = KEY_CODEC.transform(
            key -> MinecraftServer.getEnchantmentRegistry().get(key),
            enchantment -> {
                // Note: Enchantment doesn't have a key() method, so we need to find a different approach
                // For now, we'll throw an exception for encoding as it's not typically needed
                throw new UnsupportedOperationException("Enchantment encoding not supported");
            }
    );

    /**
     * Codec for parsing Component objects from JSON strings.
     */
    public static final @NotNull Codec<Component> COMPONENT_CODEC = Codec.STRING.transform(
            str -> GsonComponentSerializer.gson().deserialize(str),
            component -> GsonComponentSerializer.gson().serialize(component)
    );

    /**
     * Codec for parsing CompoundBinaryTag objects from JSON strings.
     */
    public static final @NotNull Codec<CompoundBinaryTag> NBT_COMPOUND_CODEC = Codec.STRING.transform(
            json -> {
                try {
                    return TagStringIO.get().asCompound(json);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse NBT compound: " + json, e);
                }
            },
            tag -> {
                try {
                    return TagStringIO.get().asString(tag);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize NBT compound", e);
                }
            }
    );

    /**
     * Codec for parsing Range.Float objects from JSON.
     * Can handle both single numbers (converted to range with same min/max) and arrays of two numbers.
     */
    public static final @NotNull Codec<Range.Float> FLOAT_RANGE_CODEC = 
            Codec.FLOAT.<Range.Float>transform(f -> new Range.Float(f), r -> r.min())
                    .orElse(Codec.FLOAT.list().transform(
                            list -> {
                                if (list.size() != 2) {
                                    throw new IllegalArgumentException("Float range array must have exactly 2 elements, got " + list.size());
                                }
                                return new Range.Float(list.get(0), list.get(1));
                            },
                            range -> List.of(range.min(), range.max())
                    ));

    /**
     * Codec for parsing DoubleList objects from JSON arrays.
     */
    public static final @NotNull Codec<DoubleList> DOUBLE_LIST_CODEC = Codec.DOUBLE.list().transform(
            list -> {
                DoubleList doubleList = new DoubleArrayList(list.size());
                for (Double d : list) {
                    doubleList.add(d.doubleValue());
                }
                return DoubleLists.unmodifiable(doubleList);
            },
            doubleList -> {
                List<Double> list = new java.util.ArrayList<>(doubleList.size());
                for (int i = 0; i < doubleList.size(); i++) {
                    list.add(doubleList.getDouble(i));
                }
                return list;
            }
    );

    /**
     * Codec for UUID objects (currently unsupported - throws exception).
     * TODO: Implement proper UUID support.
     */
    public static final @NotNull Codec<UUID> UUID_CODEC = Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("UUIDs are not supported yet");
            },
            UUID::toString
    );

    // Private constructor to prevent instantiation
    private DatapackCodecs() {}
}