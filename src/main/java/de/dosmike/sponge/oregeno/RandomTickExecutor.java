package de.dosmike.sponge.oregeno;

import com.flowpowered.math.vector.Vector3i;
import de.dosmike.sponge.oregeno.recipe.GrowthRecipe;
import de.dosmike.sponge.oregeno.recipe.RecipeRegitry;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class RandomTickExecutor implements Runnable {

    @Override
    public void run() {
        long t0 = System.nanoTime();
        Collection<World> worlds = Sponge.getServer().getWorlds();
        try {
            for (World w : worlds)
                if (w.isLoaded())
                    tickWorld(w);
        } catch (NullPointerException ignore) {
            // seems like this can happen with the internal iterator when stopping
        }
        long dt = System.nanoTime()-t0;
        PerformanceReport.pushNSPT(dt);
    }

    private static Random rng = new Random(System.currentTimeMillis());

    private static void tickWorld(World loaded) {
        Iterable<Chunk> chunks = loaded.getLoadedChunks();
        for (Chunk c : chunks)
            if (c.isLoaded())
                tickChunk(loaded, c, OreGeno.getTickSpeed());
    }

    private static void tickChunk(World world, Chunk chunk, int tickSpeed) {
        //chunk centers have to be 128 blocks with a player
        if (OreGeno.getPlayerLocationCache(world).stream().noneMatch(loc->{
            double dx = loc.getX() - (chunk.getBlockMin().getX()+7.5);
            double dz = loc.getZ() - (chunk.getBlockMin().getZ()+7.5);
            return dx*dx+dz*dz <= 128.0*128.0; //distance squared
        })) return;
        //check if chunk is cached
        ChunkCandidateCache cache = ChunkCandidateCache.getWorldCache(world);
        if (cache != null && cache.isChunkCached(chunk)) {
            Set<Vector3i> candidates = cache.getCandidates(chunk);
            for (int i = 0; i < world.getBlockMax().getY() / 16; i++)
                tickCachedSection(world, chunk, candidates, 16 * i, tickSpeed);
        } else {
            ChunkCandidateCache.cacheLater(chunk);
            for (int i = 0; i < world.getBlockMax().getY() / 16; i++)
                tickSection(world, chunk, 16 * i, tickSpeed);
        }
    }

    private static void tickSection(World world, Chunk chunk, int section, int tickSpeed) {
        Vector3i chunkPos = chunk.getBlockMin();
        Set<Vector3i> preventDoubles = new HashSet<>();
        for(int i=0; i < tickSpeed; i++) {
            Vector3i block = new Vector3i(
                    chunkPos.getX() + rng.nextInt(16),
                    section + rng.nextInt(16),
                    chunkPos.getZ() + rng.nextInt(16));
            if (preventDoubles.add(block))
                tickLocation(new Location<>(world, block));
        }
    }
    private static void tickCachedSection(World world, Chunk chunk, Set<Vector3i> cache, int section, int tickSpeed) {
        Vector3i chunkPos = chunk.getBlockMin();
        Set<Vector3i> preventDoubles = new HashSet<>();
        for(int i=0; i < tickSpeed; i++) {
            Vector3i block = new Vector3i(
                    chunkPos.getX() + rng.nextInt(16),
                    section + rng.nextInt(16),
                    chunkPos.getZ() + rng.nextInt(16));
            if (cache.contains(block) && preventDoubles.add(block))
                tickLocation(new Location<>(world, block));
        }
    }

    private static void tickLocation(Location<World> location) {
//        long t0 = System.nanoTime();
        try {
            for (GrowthRecipe recipe : RecipeRegitry.getRecipesForLocation(location)) {
                if (OreGeno.getSyncScheduler().submit(()->{
                    boolean success = recipe.tryGrowth(rng, location);
                    if (success) OreGeno.l("Grew %s at %s in",
                            recipe.getGrowthResult().toString(),
                            location.getBlockPosition().toString(),
                            location.getExtent().getName());
                    return success;
                }).get())
                    return;
            }
        } catch (Exception ignore) {
            //illegal state exception if chunk unloads
            //concurrency exceptions by synchronizing
        }
//        long dt = System.nanoTime()-t0;
//        if (dt > 1_000_000)
//            OreGeno.w("One growth sub-tick took %.3fms for %d recipes", (double)dt/1000000.0, RecipeRegitry.count());
    }
}
