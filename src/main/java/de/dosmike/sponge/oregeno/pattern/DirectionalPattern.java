package de.dosmike.sponge.oregeno.pattern;

import de.dosmike.sponge.oregeno.recipe.BlockTypeEx;
import de.dosmike.sponge.oregeno.util.AmountComparator;
import de.dosmike.sponge.oregeno.util.StringUtils;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;

public abstract class DirectionalPattern implements Pattern {

    protected int amount;
    protected BlockTypeEx type;
    protected AmountComparator comparator;
    protected List<Direction> direction = new LinkedList<>();
    protected boolean directionAsBlacklist;

    /**
     * @param direction Optional
     */
    public DirectionalPattern(BlockTypeEx type, int amount, AmountComparator comparator, Direction... direction) {
        this.type = type;
        this.amount = amount;
        this.comparator = comparator;
        for (Direction dir : direction) this.direction.add(dir);
        this.directionAsBlacklist = false;
    }

    /**
     *  Accepts a Pattern like minecraft:blocktype@meta &gt;= 9 ~(up)<br>
     *  <code>minecraft:blocktype</code> is mandatory, mod prefix isn't<br>
     *  <code>@meta</code> is a optional block meta int, that's necessary until 1.13<br>
     *  <code>&gt;= 9</code> is a amount comparison, optional<br>
     *  <code>~(up)</code> is the list of directions, ~ denotes blacklist<br>
     *      Directions are UP, DOWN, NORTH, EAST, SOUTH, WEST
     */
    public DirectionalPattern(String string) {
        String[] tmp = StringUtils.consume("^\\w+(?:\\:\\w+)?", string.replaceAll("\\s", ""));
        if (tmp.length != 2) throw new IllegalArgumentException("Pattern is malformed: Missing BlockType");
        BlockType blockType = Sponge.getRegistry().getType(BlockType.class, tmp[1]).orElseThrow(()->new IllegalArgumentException("Pattern is malformed: Invalid BlockType"));

        tmp = StringUtils.consume("^@[0-9]+", tmp[0]);
        if (tmp.length == 2) {
            type = new BlockTypeEx(blockType, Integer.parseInt(tmp[1].substring(1)));
        } else type = new BlockTypeEx(blockType);

        tmp = StringUtils.consume("^[<>!=]+", tmp[0]);
        if (tmp.length == 2) {
            switch (tmp[1]) {
                case "=":
                case "==":
                    comparator = AmountComparator.EXACT;
                    break;
                case "<":
                    comparator = AmountComparator.LESS;
                    break;
                case "<=":
                    comparator = AmountComparator.LESSEQUAL;
                    break;
                case ">":
                    comparator = AmountComparator.GREATER;
                    break;
                case ">=":
                    comparator = AmountComparator.GREATEREQUAL;
                    break;
                case "<>":
                case "!=":
                    comparator = AmountComparator.NOT;
                    break;
                default:
                    throw new IllegalArgumentException("Pattern is malformed: Unknown value comparator "+tmp[1]);
            }
            tmp = StringUtils.consume("^[0-9]+", tmp[0]);
            if (tmp.length != 2) throw new IllegalArgumentException("Pattern is malformed: Missing amount for comparator");
            amount = Integer.valueOf(tmp[1]);
        } else {
            amount = 1;
            comparator = AmountComparator.GREATEREQUAL;
        }
        if (tmp[0].length() > 0) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("^(~)?\\((\\w+(?:,\\w+)*)\\)$");
            Matcher m = p.matcher(tmp[0]);
            if (m.matches()) {
                directionAsBlacklist = (m.group(1) != null);
                tmp = m.group(2).split(",");
                for (String d : tmp) for (Direction dir : Direction.values())
                    if (dir.toString().equalsIgnoreCase(d)) {
                        if (dir.isCardinal() || dir.isUpright()) direction.add(dir);
                        else throw new IllegalArgumentException("Pattern is malformed: Only cardinal and upright directions are supported");
                    }
            } else throw new IllegalArgumentException("Pattern is malformed: Direction list expected");
        } else {
            directionAsBlacklist = true;
        }
    }

    protected boolean checkBlockTypeAndMeta(BlockState state) {
        return type.equals(state);
    }
    public abstract boolean matches(Location<World> location);
}
