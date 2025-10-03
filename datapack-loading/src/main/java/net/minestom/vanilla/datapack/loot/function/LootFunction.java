package net.minestom.vanilla.datapack.loot.function;

import com.squareup.moshi.JsonReader;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.item.ItemStack;
import net.minestom.vanilla.datapack.DatapackCodecs;
import net.minestom.vanilla.datapack.loot.context.LootContext;

import java.io.IOException;
import java.util.Map;
import java.util.random.RandomGenerator;

// aka ItemFunction, or ItemModifier
// https://minecraft.fandom.com/wiki/Item_modifier
public interface LootFunction extends InBuiltLootFunctions {

    /**
     * @return The function id.
     */
    Key function();

    /**
     * Applies the function to the item stack.
     *
     * @param context the function context
     * @return the modified item stack
     */
    ItemStack apply(Context context);

    static LootFunction fromJson(JsonReader reader) throws Exception {
        // Convert JsonReader to JSON string and use raw codec parsing
        String jsonString = reader.nextSource().readUtf8();
        var jsonElement = com.google.gson.JsonParser.parseString(jsonString);
        return DatapackCodecs.LOOT_FUNCTION_CODEC.decode(Transcoder.JSON, jsonElement).orElseThrow("Failed to decode loot function");
    }

    /**
     * The context of the function.
     */
    interface Context extends LootContext {
        /**
         * The random generator used by the function.
         *
         * @return the random generator
         */
        RandomGenerator random();

        /**
         * The item stack to apply the function to.
         *
         * @return the previous item stack
         */
        ItemStack itemStack();
    }
}
