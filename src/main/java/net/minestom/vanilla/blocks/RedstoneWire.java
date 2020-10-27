package net.minestom.vanilla.blocks;

import net.minestom.server.data.Data;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockAlternative;
import net.minestom.server.instance.block.CustomBlock;
import net.minestom.server.item.Material;
import net.minestom.server.utils.BlockPosition;
import net.minestom.server.utils.Direction;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.Position;
import net.minestom.server.utils.Vector;
import net.minestom.vanilla.entity.PrimedTNT;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Redstone Wire:
 * 
 * This is the entrypoint for all redstone wire.
 */

public class RedstoneWire extends VanillaBlock {

	public RedstoneWire() {
        super(Block.REDSTONE_WIRE);
    }

    @Override
    protected BlockPropertyList createPropertyValues() {
        return new BlockPropertyList()
        		.property("east", "none", "side", "up")
        		.property("north", "none", "side", "up")
        		.property("power", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15")
        		.property("south", "none", "side", "up")
        		.property("west", "none", "side", "up");
    }
    
    @Override
    public short getVisualBlockForPlacement(Player player, Player.Hand hand, BlockPosition blockPosition) {
    	// Update surroundings
    	updateSurroundingRedstone(player.getInstance(), blockPosition);
    	
    	return getNewBlockState(player.getInstance(), blockPosition);
    }
    
    
    
    @Override
    public void update(Instance instance, BlockPosition blockPosition, Data data) {
    	// If redstone component
    	if (Block.fromStateId(instance.getBlockStateId(blockPosition)).equals(Block.REDSTONE_WIRE)) {
			// Compare previous redstone signal with new calculated signal
			short NewBlockState = getNewBlockState(instance, blockPosition);
			if (NewBlockState != instance.getBlockStateId(blockPosition)) {
				// Set new signal and update surrounding blocks
				instance.setBlockStateId(blockPosition, NewBlockState);
				updateSurroundingRedstone(instance, blockPosition);
			}
    	}
    }
    
    private void updateSurroundingRedstone(Instance instance, BlockPosition blockPosition) {
		// get XYZ components
    	int X = blockPosition.getX();
    	int Y = blockPosition.getY();
    	int Z = blockPosition.getZ();
    	
    	// Create new data
    	Data data = Data.EMPTY;
    	data.set("redstoneUpdate", true, Boolean.class);
    	for (int x = -1; x < 2; x++) {
    		for (int y = -1; y < 2; y++) {
    			for (int z = -1; z < 2; z++) {
    				if (!(x == 0 && y == 0 && z == 0)) {
    					update(instance, new BlockPosition(X + x, Y + y, Z + z), data);
    				}
    			}
    		}
    	}
	}

	/**
     * 
     * @param instance
     * @param blockPosition
     * @return 
     */
    
    private Boolean[][] getConnections(Instance instance, BlockPosition blockPosition) {
    	
    	// TODO: Structuring needs work here
    	
    	int X = blockPosition.getX();
    	int Y = blockPosition.getY();
    	int Z = blockPosition.getZ();
    	Boolean[][] Connections = {
    			{false, false, false},	// North - Z
    			{false, false, false},	// East + X
    			{false, false, false},	// South + Z
    			{false, false, false}	// West - X
    		};
    	Connections[0][0] = (Block.fromStateId(instance.getBlockStateId(X, Y + 1, Z - 1)) == Block.REDSTONE_WIRE);	// North-Up
    	Connections[0][1] = (Block.fromStateId(instance.getBlockStateId(X, Y, Z - 1)) == Block.REDSTONE_WIRE);		// North-Flat
    	Connections[0][2] = (Block.fromStateId(instance.getBlockStateId(X, Y - 1, Z - 1)) == Block.REDSTONE_WIRE);	// North-Down
    	Connections[1][0] = (Block.fromStateId(instance.getBlockStateId(X + 1, Y + 1, Z)) == Block.REDSTONE_WIRE);	// East-Up
    	Connections[1][1] = (Block.fromStateId(instance.getBlockStateId(X + 1, Y, Z)) == Block.REDSTONE_WIRE);		// East-Flat
    	Connections[1][2] = (Block.fromStateId(instance.getBlockStateId(X + 1, Y - 1, Z)) == Block.REDSTONE_WIRE);	// East-Down
    	Connections[2][0] = (Block.fromStateId(instance.getBlockStateId(X, Y + 1, Z + 1)) == Block.REDSTONE_WIRE);	// South-Up
    	Connections[2][1] = (Block.fromStateId(instance.getBlockStateId(X, Y, Z + 1)) == Block.REDSTONE_WIRE);		// South-Flat
    	Connections[2][2] = (Block.fromStateId(instance.getBlockStateId(X, Y - 1, Z + 1)) == Block.REDSTONE_WIRE);	// South-Down
    	Connections[3][0] = (Block.fromStateId(instance.getBlockStateId(X - 1, Y + 1, Z)) == Block.REDSTONE_WIRE);	// West-Up
    	Connections[3][1] = (Block.fromStateId(instance.getBlockStateId(X - 1, Y, Z)) == Block.REDSTONE_WIRE);		// West-Flat
    	Connections[3][2] = (Block.fromStateId(instance.getBlockStateId(X - 1, Y - 1, Z)) == Block.REDSTONE_WIRE);	// West-Down
    	
    	return Connections;
    }
    
private int getSurroundingPower(Instance instance, BlockPosition blockPosition) {
    	
    	// TODO: Structuring needs work here
    	
		int highestPower = 0;
		
		
		
    	int X = blockPosition.getX();
    	int Y = blockPosition.getY();
    	int Z = blockPosition.getZ();
    	
    	
    	int[][] Coms = { // Combinations
    			{-1, -1, 0},
    			{1, -1, 0},
    			{-1, 0, 0},
    			{1, 0, 0},
    			{-1, 1, 0},
    			{1, 1, 0},
    			{0, -1, -1},
    			{0, -1, 1},
    			{0, 0, -1},
    			{0, 0, 1},
    			{0, 1, -1},
    			{0, 1, 1},
    	};
    	
    	
    	Iteration:
    	for (int i = 0; i < Coms.length; i++) {
    		
    		int NewX = X + Coms[i][0];
    		int NewY = Y + Coms[i][1];
    		int NewZ = Z + Coms[i][2];
    		
	    	int powerLevel = 0;
	    	
	    	Block TargetBlock = Block.fromStateId(instance.getBlockStateId(NewX, NewY, NewZ));
	    	
	    	switch (TargetBlock) {
	    	case REDSTONE_BLOCK:
	    		highestPower = 15;
	    	
	    	case REDSTONE_TORCH:
	    		highestPower = 15;
	    	
	    	case REDSTONE_WIRE:
	    		try {
		        	BlockState blockState = VanillaBlock.getBlockState(instance, new BlockPosition(NewX, NewY, NewZ));
		        	
		        	String powerString = blockState.get("power");
		        	
		        	powerLevel = Integer.valueOf(powerString);
		        	
		    	} catch (Exception e) {
		    	}
	    		
				if (powerLevel > highestPower) {
					highestPower = powerLevel;
				}
			
	    	default:
				break;
	    	}
	    	
	    	
	    	if (highestPower == 15) {
	    		break Iteration;
	    	}
	    	
    	}
    	return highestPower;
    }

	private short getNewBlockState(Instance instance, BlockPosition blockPosition) {
		int Power = 0;
		int SurroundingPower = getSurroundingPower(instance, blockPosition);
		if (SurroundingPower > 0) {
			Power = SurroundingPower - 1;
		} else {
			Power = 0;
		}
    	String North = "none";
    	String East = "none";
    	String South = "none";
    	String West = "none";
    	Boolean[][] Cons = getConnections(instance, blockPosition);
    	if (Cons[0][0]) { // If Up-North
    		North = "up";
    	} else if (Cons[0][1] || (Cons[0][2])) { // If Flat-North or Down-North
    		North = "side";
    	}
    	if (Cons[1][0]) { // If Up-East
    		East = "up";
    	} else if (Cons[1][1] || (Cons[1][2])) { // If Flat-East or Down-East
    		East = "side";
    	}
    	if (Cons[2][0]) { // If Up-South
    		South = "up";
    	} else if (Cons[2][1] || (Cons[2][2])) { // If Flat-South or Down-South
    		South = "side";
    	}
    	if (Cons[3][0]) { // If Up-West
    		West = "up";
    	} else if (Cons[3][1] || (Cons[3][2])) { // If Flat-West or Down-West
    		West = "side";
    	}
    	return getBaseBlockState()
    			.with("east", East)
    			.with("north", North)
    			.with("power", Integer.toString(Power))
    			.with("south", South)
    			.with("west", West)
                .getBlockId();
	}
}