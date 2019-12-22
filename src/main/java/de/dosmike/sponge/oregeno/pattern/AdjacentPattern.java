package de.dosmike.sponge.oregeno.pattern;

import com.flowpowered.math.vector.Vector3i;
import de.dosmike.sponge.oregeno.recipe.BlockTypeEx;
import de.dosmike.sponge.oregeno.util.AmountComparator;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** adjacent 3x3 pattern in a direction */
public class AdjacentPattern extends DirectionalPattern {

    private boolean includeFacing = false;

    public AdjacentPattern(BlockTypeEx type, int amount, AmountComparator comparator, Direction... direction) {
        super(type, amount, comparator, direction);
    }

    public AdjacentPattern(String string) {
        super(string);
    }

    public void setIgnoreFacing(boolean ignoreFacing) {
        this.includeFacing =! ignoreFacing;
    }

    @Override
    public boolean matches(Location<World> location) {
        //collect block count
        Set<Vector3i> matches = new HashSet<>();
        Set<Direction> directions = new HashSet<>(Arrays.asList(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
        if (directionAsBlacklist) directions.removeAll(this.direction);
        else directions.retainAll(this.direction);

        for (Direction dir : directions) {
            Location<World> directRelative = location.getBlockRelative(dir);
            if (includeFacing && checkBlockTypeAndMeta(directRelative.getBlock()))
                matches.add(dir.asBlockOffset().clone());
            for (Direction d : cordinalUprightAround(dir)) {
                Location<World> degenerate = directRelative.getBlockRelative(d);
                if (checkBlockTypeAndMeta(degenerate.getBlock()))
                    //clone dir because add is a mutating operation, even tho it returns
                    matches.add(dir.asBlockOffset().clone().add(d.asBlockOffset()));
            }
        }

        return this.comparator.compares(matches.size(), this.amount);
    }

    private Set<Direction> cordinalUprightAround(Direction axis) {
        Set<Direction> directions = new HashSet<>(Arrays.asList(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
        directions.removeIf(dir->dir.equals(axis)||dir.isOpposite(axis));
        return directions;
    }
}
