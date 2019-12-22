package de.dosmike.sponge.oregeno.recipe;

import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;

import static de.dosmike.sponge.oregeno.recipe.GrowthRecipe.Consume.*;

public interface GrowthRecipe {

    enum Consume { NONE, VOID_FACING, VOID_ALL };

    /** This method checks whether various patterns and conditions match the specified location.
     * @param location the location to check
     * @return true if all conditions are met, meaning the result could grow
     */
    boolean isRecipeValidAt(Location<World> location);

    /** @return a double in the range [0.0, 1.0] where 1.0 is a 100% probability for
     * the block to change the next time the location is ticked. */
    double getGrowthProbablility();

    /**
     * @return the amount of ticks this recipe takes to process, or empty if random.
     * if a recipe is time-based growth probability will be ignored by default
     */
    default Optional<Integer> getGrowthTime() {
        return Optional.empty();
    }

    default Consume growthConsumes() {
        return NONE;
    }

    /** @return the block type that this growth recipe results in
     */
    BlockTypeEx getGrowthResult();

    /** Default implementation does not check whether a location is valid. This is usually
     * done prior to this call!
     * If so, using the supplied random number generator it will dice if this recipe should
     * grow this tick or not.
     * @param generator the rng to use
     * @param location the location to pass to isRecipeValidAt
     * @return whether the block grew or not
     */
    default boolean tryGrowth(Random generator, Location<World> location) {
        if (generator.nextDouble() < getGrowthProbablility()) {
            if (growthConsumes() == VOID_ALL) {
                for (int z = -1; z <= 1; z++)
                for (int x = -1; x <= 1; x++)
                for (int y = -1; y <= 1; y++)
                    if (x != 0 && y != 0 && z != 0)
                    location.getExtent().setBlockType(
                            location.getBlockPosition().clone().add(x,y,z),
                            BlockTypes.AIR
                    );
            } else if (growthConsumes() == VOID_FACING) {
                new HashSet<>(Arrays.asList(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST))
                    .forEach(d->location.getBlockRelative(d).setBlockType(BlockTypes.AIR));
            }

            getGrowthResult().placeAt(location);
            return true;
        }
        return false;
    }

}
