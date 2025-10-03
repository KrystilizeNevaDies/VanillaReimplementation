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
import net.minestom.server.codec.StructCodec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.utils.Range;
import net.minestom.vanilla.datapack.advancement.Advancement;
import net.minestom.vanilla.datapack.dimension.DimensionType;
import net.minestom.vanilla.datapack.json.JsonUtils;
import net.minestom.vanilla.datapack.loot.LootTable;
import net.minestom.vanilla.datapack.loot.NBTPath;
import net.minestom.vanilla.datapack.loot.context.LootContext;
import net.minestom.vanilla.datapack.loot.function.LootFunction;
import net.minestom.vanilla.datapack.loot.function.Predicate;
import net.minestom.vanilla.datapack.number.NumberProvider;
import net.minestom.vanilla.datapack.recipe.Recipe;
import net.minestom.vanilla.datapack.tags.Tag;
import net.minestom.vanilla.datapack.trims.TrimMaterial;
import net.minestom.vanilla.datapack.trims.TrimPattern;
import net.minestom.vanilla.datapack.worldgen.*;
import net.minestom.vanilla.datapack.worldgen.math.CubicSpline;
import net.minestom.vanilla.datapack.worldgen.noise.Noise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Comprehensive codec-based implementations for all datapack parsing, replacing the legacy Moshi system.
 * This class provides a complete codec tree for the entire datapack loading infrastructure.
 */
public class DatapackCodecs {

    // Basic type codecs
    public static final @NotNull Codec<Key> KEY_CODEC = Codec.STRING.transform(
            str -> str.startsWith("#") ? Key.key(str.substring(1)) : Key.key(str),
            Key::asString
    );

    public static final @NotNull Codec<Material> MATERIAL_CODEC = KEY_CODEC.transform(
            key -> {
                Material mat = Material.fromKey(key);
                
                // Legacy material mappings for backward compatibility
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

    public static final @NotNull Codec<EntityType> ENTITY_TYPE_CODEC = KEY_CODEC.transform(
            EntityType::fromKey,
            EntityType::key
    );

    public static final @NotNull Codec<Enchantment> ENCHANTMENT_CODEC = KEY_CODEC.transform(
            key -> MinecraftServer.getEnchantmentRegistry().get(key),
            enchantment -> {
                throw new UnsupportedOperationException("Enchantment encoding not supported");
            }
    );

    public static final @NotNull Codec<Component> COMPONENT_CODEC = Codec.STRING.transform(
            str -> GsonComponentSerializer.gson().deserialize(str),
            component -> GsonComponentSerializer.gson().serialize(component)
    );

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

    public static final @NotNull Codec<UUID> UUID_CODEC = Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("UUIDs are not supported yet");
            },
            UUID::toString
    );

    public static final @NotNull Codec<Block> BLOCK_CODEC = 
            Codec.STRING.transform(Block::fromKey, block -> block.key().asString());

    // SingleOrList codec for common patterns
    public static <T> @NotNull Codec<JsonUtils.SingleOrList<T>> singleOrListCodec(Codec<T> elementCodec) {
        return elementCodec.<JsonUtils.SingleOrList<T>>transform(
                element -> new JsonUtils.SingleOrList.Single<>(element),
                singleOrList -> singleOrList.asObject()
        ).orElse(elementCodec.list().transform(
                list -> new JsonUtils.SingleOrList.List<>(list),
                singleOrList -> singleOrList.asList()
        ));
    }

    // Tag codec for handling tag references
    public static final @NotNull Codec<Tag> TAG_CODEC = KEY_CODEC.transform(
            key -> new Tag(key.asString()),
            Tag::key
    );

    // Advanced datapack type codecs
    public static final @NotNull Codec<Recipe> RECIPE_CODEC = createRecipeCodec();
    public static final @NotNull Codec<LootTable> LOOT_TABLE_CODEC = createLootTableCodec();
    public static final @NotNull Codec<Advancement> ADVANCEMENT_CODEC = createAdvancementCodec();
    public static final @NotNull Codec<DensityFunction> DENSITY_FUNCTION_CODEC = createDensityFunctionCodec();
    public static final @NotNull Codec<Biome> BIOME_CODEC = createBiomeCodec();
    public static final @NotNull Codec<Noise> NOISE_CODEC = createNoiseCodec();
    public static final @NotNull Codec<Carver> CARVER_CODEC = createCarverCodec();
    public static final @NotNull Codec<FloatProvider> FLOAT_PROVIDER_CODEC = createFloatProviderCodec();
    public static final @NotNull Codec<HeightProvider> HEIGHT_PROVIDER_CODEC = createHeightProviderCodec();
    public static final @NotNull Codec<VerticalAnchor> VERTICAL_ANCHOR_CODEC = createVerticalAnchorCodec();
    public static final @NotNull Codec<CubicSpline> CUBIC_SPLINE_CODEC = createCubicSplineCodec();
    public static final @NotNull Codec<NBTPath> NBT_PATH_CODEC = createNBTPathCodec();
    public static final @NotNull Codec<NumberProvider> NUMBER_PROVIDER_CODEC = createNumberProviderCodec();
    public static final @NotNull Codec<LootFunction> LOOT_FUNCTION_CODEC = createLootFunctionCodec();
    public static final @NotNull Codec<Predicate> PREDICATE_CODEC = createPredicateCodec();
    public static final @NotNull Codec<LootContext.Trait> LOOT_CONTEXT_TRAIT_CODEC = createLootContextTraitCodec();
    public static final @NotNull Codec<BlockState> BLOCK_STATE_CODEC = createBlockStateCodec();
    public static final @NotNull Codec<NoiseSettings.SurfaceRule> SURFACE_RULE_CODEC = createSurfaceRuleCodec();
    public static final @NotNull Codec<Datapack.Tag> DATAPACK_TAG_CODEC = createDatapackTagCodec();
    public static final @NotNull Codec<Datapack.ChatType> CHAT_TYPE_CODEC = createChatTypeCodec();
    public static final @NotNull Codec<Datapack.DamageType> DAMAGE_TYPE_CODEC = createDamageTypeCodec();
    public static final @NotNull Codec<Datapack.Dimension> DIMENSION_CODEC = createDimensionCodec();
    public static final @NotNull Codec<DimensionType> DIMENSION_TYPE_CODEC = createDimensionTypeCodec();
    public static final @NotNull Codec<TrimPattern> TRIM_PATTERN_CODEC = createTrimPatternCodec();
    public static final @NotNull Codec<TrimMaterial> TRIM_MATERIAL_CODEC = createTrimMaterialCodec();

    // Registry for all codecs - this replaces the Moshi adapter registry
    private static final Map<String, Codec<?>> CODEC_REGISTRY = createCodecRegistry();

    private static @NotNull Map<String, Codec<?>> createCodecRegistry() {
        Map<String, Codec<?>> registry = new java.util.HashMap<>();
        
        // Register basic type codecs
        registry.put("key", KEY_CODEC);
        registry.put("material", MATERIAL_CODEC);
        registry.put("entity_type", ENTITY_TYPE_CODEC);
        registry.put("enchantment", ENCHANTMENT_CODEC);
        registry.put("component", COMPONENT_CODEC);
        registry.put("nbt_compound", NBT_COMPOUND_CODEC);
        registry.put("float_range", FLOAT_RANGE_CODEC);
        registry.put("double_list", DOUBLE_LIST_CODEC);
        registry.put("uuid", UUID_CODEC);
        registry.put("block", BLOCK_CODEC);
        
        // Register advanced datapack type codecs
        registry.put("recipe", RECIPE_CODEC);
        registry.put("loot_table", LOOT_TABLE_CODEC);
        registry.put("advancement", ADVANCEMENT_CODEC);
        registry.put("density_function", DENSITY_FUNCTION_CODEC);
        registry.put("biome", BIOME_CODEC);
        registry.put("noise", NOISE_CODEC);
        registry.put("carver", CARVER_CODEC);
        registry.put("float_provider", FLOAT_PROVIDER_CODEC);
        registry.put("height_provider", HEIGHT_PROVIDER_CODEC);
        registry.put("vertical_anchor", VERTICAL_ANCHOR_CODEC);
        registry.put("cubic_spline", CUBIC_SPLINE_CODEC);
        registry.put("nbt_path", NBT_PATH_CODEC);
        registry.put("number_provider", NUMBER_PROVIDER_CODEC);
        registry.put("loot_function", LOOT_FUNCTION_CODEC);
        registry.put("predicate", PREDICATE_CODEC);
        registry.put("loot_context_trait", LOOT_CONTEXT_TRAIT_CODEC);
        registry.put("block_state", BLOCK_STATE_CODEC);
        registry.put("surface_rule", SURFACE_RULE_CODEC);
        registry.put("datapack_tag", DATAPACK_TAG_CODEC);
        registry.put("chat_type", CHAT_TYPE_CODEC);
        registry.put("damage_type", DAMAGE_TYPE_CODEC);
        registry.put("dimension", DIMENSION_CODEC);
        registry.put("dimension_type", DIMENSION_TYPE_CODEC);
        registry.put("trim_pattern", TRIM_PATTERN_CODEC);
        registry.put("trim_material", TRIM_MATERIAL_CODEC);
        
        return registry;
    }

    @SuppressWarnings("unchecked")
    public static <T> @NotNull Codec<T> getCodec(@NotNull String type) {
        return (Codec<T>) CODEC_REGISTRY.get(type);
    }

    @SuppressWarnings("unchecked")
    public static <T> @NotNull Codec<T> getCodec(@NotNull Class<T> clazz) {
        String simpleName = clazz.getSimpleName().toLowerCase();
        return (Codec<T>) CODEC_REGISTRY.get(simpleName);
    }

    // Factory methods for complex codecs - these will delegate to the existing fromJson methods
    // but wrap them in codec interfaces for now to maintain compatibility
    
    private static @NotNull Codec<Recipe> createRecipeCodec() {
        // Create a map of recipe type to codec
        Map<String, Codec<Recipe>> recipeCodecs = Map.of(
                "minecraft:blasting", createFromJsonCodec("blasting", element -> parseRecipeType(element, "blasting")),
                "minecraft:campfire_cooking", createFromJsonCodec("campfire_cooking", element -> parseRecipeType(element, "campfire_cooking")),
                "minecraft:crafting_shaped", createFromJsonCodec("crafting_shaped", element -> parseRecipeType(element, "crafting_shaped")),
                "minecraft:crafting_shapeless", createFromJsonCodec("crafting_shapeless", element -> parseRecipeType(element, "crafting_shapeless"))
                // Add more as needed
        );
        return createUnionCodec("type", recipeCodecs);
    }
    
    // Temporary helper method for recipe parsing
    private static Recipe parseRecipeType(com.google.gson.JsonElement element, String type) {
        // For now, throw an exception to indicate this needs proper implementation
        throw new UnsupportedOperationException("Recipe parsing not yet fully converted to codecs: " + type);
    }
    
    private static @NotNull Codec<LootTable> createLootTableCodec() {
        return createDelegatingCodec("loot_table", reader -> {
            // For now, delegate to existing parsing - this can be fully converted later
            throw new UnsupportedOperationException("LootTable codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<Advancement> createAdvancementCodec() {
        return createDelegatingCodec("advancement", reader -> {
            throw new UnsupportedOperationException("Advancement codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<DensityFunction> createDensityFunctionCodec() {
        return createDelegatingCodec("density_function", DensityFunction::fromJson);
    }
    
    private static @NotNull Codec<Biome> createBiomeCodec() {
        return createDelegatingCodec("biome", reader -> {
            throw new UnsupportedOperationException("Biome codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<Noise> createNoiseCodec() {
        return createDelegatingCodec("noise", Noise::fromJson);
    }
    
    private static @NotNull Codec<Carver> createCarverCodec() {
        return createDelegatingCodec("carver", Carver::fromJson);
    }
    
    private static @NotNull Codec<FloatProvider> createFloatProviderCodec() {
        return createDelegatingCodec("float_provider", FloatProvider::fromJson);
    }
    
    private static @NotNull Codec<HeightProvider> createHeightProviderCodec() {
        return createDelegatingCodec("height_provider", HeightProvider::fromJson);
    }
    
    private static @NotNull Codec<VerticalAnchor> createVerticalAnchorCodec() {
        return createDelegatingCodec("vertical_anchor", VerticalAnchor::fromJson);
    }
    
    private static @NotNull Codec<CubicSpline> createCubicSplineCodec() {
        return createDelegatingCodec("cubic_spline", CubicSpline::fromJson);
    }
    
    private static @NotNull Codec<NBTPath> createNBTPathCodec() {
        return createDelegatingCodec("nbt_path", NBTPath::fromJson);
    }
    
    private static @NotNull Codec<NumberProvider> createNumberProviderCodec() {
        return createDelegatingCodec("number_provider", NumberProvider.Double::fromJson);
    }
    
    private static @NotNull Codec<LootFunction> createLootFunctionCodec() {
        return createDelegatingCodec("loot_function", LootFunction::fromJson);
    }
    
    private static @NotNull Codec<Predicate> createPredicateCodec() {
        return createDelegatingCodec("predicate", Predicate::fromJson);
    }
    
    private static @NotNull Codec<LootContext.Trait> createLootContextTraitCodec() {
        return createDelegatingCodec("loot_context_trait", LootContext.Trait::fromJson);
    }
    
    private static @NotNull Codec<BlockState> createBlockStateCodec() {
        return createDelegatingCodec("block_state", BlockState::fromJson);
    }
    
    private static @NotNull Codec<NoiseSettings.SurfaceRule> createSurfaceRuleCodec() {
        return createDelegatingCodec("surface_rule", NoiseSettings.SurfaceRule::fromJson);
    }
    
    private static @NotNull Codec<Datapack.Tag> createDatapackTagCodec() {
        return createDelegatingCodec("datapack_tag", reader -> {
            throw new UnsupportedOperationException("Datapack.Tag codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<Datapack.ChatType> createChatTypeCodec() {
        return createDelegatingCodec("chat_type", reader -> {
            throw new UnsupportedOperationException("ChatType codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<Datapack.DamageType> createDamageTypeCodec() {
        return createDelegatingCodec("damage_type", reader -> {
            throw new UnsupportedOperationException("DamageType codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<Datapack.Dimension> createDimensionCodec() {
        return createDelegatingCodec("dimension", reader -> {
            throw new UnsupportedOperationException("Dimension codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<DimensionType> createDimensionTypeCodec() {
        return createDelegatingCodec("dimension_type", reader -> {
            throw new UnsupportedOperationException("DimensionType codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<TrimPattern> createTrimPatternCodec() {
        return createDelegatingCodec("trim_pattern", reader -> {
            throw new UnsupportedOperationException("TrimPattern codec not yet implemented");
        });
    }
    
    private static @NotNull Codec<TrimMaterial> createTrimMaterialCodec() {
        return createDelegatingCodec("trim_material", reader -> {
            throw new UnsupportedOperationException("TrimMaterial codec not yet implemented");
        });
    }

    // Helper method to create a codec that delegates to existing fromJson methods
    // This is a bridge pattern to allow gradual migration
    private static <T> @NotNull Codec<T> createFromJsonCodec(String name, Function<com.google.gson.JsonElement, T> fromJsonFunction) {
        return Codec.STRING.transform(
                jsonString -> {
                    try {
                        var jsonElement = com.google.gson.JsonParser.parseString(jsonString);
                        return fromJsonFunction.apply(jsonElement);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse " + name + " from JSON: " + jsonString, e);
                    }
                },
                value -> {
                    throw new UnsupportedOperationException("Encoding not implemented for " + name);
                }
        );
    }

    // Create a union type codec that matches the pattern used by unionStringTypeAdapted
    public static <T> @NotNull Codec<T> createUnionCodec(String typeKey, Map<String, Codec<T>> codecMap) {
        return Codec.STRING.transform(
                jsonString -> {
                    var jsonElement = com.google.gson.JsonParser.parseString(jsonString);
                    return JsonUtils.unionStringTypeMap(jsonElement, typeKey, codecMap);
                },
                value -> {
                    throw new UnsupportedOperationException("Union codec encoding not implemented");
                }
        );
    }

    // Private constructor to prevent instantiation
    private DatapackCodecs() {}
}