package net.minestom.vanilla.datapack.loot.function;

import com.squareup.moshi.JsonReader;
import net.minestom.server.codec.Transcoder;
import net.minestom.vanilla.datapack.DatapackCodecs;
import net.minestom.vanilla.datapack.loot.context.LootContext;

import java.io.IOException;

public interface Predicate extends InBuiltPredicates {

    String condition();

    boolean test(LootContext context);

    static Predicate fromJson(JsonReader reader) throws Exception {
        // Convert JsonReader to JSON string and use raw codec parsing
        String jsonString = reader.nextSource().readUtf8();
        var jsonElement = com.google.gson.JsonParser.parseString(jsonString);
        return DatapackCodecs.PREDICATE_CODEC.decode(Transcoder.JSON, jsonElement).orElseThrow("Failed to decode predicate");
    }
}
