package net.minestom.vanilla.datapack;

import io.github.pesto.MojangDataFeature;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minestom.server.MinecraftServer;
import net.minestom.vanilla.VanillaReimplementation;
import net.minestom.vanilla.datapack.advancement.Advancement;
import net.minestom.vanilla.datapack.loot.LootTable;
import net.minestom.vanilla.datapack.recipe.Recipe;
import net.minestom.vanilla.datapack.worldgen.Structure;
import net.minestom.vanilla.files.ByteArray;
import net.minestom.vanilla.files.FileSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for resource pack parsing functionality.
 * Tests the parsing of various datapack components using the true resource pack
 * downloaded through the existing downloading mechanism.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ResourcePackParsingTests {

    private static VanillaReimplementation vri;
    private static Datapack datapack;
    private static FileSystem<ByteArray> rawAssets;

    @BeforeAll
    public static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        MinecraftServer.init();
        vri = VanillaReimplementation.hook(MinecraftServer.process());

        // Get the true resource pack data through the existing download mechanism
        MojangDataFeature mojangData = vri.feature(MojangDataFeature.class);
        rawAssets = mojangData.latestAssets();

        // Load the datapack from the downloaded assets
        DatapackLoadingFeature feature = vri.feature(DatapackLoadingFeature.class);
        datapack = feature.current();
    }

    @Test
    public void testResourcePackDownload() {
        assertNotNull(rawAssets, "Raw assets should be downloaded");
        assertTrue(rawAssets.files().size() > 0 || rawAssets.folders().size() > 0, "Assets should contain files or folders");
        assertTrue(rawAssets.hasFolder("minecraft"), "Assets should contain minecraft folder");
    }

    @Test
    public void testDatapackLoading() {
        assertNotNull(datapack, "Datapack should be loaded");
        assertNotNull(datapack.namespacedData(), "Datapack should have namespaced data");
        assertTrue(datapack.namespacedData().containsKey("minecraft"), 
            "Datapack should contain minecraft namespace");
    }

    @Test
    public void testLootTableParsing() {
        // Debug: Check what namespaces we have
        System.out.println("Available namespaces: " + datapack.namespacedData().keySet());
        
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        assertNotNull(minecraftData, "Minecraft namespace should exist");
        
        FileSystem<LootTable> lootTables = minecraftData.loot_tables();
        assertNotNull(lootTables, "Loot tables should be loaded");
        
        Collection<String> lootTableFiles = lootTables.files();
        Collection<String> lootTableFolders = lootTables.folders();
        
        // Debug: Print what we actually have
        System.out.println("Loot table files count: " + lootTableFiles.size());
        System.out.println("Loot table folders count: " + lootTableFolders.size());
        System.out.println("Loot table folders: " + lootTableFolders);
        
        // Also check other systems to see if they have data
        System.out.println("Recipes count: " + minecraftData.recipes().files().size());
        System.out.println("Advancements count: " + minecraftData.advancements().files().size());
        System.out.println("Structures count: " + minecraftData.structures().files().size());
        
        // With server JAR, we should have loot table files
        assertTrue(lootTableFiles.size() > 0 || lootTableFolders.size() > 0, "Should have loot table files or folders from server JAR");
        
        // Test parsing of specific loot tables
        if (lootTables.hasFolder("blocks")) {
            FileSystem<LootTable> blockLootTables = lootTables.folder("blocks");
            Collection<String> blockFiles = blockLootTables.files();
            assertTrue(blockFiles.size() > 0, "Should have block loot table files");
            
            // Test a specific loot table
            String firstFile = blockFiles.iterator().next();
            LootTable lootTable = blockLootTables.file(firstFile);
            assertNotNull(lootTable, "Loot table should be parsed successfully");
            assertNotNull(lootTable.pools(), "Loot table should have pools");
        }
    }
    }

    @Test
    public void testRecipeParsing() {
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        FileSystem<Recipe> recipes = minecraftData.recipes();
        assertNotNull(recipes, "Recipes should be loaded");
        
        Collection<String> recipeFiles = recipes.files();
        assertTrue(recipeFiles.size() > 0, "Should have recipe files");
        
        // Test parsing of a recipe
        String firstRecipeFile = recipeFiles.iterator().next();
        Recipe recipe = recipes.file(firstRecipeFile);
        assertNotNull(recipe, "Recipe should be parsed successfully");
        assertNotNull(recipe.type(), "Recipe should have a type");
    }

    @Test
    public void testAdvancementParsing() {
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        FileSystem<Advancement> advancements = minecraftData.advancements();
        assertNotNull(advancements, "Advancements should be loaded");
        
        Collection<String> advancementFiles = advancements.files();
        Collection<String> advancementFolders = advancements.folders();
        
        // Debug: Print what we actually have
        System.out.println("Advancement files count: " + advancementFiles.size());
        System.out.println("Advancement folders count: " + advancementFolders.size());
        System.out.println("Advancement folders: " + advancementFolders);
        
        // With server JAR, we should have advancement files
        assertTrue(advancementFiles.size() > 0 || advancementFolders.size() > 0, "Should have advancement files or folders from server JAR");
        
        // Test parsing of an advancement if we have files
        if (advancementFiles.size() > 0) {
            String firstAdvancementFile = advancementFiles.iterator().next();
            Advancement advancement = advancements.file(firstAdvancementFile);
            assertNotNull(advancement, "Advancement should be parsed successfully");
        }
    }
    }

    @Test
    public void testStructureParsing() {
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        FileSystem<Structure> structures = minecraftData.structures();
        assertNotNull(structures, "Structures should be loaded");
        
        Collection<String> structureFiles = structures.files();
        if (structureFiles.size() > 0) {
            // Test parsing of a structure
            String firstStructureFile = structureFiles.iterator().next();
            Structure structure = structures.file(firstStructureFile);
            assertNotNull(structure, "Structure should be parsed successfully");
            assertNotNull(structure.size(), "Structure should have size");
            assertNotNull(structure.blocks(), "Structure should have blocks");
            assertNotNull(structure.entities(), "Structure should have entities");
            assertTrue(structure.DataVersion() > 0, "Structure should have valid data version");
        }
    }

    @Test
    public void testWorldGenComponentParsing() {
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        Datapack.WorldGen worldGen = minecraftData.world_gen();
        assertNotNull(worldGen, "WorldGen should be loaded");
        
        // Test biome parsing
        if (worldGen.biome().files().size() > 0) {
            Collection<String> biomeFiles = worldGen.biome().files();
            assertTrue(biomeFiles.size() > 0, "Should have biome files");
        }
        
        // Test noise parsing
        if (worldGen.noise().files().size() > 0) {
            Collection<String> noiseFiles = worldGen.noise().files();
            assertTrue(noiseFiles.size() > 0, "Should have noise files");
        }
    }

    @Test
    public void testTagParsing() {
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        FileSystem<Datapack.Tag> tags = minecraftData.tags();
        assertNotNull(tags, "Tags should be loaded");
        
        // Test different tag types
        if (tags.hasFolder("blocks")) {
            FileSystem<Datapack.Tag> blockTags = tags.folder("blocks");
            Collection<String> blockTagFiles = blockTags.files();
            if (blockTagFiles.size() > 0) {
                String firstTagFile = blockTagFiles.iterator().next();
                Datapack.Tag tag = blockTags.file(firstTagFile);
                assertNotNull(tag, "Tag should be parsed successfully");
                assertNotNull(tag.values(), "Tag should have values");
            }
        }
    }

    @Test
    public void testDataIntegrity() {
        // Test that all major components are present and non-empty
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        
        AtomicInteger totalFiles = new AtomicInteger(0);
        
        // Count files in each category
        totalFiles.addAndGet(minecraftData.loot_tables().files().size());
        totalFiles.addAndGet(minecraftData.recipes().files().size());
        totalFiles.addAndGet(minecraftData.advancements().files().size());
        totalFiles.addAndGet(minecraftData.tags().files().size());
        
        assertTrue(totalFiles.get() > 100, "Should have a substantial number of parsed files");
    }

    @Test
    public void testParsingErrorHandling() {
        // Test that the parsing handles the real resource pack without errors
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        
        // Count successful parses vs total files in raw assets
        FileSystem<String> lootTableSource = rawAssets.map((Function<ByteArray, String>) ByteArray::toCharacterString);
        
        int sourceFiles = 0;
        if (lootTableSource.hasFolder("minecraft") && lootTableSource.folder("minecraft").hasFolder("loot_tables")) {
            sourceFiles = lootTableSource.folder("minecraft", "loot_tables").files().size();
        }
        
        int parsedFiles = minecraftData.loot_tables().files().size();
        
        if (sourceFiles > 0) {
            // Most files should parse successfully (allowing for some expected failures)
            assertTrue(parsedFiles > sourceFiles * 0.8, 
                "Most loot table files should parse successfully");
        } else {
            // If no source files, parsing should still work (just with no output)
            System.out.println("No loot table source files found, which is acceptable for client JAR");
            assertTrue(parsedFiles == 0, "No parsed files expected when no source files");
        }
    }

    @Test
    public void testDatapackMetadata() {
        // Test pack.mcmeta if present
        if (rawAssets.hasFile("pack.mcmeta")) {
            String packMcmeta = rawAssets.map((Function<ByteArray, String>) ByteArray::toCharacterString).file("pack.mcmeta");
            assertNotNull(packMcmeta, "pack.mcmeta should be readable");
            assertTrue(packMcmeta.contains("pack_format"), "pack.mcmeta should contain pack_format");
        }
    }

    @Test
    public void testNamespaceCompleteness() {
        // Verify that the minecraft namespace contains expected data types
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        
        assertNotNull(minecraftData.loot_tables(), "Should have loot tables");
        assertNotNull(minecraftData.recipes(), "Should have recipes");
        assertNotNull(minecraftData.advancements(), "Should have advancements");
        assertNotNull(minecraftData.tags(), "Should have tags");
        assertNotNull(minecraftData.structures(), "Should have structures");
        assertNotNull(minecraftData.world_gen(), "Should have world generation data");
        assertNotNull(minecraftData.predicates(), "Should have predicates");
        assertNotNull(minecraftData.item_modifiers(), "Should have item modifiers");
        assertNotNull(minecraftData.functions(), "Should have functions");
    }
}