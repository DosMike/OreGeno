package de.dosmike.sponge.oregeno.recipe;

import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.extent.Extent;

import java.util.LinkedList;
import java.util.List;

public class RecipeRegitry {

    private static List<GrowthRecipe> recipeList1 = new LinkedList<>();
    private static List<GrowthRecipe> recipeList2 = new LinkedList<>();

    private static final Object genTickMutex = new Object();

    public static List<GrowthRecipe> getRecipesForLocation(Location<World> location) {
        synchronized (genTickMutex) {
            List<GrowthRecipe> result = new LinkedList<>();
            for (GrowthRecipe recipe : recipeList1) {
                if (!location.getExtent().isLoaded() ||
                        //Check if chunk is loaded
                    !location.getExtent().getChunk(location.getChunkPosition()).map(Extent::isLoaded).orElse(false))
                    throw new IllegalStateException("World or chunk unloaded while testing location");
                if (recipe.isRecipeValidAt(location))
                    result.add(recipe);
            }
            for (GrowthRecipe recipe : recipeList2) {
                if (!location.getExtent().isLoaded() ||
                        //Check if chunk is loaded
                    !location.getExtent().getChunk(location.getChunkPosition()).map(Extent::isLoaded).orElse(false))
                    throw new IllegalStateException("World or chunk unloaded while testing location");
                if (recipe.isRecipeValidAt(location))
                    result.add(recipe);
            }
            return result;
        }
    }

    /** register the recipe until the plugin reloads */
    public static void registerVolatile(GrowthRecipe recipe) {
        synchronized (genTickMutex) {
            recipeList1.add(recipe);
        }
    }
    /** clear volatile registry, internal function DO NOT CALL */
    public static void clearRegistry() {
        synchronized (genTickMutex) {
            recipeList1.clear();
        }
    }
    public static void register(GrowthRecipe recipe) {
        synchronized (genTickMutex) {
            recipeList2.add(recipe);
        }
    }
    public static void unregister(GrowthRecipe recipe) {
        synchronized (genTickMutex) {
            recipeList2.remove(recipe);
        }
    }

    public static int count() {
        return recipeList1.size()+recipeList2.size();
    }
}
