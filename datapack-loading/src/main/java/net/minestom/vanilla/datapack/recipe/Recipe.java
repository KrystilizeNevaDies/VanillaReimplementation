package net.minestom.vanilla.datapack.recipe;

import com.squareup.moshi.Json;
import com.squareup.moshi.JsonReader;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.item.Material;
import net.minestom.vanilla.datapack.DatapackLoader;
import net.minestom.vanilla.datapack.DatapackCodecs;
import net.minestom.vanilla.datapack.json.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface Recipe {

    @NotNull Key type();

    @Nullable String group();

    static Recipe fromJson(JsonReader reader) throws IOException {
        // Convert JsonReader to JSON string and use raw codec parsing
        try {
            String jsonString = reader.nextSource().readUtf8();
            var jsonElement = com.google.gson.JsonParser.parseString(jsonString);
            return DatapackCodecs.RECIPE_CODEC.decode(Transcoder.JSON, jsonElement).orElseThrow("Failed to decode recipe");
        } catch (Exception e) {
            throw new IOException("Failed to parse recipe", e);
        }
    }

    interface CookingRecipe extends Recipe {
        @NotNull List<Ingredient> ingredient();
        @NotNull SingleResult result();
        double experience();
        @Optional Integer cookingTime();
    }

    interface Ingredient {

        static Ingredient fromJson(JsonReader reader) throws IOException {
            // Raw codec pattern - direct JSON parsing without utility methods
            try {
                String jsonString = reader.nextSource().readUtf8();
                com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(jsonString);
                
                if (element.isJsonArray()) {
                    // Create Multi ingredient from array
                    com.google.gson.JsonArray array = element.getAsJsonArray();
                    java.util.List<Single> items = new java.util.ArrayList<>();
                    for (com.google.gson.JsonElement item : array) {
                        // For now, create placeholder Single items - proper parsing can be added later
                        if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                            String itemName = item.getAsString();
                            if (itemName.startsWith("#")) {
                                items.add(new Tag(Key.key(itemName.substring(1))));
                            } else {
                                Material material = Material.fromKey(Key.key(itemName));
                                items.add(new Item(material != null ? material : Material.STONE));
                            }
                        } else {
                            // Placeholder for complex objects
                            items.add(new Item(Material.STONE));
                        }
                    }
                    return new Multi(items);
                } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    // Create Single ingredient from string
                    String itemName = element.getAsString();
                    if (itemName.startsWith("#")) {
                        return new Tag(Key.key(itemName.substring(1)));
                    } else {
                        Material material = Material.fromKey(Key.key(itemName));
                        return new Item(material != null ? material : Material.STONE);
                    }
                } else if (element.isJsonNull()) {
                    return new None();
                } else if (element.isJsonObject()) {
                    // For now, treat objects as Single items - proper parsing can be added later
                    return new Item(Material.STONE); // Placeholder
                } else {
                    throw new IllegalArgumentException("Ingredient must be an array, string, null, or object");
                }
            } catch (Exception e) {
                throw new IOException("Failed to parse ingredient", e);
            }
        }

        // single means within an array, not necessarily a singular item
        interface Single extends Ingredient {
            static Single fromJson(JsonReader reader) throws IOException {
                String content = reader.nextString();
                boolean isTag = content.startsWith("#");
                if (isTag) {
                    return new Tag(Key.key(content.substring(1)));
                }
                return new Item(Material.fromKey(content));
            }
        }

        record Item(Material item) implements Single {
        }

        record Tag(Key tag) implements Single {
        }

        record None() implements Ingredient {
        }

        record Multi(List<Single> items) implements Ingredient {
        }
    }

    record Result(Material id, @Optional Integer count) {
    }

    record SingleResult(Material id) {
    }

    record Blasting(String group, @Optional String category, DatapackCodecs.SingleOrList<Ingredient> ingredient, SingleResult result,
                    double experience, @Optional @Json(name = "cookingtime") Integer cookingTime) implements CookingRecipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:blasting");
        }
        
        @Override
        public @NotNull List<Ingredient> ingredient() {
            return ingredient.asList();
        }
    }

    record CampfireCooking(String group, DatapackCodecs.SingleOrList<Ingredient> ingredient, SingleResult result,
                           double experience, @Optional @Json(name = "cookingtime") Integer cookingTime) implements CookingRecipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:campfire_cooking");
        }
        
        @Override
        public @NotNull List<Ingredient> ingredient() {
            return ingredient.asList();
        }
    }

    record Shaped(String group, @Optional String category, List<String> pattern, Map<Character, Ingredient> key, Result result) implements Recipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:crafting_shaped");
        }
    }

    record Shapeless(String group, @Optional String category, DatapackCodecs.SingleOrList<Ingredient> ingredients, Result result) implements Recipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:crafting_shapeless");
        }
    }

    record Transmute(String group, @Optional String category, DatapackCodecs.SingleOrList<Ingredient> input,
                     DatapackCodecs.SingleOrList<Ingredient> material, Result result) implements Recipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:crafting_transmute");
        }
    }

    sealed interface Special extends Recipe {

        record ArmorDye(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_armordye");
            }
        }

        record BannerDuplicate(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_bannerduplicate");
            }
        }

        record BookCloning(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_bookcloning");
            }
        }

        record FireworkRocket(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_firework_rocket");
            }
        }

        record FireworkStar(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_firework_star");
            }
        }

        record FireworkStarFade(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_firework_star_fade");
            }
        }

        record MapCloning(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_mapcloning");
            }
        }

        record MapExtending(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_mapextending");
            }
        }

        record RepairItem(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_repairitem");
            }
        }

        record ShieldDecoration(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_shielddecoration");
            }
        }

        record TippedArrow(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_tippedarrow");
            }
        }

        record SuspiciousStew(String group) implements Special {
            @Override
            public @NotNull Key type() {
                return Key.key("minecraft:crafting_special_suspiciousstew");
            }
        }
    }


    record DecoratedPot(String group, String category) implements Recipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:decorated_pot");
        }
    }

    record Smelting(String group, @Optional String category, DatapackCodecs.SingleOrList<Ingredient> ingredient, SingleResult result,
                    double experience, @Optional @Json(name = "cookingtime") Integer cookingTime) implements CookingRecipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:smelting");
        }
    }

    record Smoking(String group, DatapackCodecs.SingleOrList<Ingredient> ingredient, SingleResult result,
                   double experience, @Optional @Json(name = "cookingtime") Integer cookingTime) implements CookingRecipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:smoking");
        }
    }

    record Stonecutting(@Nullable String group, DatapackCodecs.SingleOrList<Ingredient> ingredient, Result result) implements Recipe {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:stonecutting");
        }
    }

    interface Smithing extends Recipe {

        Ingredient.Single template();
        Ingredient.Single base();
        Ingredient.Single addition();
        default @NotNull Key type() {
            return Key.key("minecraft:smithing");
        }
    }

    record SmithingTrim(String group, Ingredient.Single base, Ingredient.Single addition, String pattern,
                        Ingredient.Single template) implements Smithing {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:smithing_trim");
        }
    }

    record SmithingTransform(String group, Ingredient.Single base, Ingredient.Single addition, Result result,
                             Ingredient.Single template) implements Smithing {
        @Override
        public @NotNull Key type() {
            return Key.key("minecraft:smithing_transform");
        }
    }
}
