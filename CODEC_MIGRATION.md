# Minecraft Codec Migration

This document explains the migration from legacy JSON parsing to Minecraft codec-based parsing in the VanillaReimplementation project.

## Overview

The datapack loading system has been enhanced with codec-based parsing that works alongside the existing Moshi-based JSON parsing. This provides better performance, consistency with Minecraft's internal systems, and improved type safety.

## What Changed

### New Components

1. **`DatapackCodecs.java`** - Contains codec implementations for all major datapack parsing types
2. **`DatapackLoader.codec()`** - New method to integrate codecs with existing infrastructure
3. **Comprehensive tests** - Full test suite validating codec functionality

### Legacy Compatibility

- All existing Moshi-based parsing continues to work unchanged
- No breaking changes to existing APIs
- Legacy methods remain available for backward compatibility

## Codec Implementations

### Material Parsing
```java
// Old way (still works)
Material material = DatapackLoader.moshi(Material.class).apply(reader);

// New codec way
Material material = DatapackLoader.codec(DatapackCodecs.MATERIAL_CODEC).apply(reader);
```

Features:
- Automatic legacy material mapping (e.g., "scute" → TURTLE_SCUTE)
- Better error handling
- Type-safe operations

### Key Parsing
```java
// Handles both regular keys and tag references
Key regularKey = codec.parse("\"minecraft:stone\"");      // → minecraft:stone
Key tagKey = codec.parse("\"#minecraft:logs\"");          // → minecraft:logs (# stripped)
```

### NBT Compound Parsing
```java
// Direct JSON string to CompoundBinaryTag conversion
CompoundBinaryTag nbt = codec.parse("\"{\\\"test\\\":\\\"value\\\"}\"");
```

### Range and Collections
```java
// Float ranges support both single values and arrays
Range.Float singleRange = codec.parse("5.5");           // → Range(5.5, 5.5)
Range.Float arrayRange = codec.parse("[1.0, 10.0]");    // → Range(1.0, 10.0)

// DoubleList with full validation
DoubleList list = codec.parse("[1.0, 2.5, 3.14]");
```

## Benefits

1. **Performance**: Codecs are more efficient than reflection-based Moshi parsing
2. **Type Safety**: Compile-time type checking and better error messages  
3. **Consistency**: Uses the same codec system as Minecraft internally
4. **Extensibility**: Easy to add new codec-based parsers
5. **Maintainability**: Cleaner, more focused code

## Migration Guide

### For New Code
Use codec-based parsing for new implementations:
```java
// Recommended for new code
var parser = DatapackLoader.codec(DatapackCodecs.MATERIAL_CODEC);
Material result = parser.apply(jsonReader);
```

### For Existing Code
No immediate changes required. Legacy parsing continues to work:
```java
// Existing code continues to work unchanged
Material result = DatapackLoader.moshi(Material.class).apply(jsonReader);
```

### Gradual Migration
You can migrate existing code gradually by replacing Moshi calls with codec calls:
```java
// Before
var parser = DatapackLoader.moshi(Material.class);

// After  
var parser = DatapackLoader.codec(DatapackCodecs.MATERIAL_CODEC);
```

## Testing

The implementation includes comprehensive tests covering:
- Individual codec functionality
- Integration with existing DatapackLoader infrastructure
- Error handling and edge cases
- Legacy compatibility verification

Run tests with:
```bash
./gradlew :datapack-tests:test --tests "DatapackCodecsTest*"
```

## Future Considerations

1. **Performance Monitoring**: Track performance improvements from codec adoption
2. **Coverage Expansion**: Add codecs for additional types as needed
3. **Legacy Deprecation**: Eventually deprecate Moshi-based parsing (not in this change)
4. **Documentation**: Update datapack parsing documentation to recommend codecs

## Summary

This implementation successfully converts legacy JSON parsing to use Minecraft codecs while maintaining full backward compatibility. The new system is more efficient, type-safe, and consistent with Minecraft's internal architecture, while requiring no immediate changes to existing code.