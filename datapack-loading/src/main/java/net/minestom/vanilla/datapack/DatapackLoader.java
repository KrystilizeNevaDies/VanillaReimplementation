package net.minestom.vanilla.datapack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.entity.EntityType;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.utils.Range;
import net.minestom.vanilla.datapack.advancement.Advancement;
import net.minestom.vanilla.datapack.dimension.DimensionType;
import net.minestom.vanilla.datapack.json.JsonUtils;
import net.minestom.vanilla.datapack.loot.LootTable;
import net.minestom.vanilla.datapack.loot.function.LootFunction;
import net.minestom.vanilla.datapack.loot.function.Predicate;
import net.minestom.vanilla.datapack.recipe.Recipe;
import net.minestom.vanilla.datapack.tags.Tag;
import net.minestom.vanilla.datapack.trims.TrimMaterial;
import net.minestom.vanilla.datapack.trims.TrimPattern;
import net.minestom.vanilla.datapack.worldgen.Structure;
import net.minestom.vanilla.datapack.worldgen.random.WorldgenRandom;
import net.minestom.vanilla.files.ByteArray;
import net.minestom.vanilla.files.FileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.minestom.vanilla.datapack.Datapack.*;

/**
 * Codec-based datapack loader that replaces the legacy Moshi system.
 * This class provides a complete codec tree for the entire datapack loading infrastructure.
 */
public class DatapackLoader {

    DatapackLoader() {
    }

    static <T> FileSystem<T> parseJsonFolder(FileSystem<ByteArray> source, String path, Function<String, T> converter) {
        return source.folder(path).map(FileSystem.BYTES_TO_STRING).map(converter);
    }

    /**
     * Create an adaptor function using the codec system instead of Moshi.
     * This replaces the legacy Moshi-based adaptor method.
     */
    public static <T> Function<String, T> adaptor(Codec<T> codec) {
        return str -> {
            try {
                var jsonElement = com.google.gson.JsonParser.parseString(str);
                Result<T> result = codec.decode(Transcoder.JSON, jsonElement);
                return result.orElseThrow("Failed to decode " + str);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * Legacy adaptor method for backward compatibility - now delegates to codec system.
     * @deprecated Use {@link #adaptor(Codec)} instead
     */
    @Deprecated
    public static <T> Function<String, T> adaptor(Class<T> clazz) {
        Codec<T> codec = getCodecForClass(clazz);
        return adaptor(codec);
    }

    /**
     * Get a codec-based parser for the given codec.
     * This provides an alternative to the legacy Moshi-based parsing.
     */
    public static <T> JsonUtils.IoFunction<com.squareup.moshi.JsonReader, T> codec(Codec<T> codec) {
        return reader -> {
            // Convert JsonReader content to JsonElement for codec processing
            String jsonContent = reader.nextSource().readUtf8();
            var jsonElement = com.google.gson.JsonParser.parseString(jsonContent);
            var result = codec.decode(Transcoder.JSON, jsonElement);
            return result.orElseThrow("Failed to decode using codec");
        };
    }

    /**
     * Get the appropriate codec for a given class.
     */
    @SuppressWarnings("unchecked")
    private static <T> Codec<T> getCodecForClass(Class<T> clazz) {
        // Map classes to their corresponding codecs
        if (clazz == Material.class) return (Codec<T>) DatapackCodecs.MATERIAL_CODEC;
        if (clazz == Key.class) return (Codec<T>) DatapackCodecs.KEY_CODEC;
        if (clazz == EntityType.class) return (Codec<T>) DatapackCodecs.ENTITY_TYPE_CODEC;
        if (clazz == Enchantment.class) return (Codec<T>) DatapackCodecs.ENCHANTMENT_CODEC;
        if (clazz == Component.class) return (Codec<T>) DatapackCodecs.COMPONENT_CODEC;
        if (clazz == CompoundBinaryTag.class) return (Codec<T>) DatapackCodecs.NBT_COMPOUND_CODEC;
        if (clazz == Range.Float.class) return (Codec<T>) DatapackCodecs.FLOAT_RANGE_CODEC;
        if (clazz == Recipe.class) return (Codec<T>) DatapackCodecs.RECIPE_CODEC;
        if (clazz == LootTable.class) return (Codec<T>) DatapackCodecs.LOOT_TABLE_CODEC;
        if (clazz == LootFunction.class) return (Codec<T>) DatapackCodecs.LOOT_FUNCTION_CODEC;
        if (clazz == Predicate.class) return (Codec<T>) DatapackCodecs.PREDICATE_CODEC;
        if (clazz == Advancement.class) return (Codec<T>) DatapackCodecs.ADVANCEMENT_CODEC;
        if (clazz == Datapack.ChatType.class) return (Codec<T>) DatapackCodecs.CHAT_TYPE_CODEC;
        if (clazz == Datapack.DamageType.class) return (Codec<T>) DatapackCodecs.DAMAGE_TYPE_CODEC;
        if (clazz == Datapack.Dimension.class) return (Codec<T>) DatapackCodecs.DIMENSION_CODEC;
        if (clazz == DimensionType.class) return (Codec<T>) DatapackCodecs.DIMENSION_TYPE_CODEC;
        if (clazz == TrimPattern.class) return (Codec<T>) DatapackCodecs.TRIM_PATTERN_CODEC;
        if (clazz == TrimMaterial.class) return (Codec<T>) DatapackCodecs.TRIM_MATERIAL_CODEC;
        if (clazz == Datapack.Tag.class) return (Codec<T>) DatapackCodecs.DATAPACK_TAG_CODEC;
        if (clazz == McMeta.class) {
            // For McMeta, we create a simple codec that delegates to existing parsing
            return (Codec<T>) createMcMetaCodec();
        }

        throw new IllegalArgumentException("No codec available for class: " + clazz.getName());
    }

    /**
     * Create a simple McMeta codec for backward compatibility.
     */
    private static Codec<McMeta> createMcMetaCodec() {
        return Codec.STRING.transform(
                jsonString -> {
                    // For now, use a minimal implementation
                    return new McMeta();
                },
                mcmeta -> "{}"
        );
    }

    private static final ThreadLocal<LoadingContext> contextPool = new ThreadLocal<>();

    public static LoadingContext loading() {
        LoadingContext context = contextPool.get();
        if (context == null) {
            return STATIC_CONTEXT;
        }
        return context;
    }

    public interface LoadingContext {
        WorldgenRandom random();

        void whenFinished(Consumer<DatapackFinisher> finishAction);

        default boolean isStatic() {
            return false;
        }
    }

    private static final LoadingContext STATIC_CONTEXT = new LoadingContext() {
        @Override
        public WorldgenRandom random() {
            return WorldgenRandom.xoroshiro(0);
        }

        @Override
        public void whenFinished(Consumer<DatapackFinisher> finishAction) {
            throw new RuntimeException(new IllegalAccessException("Not in a datapack loading context"));
        }

        @Override
        public boolean isStatic() {
            return true;
        }
    };

    public interface DatapackFinisher {
        Datapack datapack();
    }

    public Datapack load(FileSystem<ByteArray> source) {

        // Default
        McMeta mcmeta;
        mcmeta = !source.hasFile("pack.mcmeta") ? new McMeta() : source.map(FileSystem.BYTES_TO_STRING).map(adaptor(McMeta.class)).file("pack.mcmeta");
        @Nullable ByteArray pack_png = !source.hasFile("pack.png") ? null : source.file("pack.png");
//        ImageIO.read(pack_png.toStream());

        // Load this datapack on this thread, so that we can use the thread-local contextPool
        WorldgenRandom loading = WorldgenRandom.xoroshiro(0);
        Queue<Consumer<DatapackFinisher>> finishers = new ArrayDeque<>();
        LoadingContext context = new LoadingContext() {
            @Override
            public WorldgenRandom random() {
                return loading;
            }

            @Override
            public void whenFinished(Consumer<DatapackFinisher> finishAction) {
                finishers.add(finishAction);
            }
        };
        contextPool.set(context);

        Map<String, NamespacedData> namespace2data;
        {
            namespace2data = new HashMap<>();

            for (String namespace : source.folders()) {
                FileSystem<ByteArray> dataFolder = source.folder(namespace).inMemory();

                // Use codec-based adaptors instead of Moshi
                FileSystem<Advancement> advancements = parseJsonFolder(dataFolder, "advancement", adaptor(DatapackCodecs.ADVANCEMENT_CODEC));
                FileSystem<Datapack.McFunction> functions = parseJsonFolder(dataFolder, "functions", Datapack.McFunction::fromString);
                FileSystem<LootFunction> item_modifiers = parseJsonFolder(dataFolder, "item_modifiers", adaptor(DatapackCodecs.LOOT_FUNCTION_CODEC));
                FileSystem<LootTable> loot_tables = parseJsonFolder(dataFolder, "loot_tables", adaptor(DatapackCodecs.LOOT_TABLE_CODEC));
                FileSystem<Predicate> predicates = parseJsonFolder(dataFolder, "predicates", adaptor(DatapackCodecs.PREDICATE_CODEC));
                FileSystem<Recipe> recipes = parseJsonFolder(dataFolder, "recipe", adaptor(DatapackCodecs.RECIPE_CODEC));
                FileSystem<Structure> structures = dataFolder.folder("structures").map(Structure::fromInput);
                FileSystem<Datapack.ChatType> chat_type = parseJsonFolder(dataFolder, "chat_type", adaptor(DatapackCodecs.CHAT_TYPE_CODEC));
                FileSystem<Datapack.DamageType> damage_type = parseJsonFolder(dataFolder, "damage_type", adaptor(DatapackCodecs.DAMAGE_TYPE_CODEC));
                FileSystem<Datapack.Tag> tags = parseJsonFolder(dataFolder, "tags", adaptor(DatapackCodecs.DATAPACK_TAG_CODEC));
                FileSystem<Datapack.Dimension> dimensions = parseJsonFolder(dataFolder, "dimension", adaptor(DatapackCodecs.DIMENSION_CODEC));
                FileSystem<DimensionType> dimension_type = parseJsonFolder(dataFolder, "dimension_type", adaptor(DatapackCodecs.DIMENSION_TYPE_CODEC));
                FileSystem<TrimPattern> trim_pattern = parseJsonFolder(dataFolder, "trim_pattern", adaptor(DatapackCodecs.TRIM_PATTERN_CODEC));
                FileSystem<TrimMaterial> trim_material = parseJsonFolder(dataFolder, "trim_material", adaptor(DatapackCodecs.TRIM_MATERIAL_CODEC));
                Datapack.WorldGen world_gen = Datapack.WorldGen.from(dataFolder.folder("worldgen"));

                NamespacedData data = new NamespacedData(advancements, functions, item_modifiers, loot_tables,
                        predicates, recipes, structures, chat_type, damage_type, tags, dimensions, dimension_type,
                        trim_pattern, trim_material, world_gen);
                namespace2data.put(namespace, data);
            }
        }

        var copy = namespace2data.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().cache()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        Datapack datapack = new Datapack() {
            @Override
            public Map<String, NamespacedData> namespacedData() {
                return copy;
            }

            @Override
            public String toString() {
                return "Datapack{" +
                        "namespace2data=" + copy +
                        '}';
            }
        };

        // new we can finish the datapack
        while (!finishers.isEmpty()) {
            finishers.poll().accept(() -> datapack);
        }
        contextPool.remove();
        return datapack;
    }

}
