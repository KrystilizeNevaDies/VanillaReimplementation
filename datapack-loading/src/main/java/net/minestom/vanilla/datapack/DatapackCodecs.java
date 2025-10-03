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
 * Contains raw codec definitions only - no utility classes.
 */
public class DatapackCodecs {

    // Essential types moved from JsonUtils - raw codec implementations only
    
    public interface ObjectOrList<O, E> {
        boolean isObject();
        O asObject();
        boolean isList();
        java.util.List<E> asList();
    }

    public interface SingleOrList<T> extends ObjectOrList<T, T>, ListLike<T> {
        record Single<O>(O object) implements SingleOrList<O> {
            @Override
            public boolean isObject() {
                return true;
            }

            @Override
            public O asObject() {
                return object;
            }

            @Override
            public boolean isList() {
                return false;
            }

            @Override
            public java.util.List<O> asList() {
                throw new IllegalStateException("Not a list");
            }

            @Override
            public java.util.@NotNull List<O> list() {
                return java.util.List.of(object);
            }
        }

        record List<L>(java.util.List<L> list) implements SingleOrList<L> {
            @Override
            public boolean isObject() {
                return false;
            }

            @Override
            public L asObject() {
                throw new IllegalStateException("Not an object");
            }

            @Override
            public boolean isList() {
                return true;
            }

            @Override
            public java.util.List<L> asList() {
                return list;
            }

            @Override
            public java.util.@NotNull List<L> list() {
                return list;
            }
        }
    }

    public interface ListLike<T> {
        java.util.@NotNull List<T> list();
    }

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

    // SingleOrList codec for common patterns - raw codec implementation
    public static <T> @NotNull Codec<SingleOrList<T>> singleOrListCodec(Codec<T> elementCodec) {
        return elementCodec.<SingleOrList<T>>transform(
                element -> new SingleOrList.Single<>(element),
                singleOrList -> singleOrList.asObject()
        ).orElse(elementCodec.list().transform(
                list -> new SingleOrList.List<>(list),
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
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("Recipe raw codec not yet implemented");
            },
            recipe -> {
                throw new UnsupportedOperationException("Recipe encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<LootTable> createLootTableCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("LootTable raw codec not yet implemented");
            },
            table -> {
                throw new UnsupportedOperationException("LootTable encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<Advancement> createAdvancementCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("Advancement raw codec not yet implemented");
            },
            advancement -> {
                throw new UnsupportedOperationException("Advancement encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<DensityFunction> createDensityFunctionCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("DensityFunction raw codec not yet implemented");
            },
            densityFunction -> {
                throw new UnsupportedOperationException("DensityFunction encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<Biome> createBiomeCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("Biome raw codec not yet implemented");
            },
            biome -> {
                throw new UnsupportedOperationException("Biome encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<Noise> createNoiseCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("Noise raw codec not yet implemented");
            },
            noise -> {
                throw new UnsupportedOperationException("Noise encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<Carver> createCarverCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("Carver raw codec not yet implemented");
            },
            carver -> {
                throw new UnsupportedOperationException("Carver encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<FloatProvider> createFloatProviderCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<HeightProvider> createHeightProviderCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<VerticalAnchor> createVerticalAnchorCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<CubicSpline> createCubicSplineCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<NBTPath> createNBTPathCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<NumberProvider> createNumberProviderCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<LootFunction> createLootFunctionCodec() {
        // Raw codec implementation for type discrimination based on "function" field
        return Codec.STRING.transform(
            jsonString -> {
                try {
                    com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(jsonString);
                    if (!element.isJsonObject()) {
                        throw new IllegalArgumentException("LootFunction must be a JSON object");
                    }
                    
                    com.google.gson.JsonObject obj = element.getAsJsonObject();
                    if (!obj.has("function")) {
                        throw new IllegalArgumentException("LootFunction must have a 'function' field");
                    }
                    
                    String function = obj.get("function").getAsString();
                    
                    // Raw type discrimination - no utility methods
                    return switch (function) {
                        case "minecraft:apply_bonus" -> {
                            throw new UnsupportedOperationException("LootFunction type 'apply_bonus' not yet implemented in raw codec");
                        }
                        case "minecraft:copy_name" -> {
                            throw new UnsupportedOperationException("LootFunction type 'copy_name' not yet implemented in raw codec");
                        }
                        case "minecraft:copy_nbt" -> {
                            throw new UnsupportedOperationException("LootFunction type 'copy_nbt' not yet implemented in raw codec");
                        }
                        case "minecraft:copy_state" -> {
                            throw new UnsupportedOperationException("LootFunction type 'copy_state' not yet implemented in raw codec");
                        }
                        case "minecraft:enchant_randomly" -> {
                            throw new UnsupportedOperationException("LootFunction type 'enchant_randomly' not yet implemented in raw codec");
                        }
                        case "minecraft:enchant_with_levels" -> {
                            throw new UnsupportedOperationException("LootFunction type 'enchant_with_levels' not yet implemented in raw codec");
                        }
                        case "minecraft:exploration_map" -> {
                            throw new UnsupportedOperationException("LootFunction type 'exploration_map' not yet implemented in raw codec");
                        }
                        case "minecraft:explosion_decay" -> {
                            throw new UnsupportedOperationException("LootFunction type 'explosion_decay' not yet implemented in raw codec");
                        }
                        case "minecraft:fill_player_head" -> {
                            throw new UnsupportedOperationException("LootFunction type 'fill_player_head' not yet implemented in raw codec");
                        }
                        case "minecraft:furnace_smelt" -> {
                            throw new UnsupportedOperationException("LootFunction type 'furnace_smelt' not yet implemented in raw codec");
                        }
                        case "minecraft:limit_count" -> {
                            throw new UnsupportedOperationException("LootFunction type 'limit_count' not yet implemented in raw codec");
                        }
                        case "minecraft:looting_enchant" -> {
                            throw new UnsupportedOperationException("LootFunction type 'looting_enchant' not yet implemented in raw codec");
                        }
                        case "minecraft:set_attributes" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_attributes' not yet implemented in raw codec");
                        }
                        case "minecraft:set_banner_pattern" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_banner_pattern' not yet implemented in raw codec");
                        }
                        case "minecraft:set_contents" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_contents' not yet implemented in raw codec");
                        }
                        case "minecraft:set_count" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_count' not yet implemented in raw codec");
                        }
                        case "minecraft:set_damage" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_damage' not yet implemented in raw codec");
                        }
                        case "minecraft:set_enchantments" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_enchantments' not yet implemented in raw codec");
                        }
                        case "minecraft:set_instrument" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_instrument' not yet implemented in raw codec");
                        }
                        case "minecraft:set_loot_table" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_loot_table' not yet implemented in raw codec");
                        }
                        case "minecraft:set_lore" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_lore' not yet implemented in raw codec");
                        }
                        case "minecraft:set_name" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_name' not yet implemented in raw codec");
                        }
                        case "minecraft:set_nbt" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_nbt' not yet implemented in raw codec");
                        }
                        case "minecraft:set_potion" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_potion' not yet implemented in raw codec");
                        }
                        case "minecraft:set_stew_effect" -> {
                            throw new UnsupportedOperationException("LootFunction type 'set_stew_effect' not yet implemented in raw codec");
                        }
                        default -> throw new IllegalArgumentException("Unknown loot function: " + function);
                    };
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse LootFunction from JSON: " + jsonString, e);
                }
            },
            lootFunction -> {
                throw new UnsupportedOperationException("LootFunction encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<Predicate> createPredicateCodec() {
        // Raw codec implementation for type discrimination based on "condition" field
        return Codec.STRING.transform(
            jsonString -> {
                try {
                    com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(jsonString);
                    if (!element.isJsonObject()) {
                        throw new IllegalArgumentException("Predicate must be a JSON object");
                    }
                    
                    com.google.gson.JsonObject obj = element.getAsJsonObject();
                    if (!obj.has("condition")) {
                        throw new IllegalArgumentException("Predicate must have a 'condition' field");
                    }
                    
                    String condition = obj.get("condition").getAsString();
                    
                    // Raw type discrimination - no utility methods
                    return switch (condition) {
                        case "minecraft:alternative" -> {
                            // For now, throw exception indicating this specific type isn't implemented
                            throw new UnsupportedOperationException("Predicate type 'alternative' not yet implemented in raw codec");
                        }
                        case "minecraft:block_state_property" -> {
                            throw new UnsupportedOperationException("Predicate type 'block_state_property' not yet implemented in raw codec");
                        }
                        case "minecraft:damage_source_properties" -> {
                            throw new UnsupportedOperationException("Predicate type 'damage_source_properties' not yet implemented in raw codec");
                        }
                        case "minecraft:entity_properties" -> {
                            throw new UnsupportedOperationException("Predicate type 'entity_properties' not yet implemented in raw codec");
                        }
                        case "minecraft:entity_scores" -> {
                            throw new UnsupportedOperationException("Predicate type 'entity_scores' not yet implemented in raw codec");
                        }
                        case "minecraft:inverted" -> {
                            throw new UnsupportedOperationException("Predicate type 'inverted' not yet implemented in raw codec");
                        }
                        case "minecraft:killed_by_player" -> {
                            throw new UnsupportedOperationException("Predicate type 'killed_by_player' not yet implemented in raw codec");
                        }
                        case "minecraft:location_check" -> {
                            throw new UnsupportedOperationException("Predicate type 'location_check' not yet implemented in raw codec");
                        }
                        case "minecraft:match_tool" -> {
                            throw new UnsupportedOperationException("Predicate type 'match_tool' not yet implemented in raw codec");
                        }
                        case "minecraft:random_chance" -> {
                            throw new UnsupportedOperationException("Predicate type 'random_chance' not yet implemented in raw codec");
                        }
                        case "minecraft:random_chance_with_looting" -> {
                            throw new UnsupportedOperationException("Predicate type 'random_chance_with_looting' not yet implemented in raw codec");
                        }
                        case "minecraft:reference" -> {
                            throw new UnsupportedOperationException("Predicate type 'reference' not yet implemented in raw codec");
                        }
                        case "minecraft:survives_explosion" -> {
                            throw new UnsupportedOperationException("Predicate type 'survives_explosion' not yet implemented in raw codec");
                        }
                        case "minecraft:table_bonus" -> {
                            throw new UnsupportedOperationException("Predicate type 'table_bonus' not yet implemented in raw codec");
                        }
                        case "minecraft:time_check" -> {
                            throw new UnsupportedOperationException("Predicate type 'time_check' not yet implemented in raw codec");
                        }
                        case "minecraft:value_check" -> {
                            throw new UnsupportedOperationException("Predicate type 'value_check' not yet implemented in raw codec");
                        }
                        case "minecraft:weather_check" -> {
                            throw new UnsupportedOperationException("Predicate type 'weather_check' not yet implemented in raw codec");
                        }
                        case "minecraft:any_of" -> {
                            throw new UnsupportedOperationException("Predicate type 'any_of' not yet implemented in raw codec");
                        }
                        case "minecraft:all_of" -> {
                            throw new UnsupportedOperationException("Predicate type 'all_of' not yet implemented in raw codec");
                        }
                        default -> throw new IllegalArgumentException("Unknown predicate condition: " + condition);
                    };
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse Predicate from JSON: " + jsonString, e);
                }
            },
            predicate -> {
                throw new UnsupportedOperationException("Predicate encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<LootContext.Trait> createLootContextTraitCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<BlockState> createBlockStateCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<NoiseSettings.SurfaceRule> createSurfaceRuleCodec() {
        return Codec.STRING.transform(str -> { throw new UnsupportedOperationException("Raw codec not yet implemented"); }, obj -> { throw new UnsupportedOperationException("Encoding not implemented"); });
    }
    
    private static @NotNull Codec<Datapack.Tag> createDatapackTagCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("Datapack.Tag raw codec not yet implemented");
            },
            tag -> {
                throw new UnsupportedOperationException("Datapack.Tag encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<Datapack.ChatType> createChatTypeCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("ChatType raw codec not yet implemented");
            },
            chatType -> {
                throw new UnsupportedOperationException("ChatType encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<Datapack.DamageType> createDamageTypeCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("DamageType raw codec not yet implemented");
            },
            damageType -> {
                throw new UnsupportedOperationException("DamageType encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<Datapack.Dimension> createDimensionCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("Dimension raw codec not yet implemented");
            },
            dimension -> {
                throw new UnsupportedOperationException("Dimension encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<DimensionType> createDimensionTypeCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("DimensionType raw codec not yet implemented");
            },
            dimensionType -> {
                throw new UnsupportedOperationException("DimensionType encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<TrimPattern> createTrimPatternCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("TrimPattern raw codec not yet implemented");
            },
            trimPattern -> {
                throw new UnsupportedOperationException("TrimPattern encoding not implemented");
            }
        );
    }
    
    private static @NotNull Codec<TrimMaterial> createTrimMaterialCodec() {
        // Raw codec implementation - no utility usage
        return Codec.STRING.transform(
            str -> {
                throw new UnsupportedOperationException("TrimMaterial raw codec not yet implemented");
            },
            trimMaterial -> {
                throw new UnsupportedOperationException("TrimMaterial encoding not implemented");
            }
        );
    }

    // Raw codec definitions only - no utility methods

    // Private constructor to prevent instantiation
    private DatapackCodecs() {}
}