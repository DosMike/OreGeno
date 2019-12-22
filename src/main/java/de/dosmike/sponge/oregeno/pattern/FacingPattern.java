package de.dosmike.sponge.oregeno.pattern;

import de.dosmike.sponge.oregeno.recipe.BlockTypeEx;
import de.dosmike.sponge.oregeno.util.AmountComparator;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** direct contact pattern */
public class FacingPattern extends DirectionalPattern {
    public FacingPattern(BlockTypeEx type, int amount, AmountComparator comparator, Direction... direction) {
        super(type, amount, comparator, direction);
    }

    public FacingPattern(String string) {
        super(string);
    }

    @Override
    public boolean matches(Location<World> location) {
        //collect block count
        int amount = 0;
        Set<Direction> directions = new HashSet<>(Arrays.asList(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
        if (directionAsBlacklist) directions.removeAll(this.direction);
        else directions.retainAll(this.direction);

        for (Direction dir : directions)
            if (checkBlockTypeAndMeta(location.getRelative(dir).getBlock()))
                amount ++;

        return this.comparator.compares(amount, this.amount);
    }
}
