package net.minestom.vanilla.datapack.tags;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

public record Tag(String namespace, String value) implements Key {
    public Tag(String string) {
        this(parseNamespace(string), parseValue(string));
    }
    
    private static String parseNamespace(String string) {
        // Handle cases where the string might start with # (tag reference)
        String cleaned = string.startsWith("#") ? string.substring(1) : string;
        return cleaned.split(":")[0];
    }
    
    private static String parseValue(String string) {
        // Handle cases where the string might start with # (tag reference)
        String cleaned = string.startsWith("#") ? string.substring(1) : string;
        return cleaned.split(":")[1];
    }

    @Override
    public @NotNull String asString() {
        return namespace + ":" + value;
    }
}
