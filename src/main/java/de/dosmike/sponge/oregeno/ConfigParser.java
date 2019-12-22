package de.dosmike.sponge.oregeno;

import com.flowpowered.math.vector.Vector3i;
import de.dosmike.sponge.oregeno.pattern.*;
import de.dosmike.sponge.oregeno.recipe.BlockTypeEx;
import de.dosmike.sponge.oregeno.recipe.ConditionalGrowthRecipe;
import de.dosmike.sponge.oregeno.recipe.GrowthRecipe;
import de.dosmike.sponge.oregeno.recipe.RecipeRegitry;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.World;

import java.io.*;
import java.text.ParseException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConfigParser {
    static void parse(File config) {
        if (!config.exists()) writeDefault(config);

        BufferedReader reader = null;
        String line = null;int lno=0;

        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(config)));

            BlockTypeEx tempType=null;
            double tempProbability = 1.0;
            boolean doShapeless=true;
            int gi = 0;
            LinkedList<Pattern.Builder> patternStack = new LinkedList<>();
            RecipeRegitry.clearRegistry();

            AislePattern.Builder aisleBuilder = null;
            int effect = ConditionalGrowthRecipe.EFFECT_SOUND;
            GrowthRecipe.Consume growthConsumption = GrowthRecipe.Consume.NONE;

            while ((line = reader.readLine())!=null) { lno++;
                if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;
                //get line indent
                int indent=0;
                while (line.charAt(indent)=='\t') indent++;
                line = line.substring(indent).replaceAll("\\s+", " ");
                String[] tokens = line.split(" ");

                if (indent == 0) { // next rule
                    if (tempType != null) { // complete previous rule
                        while (patternStack.size() > 1) {
                            Pattern group = patternStack.pop().build();
                            patternStack.peek().addPattern(group);
                        }
                        if (aisleBuilder != null) { //insert aisle recipe that could not be completed
                            patternStack.peek().addPattern(aisleBuilder.build());
                            aisleBuilder = null;
                        }
                        Pattern pattern = patternStack.isEmpty() ? null : patternStack.pop().build();
                        ConditionalGrowthRecipe recipe = new ConditionalGrowthRecipe(tempType, tempProbability, effect, growthConsumption, pattern);
                        effect = ConditionalGrowthRecipe.EFFECT_SOUND; //reset after usage
                        growthConsumption = GrowthRecipe.Consume.NONE; //reset after usage
                        RecipeRegitry.registerVolatile(recipe);
                    }
                    if (!patternStack.isEmpty())
                        throw new IllegalStateException("Internal state corrupted!");
                    patternStack.push(Pattern.builder(true));
                    gi = 1;

                    // initial GROW block RECIPETYPE PROBABILITY
                    if (tokens.length < 1 || !tokens[0].equalsIgnoreCase("GROW"))
                        throw new ParseException("Keyword GROW expected", 0);
                    if (tokens.length < 2)
                        throw new ParseException("<BLOCKTYPE> expected", line.length() - 1);
                    tempType = BlockTypeEx.fromString(tokens[1]);
                    if (tokens.length < 3 || (!tokens[2].equalsIgnoreCase("SHAPELESS") && !tokens[2].equalsIgnoreCase("AISLE")))
                        throw new ParseException("Keyword SHAPELESS or AISLE expected", tokenIndexAt(tokens, Math.min(2,tokens.length-1)));
                    doShapeless = tokens[2].equalsIgnoreCase("SHAPELESS");
                    if (tokens.length == 4)
                        if (!tokens[3].endsWith("%"))
                            throw new ParseException("Symbol % expected at percentage", tokenIndexAfter(tokens, 3));
                        try {
                            tempProbability = Double.parseDouble(tokens[3].substring(0, tokens[3].length()-1))/100.0;
                            if (tempProbability <= 0) throw new Exception();
                        } catch (Exception e) {
                            throw new ParseException(e.getMessage(), tokenIndexAt(tokens, 3));
                        }
                    if (tokens.length > 4)
                        throw new ParseException("End-of-Line expected", tokenIndexAfter(tokens, 3));
                } else {
                    if (patternStack.isEmpty())
                        throw new ParseException("Keyword GROW expected", 0);

                    //consume filter groups
                    for (; gi > indent; gi--) {
                        Pattern built = patternStack.pop().build();
                        patternStack.peek().addPattern(built);
                    }

                    //handle negations
                    boolean negate=false;
                    int skip = 0;
                    while (tokens[skip].equalsIgnoreCase("NOT")) {
                        skip ++;
                        negate =! negate;
                    }
                    //create value string for sub-parsing
                    String value = line.substring(tokenIndexAfter(tokens, skip)).trim();

                    switch (tokens[skip].toUpperCase()) {
                        //parse filter switches
                        case "AND": {
                            if (tokens.length-skip > 1)
                                throw new ParseException("End-of-Line expected", tokenIndexAfter(tokens, skip));
                            patternStack.push(Pattern.builder(true));
                            gi++;
                            break;
                        }
                        case "OR": {
                            if (tokens.length-skip > 1)
                                throw new ParseException("End-of-Line expected", tokenIndexAfter(tokens, skip));
                            patternStack.push(Pattern.builder(false));
                            gi++;
                            break;
                        }
                        case "YLAYER": {
                            if (gi > 1)
                                throw new ParseException("AILSE pattern must be defined as top level filter", 0);
                            if (doShapeless)
                                throw new ParseException("YLAYER not supported in SHAPELESS recipe", 0);
                            if (tokens.length-skip > 2)
                                throw new ParseException("End-of-Line expected", tokenIndexAfter(tokens, skip+1));
                            int layer;
                            try { layer = Integer.parseInt(tokens[skip+1]); if (layer < -1 || layer > 1) throw new Exception("Invalid <LAYER>: Expected one of -1, 0, 1"); }
                            catch (Exception e) { throw new ParseException(e.getMessage(), tokenIndexAt(tokens, skip+1)); }
                            String[] collect = new String[3];
                            for (int z = 0; z < 3; z++) {
                                String pattern = reader.readLine();
                                if (pattern == null)
                                    throw new IOException("Unexpected End-of-File");
                                if (pattern.charAt(0)!='\t')
                                    throw new IOException("Pattern line "+(z+1)+" not indented!");
                                pattern = pattern.substring(1);
                                if (pattern.length() != 3)
                                    throw new IOException("Pattern line "+(z+1)+" not 3 characters wide");
                                collect[z] = pattern;
                            }
                            if (aisleBuilder == null) aisleBuilder = AislePattern.builder();
                            aisleBuilder.addLayer(String.join("\n", collect));
                            break;
                        }
                        case "MAPPING": {
                            if (gi > 1)
                                throw new ParseException("AILSE pattern must be defined as top level filter", 0);
                            if (doShapeless)
                                throw new ParseException("MAPPING not supported in SHAPELESS recipe", 0);
                            if (tokens.length-skip < 2 || tokens[skip+1].length()!=1)
                                throw new ParseException("<CHAR> expected", tokenIndexAfter(tokens, skip));
                            if (tokens.length-skip < 3)
                                throw new ParseException("<BLOCKTYPE> expected", tokenIndexAfter(tokens, skip+1));
                            if (tokens.length-skip > 3)
                                throw new ParseException("End-of-Line expected", tokenIndexAfter(tokens, skip+2));
                            if (aisleBuilder == null) aisleBuilder = AislePattern.builder();
                            aisleBuilder.mapping( tokens[skip+2].charAt(0), BlockTypeEx.fromString(tokens[skip+3]) );
                            break;
                        }
                        case "FACING": {
                            if (!doShapeless)
                                throw new ParseException("FACING not supported in AISLE recipe", 0);
                            if (tokens.length-skip < 2)
                                throw new ParseException("<DIRECTIONAL> expected", tokenIndexAfter(tokens, skip));
                            patternStack.peek().addPattern(new FacingPattern(value));
                            break;
                        }
                        case "ADJACENT":
                        case "EDGES": {
                            if (!doShapeless)
                                throw new ParseException("ADJACENT not supported in AISLE recipe", 0);
                            if (tokens.length-skip < 2)
                                throw new ParseException("<DIRECTIONAL> expected", tokenIndexAfter(tokens, skip));
                            AdjacentPattern p = new AdjacentPattern(value);
                            p.setIgnoreFacing(tokens[skip].equalsIgnoreCase("EDGES"));
                            patternStack.peek().addPattern(p);
                            break;
                        }
                        case "CENTER": {
                            if (!doShapeless)
                                throw new ParseException("CENTER not supported in AISLE recipe", 0);
                            if (tokens.length-skip < 2)
                                throw new ParseException("<BLOCKTYPE> expected", tokenIndexAfter(tokens, skip));
                            final BlockTypeEx type = BlockTypeEx.fromString(value);
                            patternStack.peek().addPattern((loc)->type.equals(loc.getBlock()));
                            break;
                        }
                        case "IN": {
                            if (tokens.length-skip < 2)
                                throw new ParseException("<WORLD> expected", tokenIndexAfter(tokens, skip));
                            Optional<World> world = Sponge.getServer().getWorld(value);
                            if (!world.isPresent())
                                throw new ParseException("No such world `"+value+"`. Available: "+
                                        Sponge.getServer().getWorlds().stream().map(World::getName).collect(Collectors.joining(", ")),
                                        tokenIndexAt(tokens, skip+1));
                            String adjustedName = world.get().getName();
                            patternStack.peek().addPattern((loc)->loc.getExtent().getName().equals(adjustedName));
                            break;
                        }
                        case "AT": {
                            if (tokens.length-skip < 2)
                                throw new ParseException("<LOCATION> expected", tokenIndexAfter(tokens, skip));
                            long[] block = new long[3]; int i=0;
                            for (String s : value.split(",")) block[i++] = Long.parseLong(s.trim());
                            Vector3i target = new Vector3i(block[0], block[1], block[2]);
                            patternStack.peek().addPattern((loc)->loc.getBlockPosition().equals(target));
                            break;
                        }
                        case "WITHIN": {
                            if (tokens.length-skip < 2)
                                throw new ParseException("<RANGE> expected", tokenIndexAfter(tokens, skip));
                            patternStack.peek().addPattern(new AxisRangePattern(value));
                            break;
                        }
                        case "EFFECT": {
                            if (gi > 1)
                                throw new ParseException("EFFECT must be defined as top level filter", 0);
                            if (tokens.length-skip < 2)
                                throw new ParseException("One of NOTHING, SOUND, EXPLOSION, LIGHTNING expected", tokenIndexAfter(tokens, skip));
                            switch (value.toUpperCase()) {
                                case "SOUND":
                                    effect = ConditionalGrowthRecipe.EFFECT_SOUND;
                                    break;
                                case "EXPLOSION":
                                    effect = ConditionalGrowthRecipe.EFFECT_EXPLOSION;
                                    break;
                                case "LIGHTNING":
                                    effect = ConditionalGrowthRecipe.EFFECT_LIGHTNING;
                                    break;
                                case "NOTHING":
                                    effect = 0;
                                    break;
                                default:
                                    throw new ParseException("One of NOTHING, SOUND, EXPLOSION, LIGHTNING expected", tokenIndexAt(tokens, skip+1));
                            }
                            break;
                        }
                        case "CONSUMING": {
                            if (gi > 1)
                                throw new ParseException("CONSUMING must be defined as top level filter", 0);
                            if (tokens.length-skip < 2)
                                throw new ParseException("One of CENTER, FACING, ADJACENT expected", tokenIndexAfter(tokens, skip));
                            switch (value.toUpperCase()) {
                                case "CENTER":
                                    growthConsumption = GrowthRecipe.Consume.NONE;
                                    break;
                                case "FACING":
                                    growthConsumption = GrowthRecipe.Consume.VOID_FACING;
                                    break;
                                case "ADJACENT":
                                    growthConsumption = GrowthRecipe.Consume.VOID_ALL;
                                    break;
                                default:
                                    throw new ParseException("One of CENTER, FACING, ADJACENT expected", tokenIndexAt(tokens, skip+1));
                            }
                            break;
                        }
                        default:
                            throw new ParseException("Unknown Filter / Setting", 0);
                    }
                }

            }
            //finish rules at end of file that can't be finished by starting a new rule
            if (tempType != null) { // complete previous rule
                while (patternStack.size() > 1) {
                    Pattern group = patternStack.pop().build();
                    patternStack.peek().addPattern(group);
                }
                if (aisleBuilder != null) { //insert aisle recipe that could not be completed
                    patternStack.peek().addPattern(aisleBuilder.build());
                }
                Pattern pattern = patternStack.isEmpty() ? null : patternStack.pop().build();
                ConditionalGrowthRecipe recipe = new ConditionalGrowthRecipe(tempType, tempProbability, effect, growthConsumption, pattern);
                RecipeRegitry.registerVolatile(recipe);
            }
            if (!patternStack.isEmpty())
                throw new IllegalStateException("Internal state corrupted!");

        } catch (Exception exception) {
            OreGeno.w("Parse error at line %d: %s", lno, exception.getMessage());
            if (exception instanceof ParseException) {
                int spaces = ((ParseException) exception).getErrorOffset();
                StringBuilder sb = new StringBuilder();
                String tempSpacer = "          ";
                while (spaces > 10) {
                    sb.append(tempSpacer);
                    spaces-=10;
                }
                if (spaces > 0) {
                    sb.append(tempSpacer, 0, spaces);
                }
                sb.append("^");
                OreGeno.w("> %s", line);
                OreGeno.w("@ %s", sb.toString());
            }
            RecipeRegitry.clearRegistry();
//            exception.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (Exception ignore) {
            }
        }
    }

    private static int tokenIndexAt(String[] tokens, int index) {
        if (index >= tokens.length) { System.err.println("index exceeded array size @ tokenIndexAt, setting to array length"); index = tokens.length-1; }
        int i = index;
        for (int j = 0; j < index; j++)
            i+=tokens[j].length();
        return i;
    }
    private static int tokenIndexAfter(String[] tokens, int index) {
        if (index >= tokens.length) { System.err.println("index exceeded array size @ tokenIndexAt, setting to array length"); index = tokens.length-1; }
        int i = index;
        for (int j = 0; j <= index; j++)
            i+=tokens[j].length();
        return i;
    }

    static void writeDefault(File config) {
        config.getParentFile().mkdirs();

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(config)));

            writer.write("# Please check the Ore-Page for format specs");
            writer.newLine();
            writer.write("GROW minecraft:coal_ore SHAPELESS 0.25%\n");
            writer.write("\tCENTER minecraft:stone\n");
            writer.write("\tFACING minecraft:coal_ore\n");
            writer.write("\tADJACENT minecraft:coal_ore < 3\n");
            writer.write("\tIN world\n");
            writer.write("\tCONSUMING CENTER\n");
            writer.newLine();
            writer.write("GROW minecraft:iron_ore SHAPELESS 0.15%\n");
            writer.write("\tCENTER minecraft:stone\n");
            writer.write("\tFACING minecraft:iron_ore\n");
            writer.write("\tADJACENT minecraft:iron_ore < 3\n");
            writer.write("\tIN world\n");
            writer.write("\tCONSUMING CENTER\n");
            writer.newLine();
            writer.write("GROW minecraft:gold_ore SHAPELESS 0.15%\n");
            writer.write("\tCENTER minecraft:stone\n");
            writer.write("\tFACING minecraft:gold_ore\n");
            writer.write("\tADJACENT minecraft:gold_ore < 3\n");
            writer.write("\tIN world\n");
            writer.write("\tCONSUMING CENTER\n");
            writer.newLine();
            writer.write("GROW minecraft:redstone_ore SHAPELESS 0.20%\n");
            writer.write("\tCENTER minecraft:stone\n");
            writer.write("\tFACING minecraft:redstone_ore\n");
            writer.write("\tADJACENT minecraft:redstone_ore < 3\n");
            writer.write("\tIN world\n");
            writer.write("\tCONSUMING CENTER\n");
            writer.newLine();
            writer.write("GROW minecraft:lapis_ore SHAPELESS 0.15%\n");
            writer.write("\tCENTER minecraft:stone\n");
            writer.write("\tFACING minecraft:lapis_ore\n");
            writer.write("\tADJACENT minecraft:lapis_ore < 3\n");
            writer.write("\tIN world\n");
            writer.write("\tCONSUMING CENTER\n");
            writer.newLine();
            writer.write("GROW minecraft:diamond_ore SHAPELESS 0.10%\n");
            writer.write("\tCENTER minecraft:stone\n");
            writer.write("\tFACING minecraft:diamond_ore\n");
            writer.write("\tADJACENT minecraft:diamond_ore < 3\n");
            writer.write("\tIN world\n");
            writer.write("\tCONSUMING CENTER\n");
            writer.newLine();
            writer.write("GROW minecraft:emerald_ore SHAPELESS 0.10%\n");
            writer.write("\tCENTER minecraft:stone\n");
            writer.write("\tFACING minecraft:emerald_ore\n");
            writer.write("\tADJACENT minecraft:emerald_ore < 3\n");
            writer.write("\tIN world\n");
            writer.write("\tCONSUMING CENTER\n");
            writer.newLine();
            writer.write("GROW minecraft:nether_quartz_ore SHAPELESS 0.25%\n");
            writer.write("\tCENTER minecraft:netherrack\n");
            writer.write("\tFACING minecraft:nether_quartz_ore\n");
            writer.write("\tADJACENT minecraft:nether_quartz_ore < 3\n");
            writer.write("\tIN DIM-1\n");
            writer.write("\tCONSUMING CENTER\n");

        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            try {
                writer.flush();
                writer.close();
            } catch (Exception e) {
                /**/
            }
        }
    }
}
