package net.minestom.vanilla.datapack.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Codec-based utilities replacing the legacy JsonUtils/Moshi system.
 * All parsing is now done through Minecraft's codec system.
 */
public class JsonUtils {

    public static JsonElement jsonReader(String source) {
        return JsonParser.parseString(source);
    }

    public interface ObjectOrList<O, E> {
        /** Throws an exception if this is not an object */
        boolean isObject();
        O asObject();

        boolean isList();

        /** Throws an exception if this is not a list */
        List<E> asList();
    }

    public interface SingleOrList<T> extends ObjectOrList<T, T>, ListLike<T> {
        static <T> SingleOrList<T> fromJson(Codec<T> elementCodec, JsonElement jsonElement) {
            if (jsonElement.isJsonArray()) {
                Stream.Builder<T> builder = Stream.builder();
                for (JsonElement element : jsonElement.getAsJsonArray()) {
                    Result<T> result = elementCodec.decode(Transcoder.JSON, element);
                    builder.add(result.orElseThrow("Failed to decode array element"));
                }
                return new List<>(builder.build().toList());
            } else {
                Result<T> result = elementCodec.decode(Transcoder.JSON, jsonElement);
                return new Single<>(result.orElseThrow("Failed to decode single element"));
            }
        }

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

    public interface IoFunction<T, R> {
        R apply(T t) throws Exception;
    }

    public static <T> T unionStringType(JsonElement jsonElement, String key, Function<String, Codec<T>> findCodec) {
        if (!jsonElement.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for union type parsing");
        }

        var jsonObject = jsonElement.getAsJsonObject();
        if (!jsonObject.has(key)) {
            throw new IllegalArgumentException("Missing key '" + key + "' in JSON object");
        }

        String typeValue = jsonObject.get(key).getAsString();
        Codec<T> codec = findCodec.apply(typeValue);
        if (codec == null) {
            throw new IllegalArgumentException("No codec found for type: " + typeValue);
        }

        Result<T> result = codec.decode(Transcoder.JSON, jsonElement);
        return result.orElseThrow("Failed to decode union type with key: " + typeValue);
    }

    public static <T> T unionStringTypeAdapted(com.squareup.moshi.JsonReader reader, String key, Function<String, Class<? extends T>> findReader) throws Exception {
        // Convert JsonReader to JsonElement and use codec approach
        String jsonContent = reader.nextSource().readUtf8();
        var jsonElement = com.google.gson.JsonParser.parseString(jsonContent);
        
        return unionStringType(jsonElement, key, str -> {
            Class<? extends T> clazz = findReader.apply(str);
            if (clazz == null) return null;
            // Try to get a codec for this class
            try {
                return (Codec<T>) net.minestom.vanilla.datapack.DatapackLoader.getCodecForClass((Class<T>) clazz);
            } catch (Exception e) {
                // If no codec available, create a simple string codec for now
                return (Codec<T>) Codec.STRING.transform(s -> {
                    throw new UnsupportedOperationException("No codec available for " + clazz.getSimpleName());
                }, Object::toString);
            }
        });
    }

    public static <T> T unionStringTypeMap(JsonElement jsonElement, String key, Map<String, Codec<T>> codecMap) {
        return unionStringType(jsonElement, key, codecMap::get);
    }

    public static <T> T unionStringTypeMapAdapted(JsonElement jsonElement, String key, Map<String, Codec<T>> codecMap) {
        return unionStringTypeMap(jsonElement, key, codecMap);
    }

    public static <V, T> T unionMapType(JsonElement jsonElement, String key, Function<JsonElement, V> read, Function<V, Codec<T>> findCodec) {
        if (!jsonElement.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for union type parsing");
        }

        var jsonObject = jsonElement.getAsJsonObject();
        if (!jsonObject.has(key)) {
            throw new IllegalArgumentException("Missing key '" + key + "' in JSON object");
        }

        V property = read.apply(jsonObject.get(key));
        Codec<T> codec = findCodec.apply(property);
        if (codec == null) {
            throw new IllegalArgumentException("No codec found for property: " + property);
        }

        Result<T> result = codec.decode(Transcoder.JSON, jsonElement);
        return result.orElseThrow("Failed to decode union type");
    }

    public static <T> T typeMapMapped(com.squareup.moshi.JsonReader reader, Map<com.squareup.moshi.JsonReader.Token, JsonUtils.IoFunction<com.squareup.moshi.JsonReader, T>> type2readFunction) throws Exception {
        // Convert JsonReader approach to JsonElement approach
        String jsonContent = reader.nextSource().readUtf8();
        var jsonElement = com.google.gson.JsonParser.parseString(jsonContent);
        
        // Map JsonReader.Token to JsonElementType
        Map<JsonElementType, Codec<T>> codecMap = new java.util.HashMap<>();
        for (var entry : type2readFunction.entrySet()) {
            JsonElementType elementType = mapTokenToElementType(entry.getKey());
            // Create a codec that uses the original function
            Codec<T> codec = Codec.STRING.transform(
                jsonString -> {
                    try {
                        var tempReader = com.squareup.moshi.JsonReader.of(new okio.Buffer().writeUtf8(jsonString));
                        return entry.getValue().apply(tempReader);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse with legacy function", e);
                    }
                },
                value -> {
                    throw new UnsupportedOperationException("Encoding not supported");
                }
            );
            codecMap.put(elementType, codec);
        }
        
        return typeMapMapped(jsonElement, codecMap);
    }

    private static JsonElementType mapTokenToElementType(com.squareup.moshi.JsonReader.Token token) {
        return switch (token) {
            case BEGIN_OBJECT -> JsonElementType.OBJECT;
            case BEGIN_ARRAY -> JsonElementType.ARRAY;
            case STRING -> JsonElementType.STRING;
            case NUMBER -> JsonElementType.NUMBER;
            case BOOLEAN -> JsonElementType.BOOLEAN;
            case NULL -> JsonElementType.NULL;
            default -> throw new IllegalArgumentException("Unknown token: " + token);
        };
    }

    public static <T> T unionStringTypeMapAdapted(com.squareup.moshi.JsonReader reader, String key, Map<String, Class<? extends T>> map) throws Exception {
        // Convert JsonReader to JsonElement and create codec map
        String jsonContent = reader.nextSource().readUtf8();
        var jsonElement = com.google.gson.JsonParser.parseString(jsonContent);
        
        Map<String, Codec<T>> codecMap = new java.util.HashMap<>();
        for (var entry : map.entrySet()) {
            String mapKey = entry.getKey();
            Class<? extends T> clazz = entry.getValue();
            try {
                Codec<T> codec = net.minestom.vanilla.datapack.DatapackLoader.getCodecForClass((Class<T>) clazz);
                codecMap.put(mapKey, codec);
            } catch (Exception e) {
                // Create a placeholder codec for unsupported types
                codecMap.put(mapKey, (Codec<T>) Codec.STRING.transform(s -> {
                    throw new UnsupportedOperationException("No codec for " + clazz.getSimpleName());
                }, Object::toString));
            }
        }
        
        return unionStringTypeMapAdapted(jsonElement, key, codecMap);
    }

    public static <T> T typeMapMapped(JsonElement jsonElement, Map<JsonElementType, Codec<T>> codecMap) {
        JsonElementType elementType = getJsonElementType(jsonElement);
        Codec<T> codec = codecMap.get(elementType);
        if (codec == null) {
            throw new IllegalArgumentException("No codec mapping found for JSON element type: " + elementType);
        }
        
        Result<T> result = codec.decode(Transcoder.JSON, jsonElement);
        return result.orElseThrow("Failed to decode mapped type");
    }

    public static <T> T typeMap(com.squareup.moshi.JsonReader reader, JsonUtils.IoFunction<com.squareup.moshi.JsonReader.Token, JsonUtils.IoFunction<com.squareup.moshi.JsonReader, T>> type2readFunction) throws Exception {
        // Convert JsonReader to JsonElement and token-based approach to codec approach
        String jsonContent = reader.nextSource().readUtf8();
        var jsonElement = com.google.gson.JsonParser.parseString(jsonContent);
        
        // Determine the token type
        com.squareup.moshi.JsonReader.Token token = getTokenFromJsonElement(jsonElement);
        JsonUtils.IoFunction<com.squareup.moshi.JsonReader, T> readFunction = type2readFunction.apply(token);
        
        if (readFunction == null) {
            throw new IllegalArgumentException("No reader function found for token: " + token);
        }
        
        // Create a temporary JsonReader for the function
        var tempReader = com.squareup.moshi.JsonReader.of(new okio.Buffer().writeUtf8(jsonElement.toString()));
        return readFunction.apply(tempReader);
    }

    private static com.squareup.moshi.JsonReader.Token getTokenFromJsonElement(com.google.gson.JsonElement element) {
        if (element.isJsonObject()) return com.squareup.moshi.JsonReader.Token.BEGIN_OBJECT;
        if (element.isJsonArray()) return com.squareup.moshi.JsonReader.Token.BEGIN_ARRAY;
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) return com.squareup.moshi.JsonReader.Token.STRING;
            if (primitive.isNumber()) return com.squareup.moshi.JsonReader.Token.NUMBER;
            if (primitive.isBoolean()) return com.squareup.moshi.JsonReader.Token.BOOLEAN;
        }
        if (element.isJsonNull()) return com.squareup.moshi.JsonReader.Token.NULL;
        throw new IllegalArgumentException("Unknown JSON element type");
    }

    public static <T> T typeMap(JsonElement jsonElement, Function<JsonElementType, Codec<T>> findCodec) {
        JsonElementType elementType = getJsonElementType(jsonElement);
        Codec<T> codec = findCodec.apply(elementType);
        if (codec == null) {
            throw new IllegalArgumentException("No codec found for JSON element type: " + elementType);
        }

        Result<T> result = codec.decode(Transcoder.JSON, jsonElement);
        return result.orElseThrow("Failed to decode type");
    }

    public enum JsonElementType {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }

    private static JsonElementType getJsonElementType(JsonElement element) {
        if (element.isJsonObject()) return JsonElementType.OBJECT;
        if (element.isJsonArray()) return JsonElementType.ARRAY;
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isString()) return JsonElementType.STRING;
            if (primitive.isNumber()) return JsonElementType.NUMBER;
            if (primitive.isBoolean()) return JsonElementType.BOOLEAN;
        }
        if (element.isJsonNull()) return JsonElementType.NULL;
        throw new IllegalArgumentException("Unknown JSON element type");
    }

    @Nullable
    public static <T> T findProperty(JsonElement jsonElement, String propertyName, Function<JsonElement, T> reader) {
        if (!jsonElement.isJsonObject()) {
            return null;
        }
        
        var jsonObject = jsonElement.getAsJsonObject();
        if (!jsonObject.has(propertyName)) {
            return null;
        }
        
        return reader.apply(jsonObject.get(propertyName));
    }

    public static boolean hasProperty(JsonElement jsonElement, String property) {
        if (!jsonElement.isJsonObject()) {
            return false;
        }
        return jsonElement.getAsJsonObject().has(property);
    }

    public static <T> Map<String, T> readObjectToMap(JsonElement jsonElement, Codec<T> valueCodec) {
        if (!jsonElement.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object");
        }

        Map<String, T> map = new HashMap<>();
        var jsonObject = jsonElement.getAsJsonObject();
        
        for (var entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            
            Result<T> result = valueCodec.decode(Transcoder.JSON, value);
            map.put(key, result.orElseThrow("Failed to decode map value for key: " + key));
        }
        
        return Collections.unmodifiableMap(map);
    }

    // Legacy compatibility - convert old JsonReader patterns to JsonElement + Codec patterns
    public static String jsonElementToString(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return element.toString();
    }

    // Private constructor to prevent instantiation
    private JsonUtils() {}
}
