package de.dosmike.sponge.oregeno.pattern;

import com.flowpowered.math.vector.Vector3i;
import de.dosmike.sponge.oregeno.recipe.BlockTypeEx;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class AislePattern implements Pattern {

    /** three layers of aisle recipes for in-world matching, location mapped by vectors.
     * value will be a predicate<BlockState> */
    Map<Vector3i, Predicate<BlockState>> inputRequirement = new HashMap<>();

    /** these recipes work pretty much like the forge shaped recipe "param"-list.
     * Only difference is, a that pattern with a dimension of less than 1 defaults to top,north,west.
     * Don't worry, these recipes can be crafted in rotation 0°, 90° 180° and 270°.<br>
     * the first param is expected to be a row or layer (layer if linebreak included)<br>
     * each letter in the pattern stands for one blocktype. these can be defined by pairs of
     * characters and blockTypeEx following in that order.<br>
     * e.g. "###\n###\n###", '#', new BlockTypeEx(BlockTypes.Stone) means the entire top layer has to be stone.<br>
     * instead of blockTypeEx you can also use predicated that'll receive BlockStates for that
     * relative location.<br>
     * up to 3 layers of max 3x3 blocks can be set. characters that are not specified will be interpreted as
     * "any block here". layers traverse top to bottom
     * @throws IllegalArgumentException if a character -> type definition is broken
     * @throws IndexOutOfBoundsException if to many layers/rows are specified
     * @throws IllegalStateException if a character gets mapped twice */
    public AislePattern(Object... params) throws IllegalArgumentException, IndexOutOfBoundsException {
        Map<Vector3i, Character> relativeCharacters = new HashMap<>();
        Map<Character, Predicate<BlockState>> meaningOfLife = new HashMap<>();

        int layer=0, row=0;
        Character mark=null;
        for (Object param : params) {
            if (param instanceof BlockTypeEx) {
                BlockTypeEx type = (BlockTypeEx)param;
                if (mark == null) throw new IllegalArgumentException("Defining BlockTypeEx "+param+" before character!");
                if (meaningOfLife.containsKey(mark)) throw new IllegalStateException("Character "+mark+"already defined");
                meaningOfLife.put(mark, bs->new BlockTypeEx(bs).equals(type));
                mark = null;
            } else if (param instanceof Predicate) {
                Predicate<BlockState> pred;
                if (mark == null) throw new IllegalArgumentException("Defining BlockTypeEx "+param+" before character!");
                if (meaningOfLife.containsKey(mark)) throw new IllegalStateException("Character "+mark+"already defined");
                try {
                    pred = (Predicate<BlockState>)param;
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException("Predicate not typing BlockState");
                }
                meaningOfLife.put(mark, pred);
                mark = null;
            } else if (param instanceof Character) {
                if (mark != null) throw new IllegalArgumentException("Skipped definition for character "+mark);
                Character c = (Character) param;
                if (meaningOfLife.containsKey(c)) throw new IllegalStateException("Character "+c+"already defined");
                mark = c;
            } else if (param instanceof String) {
                String[] rows = ((String) param).split("\n");
                for (String prow : rows) {
                    if (row >= 3) { //this block is on top to only trigger if rows.length() > 3
                        layer++;
                        row=0;
                        if (layer >= 3) throw new IndexOutOfBoundsException("Too many layers specified");
                    }

                    if (prow.length()>3) throw new IndexOutOfBoundsException("Row exceeded length!");
                    for (int i=0;i<prow.length();i++) {
                        Vector3i rel = new Vector3i(i-1, -(layer-1), -(row-1));
                        relativeCharacters.put(rel, prow.charAt(i));
                    }

                    row++;
                }
            } else throw new IllegalArgumentException("Unknown parameter "+param);
        }

        for (Map.Entry<Vector3i, Character> entry : relativeCharacters.entrySet()) {
            if (meaningOfLife.containsKey(entry.getValue())) {
                inputRequirement.put(entry.getKey(), meaningOfLife.get(entry.getValue()));
            }
        }
    }

    @Override
    public boolean matches(Location<World> location) {
        rot: for (int r = 0; r<360; r+=90) {
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    for (int x = -1; x <= 1; x++) {
                        Vector3i vec = new Vector3i(x, y, z), rel;
                        Predicate<BlockState> pred = inputRequirement.get(vec);
                        if (pred == null) continue;

                        if (r == 90) rel = new Vector3i(z, y, -x);
                        else if (r == 180) rel = new Vector3i(-x, y, -z);
                        else if (r == 270) rel = new Vector3i(-z, y, x);
                        else rel = vec.clone();
                        BlockState relative = location.getExtent().getBlock(location.getBlockPosition().clone().add(rel));

                        if (!pred.test(relative)) //invalid block?
                            continue rot; //try to rotate recipe
                    }
            return true; //all blocks for this rotation matched
        }
        return false; //no rotation worked
    }

    //Region builder
    public static class Builder {
        List<String> layers = new LinkedList<>();
        Map<Character, BlockTypeEx> mapping = new HashMap<>();
        private Builder() {

        }
        public Builder addLayer(String xzPattern) {
            layers.add(xzPattern);
            return this;
        }
        public Builder mapping(char symbol, BlockTypeEx blockType) {
            mapping.put(symbol, blockType);
            return this;
        }
        public AislePattern build() {
            mapping.remove(' ');
            if (layers.isEmpty())
                throw new IllegalStateException("No AISLE layers specified!");
            if (mapping.isEmpty())
                throw new IllegalStateException("No AISLE mappings specified!");
            while (layers.size() < 2) layers.add(0, "   \n   \n   ");
            while (layers.size() < 3) layers.add("   \n   \n   ");
            List<Object> objects = new LinkedList<>();
            for (int i = 2; i >= 0; i--) objects.add(layers.get(i));
            for (Map.Entry<Character, BlockTypeEx> map : mapping.entrySet()) {
                objects.add(map.getKey(), map.getValue());
            }
            return new AislePattern(objects.toArray(new Object[0]));
        }
    }

    public static Builder builder() {
        return new Builder();
    }
    //endregion

}
