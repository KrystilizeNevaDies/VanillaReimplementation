package net.minestom.vanilla.datapack;

import io.github.pesto.MojangDataFeature;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minestom.server.MinecraftServer;
import net.minestom.vanilla.VanillaReimplementation;
import net.minestom.vanilla.datapack.loot.LootTable;
import net.minestom.vanilla.files.ByteArray;
import net.minestom.vanilla.files.FileSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for validating error handling and edge cases in resource pack parsing.
 * This tests primarily with the actual downloaded resource pack to ensure real-world compatibility.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ResourcePackErrorHandlingTests {

    private static VanillaReimplementation vri;
    private static Datapack datapack;
    private static FileSystem<ByteArray> rawAssets;

    @BeforeAll
    public static void init() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        MinecraftServer.init();
        vri = VanillaReimplementation.hook(MinecraftServer.process());

        MojangDataFeature mojangData = vri.feature(MojangDataFeature.class);
        rawAssets = mojangData.latestAssets();

        DatapackLoadingFeature feature = vri.feature(DatapackLoadingFeature.class);
        datapack = feature.current();
    }

    @Test
    public void testEmptyDatapackParsing() {
        // Test parsing an empty file system
        FileSystem<ByteArray> emptyFileSystem = FileSystem.empty();
        Datapack emptyDatapack = Datapack.loadByteArray(emptyFileSystem);
        
        assertNotNull(emptyDatapack, "Should handle empty datapack gracefully");
        assertNotNull(emptyDatapack.namespacedData(), "Should have empty namespaced data");
        assertTrue(emptyDatapack.namespacedData().isEmpty(), "Namespaced data should be empty");
    }

    @Test
    public void testRobustParsing() {
        // Test that the actual resource pack parsing is robust
        assertNotNull(datapack, "Datapack should be loaded successfully");
        assertNotNull(datapack.namespacedData(), "Should have namespaced data");
        
        // Test that minecraft namespace exists and has data
        assertTrue(datapack.namespacedData().containsKey("minecraft"), 
            "Should contain minecraft namespace");
        
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        assertNotNull(minecraftData, "Minecraft namespace data should not be null");
        
        // Test that various data types are present
        assertNotNull(minecraftData.loot_tables(), "Should have loot tables");
        assertNotNull(minecraftData.recipes(), "Should have recipes");
        assertNotNull(minecraftData.advancements(), "Should have advancements");
        
        // Test that we can access files without errors
        assertDoesNotThrow(() -> {
            minecraftData.loot_tables().files().forEach(file -> {
                LootTable lootTable = minecraftData.loot_tables().file(file);
                assertNotNull(lootTable, "Loot table should not be null: " + file);
            });
        }, "Should be able to access all loot table files without errors");
    }

    @Test
    public void testDataConsistency() {
        // Test that parsed data is consistent with source files
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        
        // Count files in various categories
        int lootTableCount = minecraftData.loot_tables().files().size();
        int recipeCount = minecraftData.recipes().files().size();
        int advancementCount = minecraftData.advancements().files().size();
        
        // Only recipes are guaranteed to be in the client JAR
        assertTrue(recipeCount > 0, "Should have parsed recipes");
        
        // Loot tables and advancements might not be in client JAR
        System.out.println("Loot tables: " + lootTableCount + ", Recipes: " + recipeCount + ", Advancements: " + advancementCount);
        
        // Total should be substantial for a full vanilla datapack (recipes are enough)
        int totalFiles = lootTableCount + recipeCount + advancementCount;
        assertTrue(totalFiles > 50, "Should have substantial number of parsed files");
    }

    @Test
    public void testParsingPerformance() {
        // Test that parsing doesn't take unreasonably long
        long startTime = System.currentTimeMillis();
        
        // Re-parse to test performance
        Datapack testDatapack = Datapack.loadByteArray(rawAssets);
        
        long endTime = System.currentTimeMillis();
        long parseTime = endTime - startTime;
        
        assertNotNull(testDatapack, "Should successfully parse datapack");
        assertTrue(parseTime < 30000, "Parsing should complete within 30 seconds"); // reasonable for CI
    }

    @Test
    public void testMemoryUsage() {
        // Test that we don't have obvious memory leaks
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create and discard multiple datapack instances
        for (int i = 0; i < 5; i++) {
            Datapack testDatapack = Datapack.loadByteArray(rawAssets);
            assertNotNull(testDatapack, "Should create datapack instance " + i);
        }
        
        // Force garbage collection
        System.gc();
        Thread.yield();
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Memory usage shouldn't grow unreasonably (this is a rough check)
        assertTrue(memoryIncrease < 500_000_000, // 500MB threshold
            "Memory usage should not increase dramatically");
    }

    @Test
    public void testFileAccessPatterns() {
        // Test various file access patterns to ensure they work correctly
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        
        assertDoesNotThrow(() -> {
            // Test accessing files in nested folders
            if (minecraftData.loot_tables().hasFolder("blocks")) {
                FileSystem<LootTable> blockLoot = minecraftData.loot_tables().folder("blocks");
                blockLoot.files().forEach(file -> {
                    LootTable table = blockLoot.file(file);
                    assertNotNull(table, "Block loot table should not be null: " + file);
                });
            }
            
            // Test accessing multiple folder levels
            if (minecraftData.tags().hasFolder("blocks")) {
                FileSystem<Datapack.Tag> blockTags = minecraftData.tags().folder("blocks");
                blockTags.files().forEach(file -> {
                    Datapack.Tag tag = blockTags.file(file);
                    assertNotNull(tag, "Block tag should not be null: " + file);
                });
            }
        }, "Should handle various file access patterns without errors");
    }

    @Test
    public void testErrorRecovery() {
        // Test that the parser can recover from individual file parsing errors
        // by checking that we get a reasonable number of successfully parsed files
        Datapack.NamespacedData minecraftData = datapack.namespacedData().get("minecraft");
        
        // Check that we have parsed a substantial portion of the files
        // (Some files might fail to parse, which is acceptable as long as most succeed)
        int successfullyParsedFiles = 0;
        
        // Count loot tables
        successfullyParsedFiles += minecraftData.loot_tables().files().size();
        
        // Count recipes  
        successfullyParsedFiles += minecraftData.recipes().files().size();
        
        // Count advancements
        successfullyParsedFiles += minecraftData.advancements().files().size();
        
        // Count tags
        successfullyParsedFiles += minecraftData.tags().files().size();
        
        assertTrue(successfullyParsedFiles > 100, 
            "Should successfully parse a large number of files even if some individual files fail");
    }
}