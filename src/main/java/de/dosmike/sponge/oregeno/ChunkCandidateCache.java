package de.dosmike.sponge.oregeno;

import com.flowpowered.math.vector.Vector3i;
import de.dosmike.sponge.oregeno.recipe.RecipeRegitry;
import javafx.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.*;

/** caches all locations, that a recipe could be applied to.
 * In order to keep performance I do this async. This may cause
 * code to break at various points due to chunk unloading while
 * a task is working on it, but nothing a catch{} shouldn't be
 * able to fix, since we're not writing any data async here. */
public class ChunkCandidateCache implements Runnable {

    //region Chunk Cache
    // Caching candidate locations for chunks in a world

    /** key is a chunk, value are locations within the chunk */
    private Map<Vector3i, Set<Vector3i>> cache = new HashMap<>();
    /** cache age tracker. Track a chunk for let's say 5 min
     * when no player is around before it auto-touches */
    private Map<Vector3i, Long> cacheAge = new HashMap<>();
    private final Object chunkMutex = new Object();

    public void touch(Chunk chunk) {
        synchronized (chunkMutex) {
            cache.remove(chunk.getPosition());
            cacheAge.remove(chunk.getPosition());
        }
    }
    public static void touch(Location<World> location) {
        World w = location.getExtent();
        w.getChunk(location.getChunkPosition()).ifPresent(c->{
            ChunkCandidateCache cache = ChunkCandidateCache.getWorldCache(w);
            if (cache!=null)
                cache.touch(c);
        });
    }

    public @Nullable Set<Vector3i> getCandidates(Chunk chunk) {
        synchronized (chunkMutex) {
            return cache.get(chunk.getPosition());
        }
    }

    public boolean isChunkCached(Chunk chunk) {
        synchronized (chunkMutex) {
            return cache.containsKey(chunk.getPosition());
        }
    }

    private static void cacheStore(Chunk chunk, Set<Vector3i> candidates) {
        synchronized (worldsMutex) {
            ChunkCandidateCache cache = worldsCache.get(chunk.getWorld().getUniqueId());
            if (cache == null) {
                cache = new ChunkCandidateCache();
                worldsCache.put(chunk.getWorld().getUniqueId(), cache);
            }
            synchronized (cache.chunkMutex) {
                cache.cache.put(chunk.getPosition(), candidates);
                cache.cacheAge.put(chunk.getPosition(), System.currentTimeMillis());
            }
        }
    }
    //endregion
    //region Cache Runner
    // Responsible for actively caching chunks

    public static class CacheRunner {
        private Chunk chunk;
        private Pair<UUID, Vector3i> chunkKey;
        public CacheRunner(Chunk chunk, Pair<UUID, Vector3i> chunkKey) {
            this.chunk = chunk;
            this.chunkKey = chunkKey;
        }

        public void run() {
            if (!chunk.isLoaded()) {
                cancelNotify();
                return;
            }
            Set<Vector3i> candidates = new HashSet<>();
            Vector3i min = chunk.getBlockMin();
            Vector3i max = chunk.getBlockMax();
            long t0 = System.currentTimeMillis();
            for (int y = 0; y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    for (int x = min.getX(); x <= max.getX(); x++) {
                        Vector3i at = new Vector3i(x, y, z);
                        try {
                            boolean applicable = !RecipeRegitry.getRecipesForLocation(chunk.getWorld().getLocation(at)).isEmpty();
                            if (applicable)
                                candidates.add(new Vector3i(x, y, z));
                        } catch (Exception e) {
                            cancelNotify();
                            return;
                        }
                    }
                }
            }
            long dt = System.currentTimeMillis() - t0;
            doneNotify(dt, candidates);
        }
        private void cancelNotify(){
            int size;
            synchronized (chunkQueueMutex) {
                // element is processes, remove
                if (!toCache.remove(chunkKey))
                    OreGeno.l("Could not remove chunk "+chunkKey.getValue()+" exceptionally!");
                size = toCache.size();
            }
                OreGeno.l("Skipped unloaded Chunk");
            PerformanceReport.pushQueue(size);
        }
        private void doneNotify(long dt, Set<Vector3i> candidates) {
            int size;
            synchronized (chunkQueueMutex) {
                // element is processes, remove
                if (!toCache.remove(chunkKey))
                    OreGeno.l("Could not remove chunk "+chunkKey.getValue()+" orderly!");
                size = toCache.size();
            }
            PerformanceReport.pushQueue(dt, size);
            cacheStore(chunk, candidates);
        }
    }

    public static void cacheLater(Chunk chunk) {
        synchronized (chunkQueueMutex) {
            Pair<UUID, Vector3i> chunkRef = new Pair<>(chunk.getWorld().getUniqueId(), chunk.getPosition().clone());
            if (!toCache.contains(chunkRef))
                toCache.add(chunkRef);
        }
    }
    private static List<Pair<UUID,Vector3i>> toCache = new LinkedList<>();
    private static final Object chunkQueueMutex = new Object();
    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                OreGeno.l("Chunk Caching was halted");
                break;
            }
            Chunk target = null;
            Pair<UUID, Vector3i> element = null;
            synchronized (chunkQueueMutex) {
                while (!toCache.isEmpty() && target == null) {
                    element = toCache.get(0);
                    Optional<World> w = Sponge.getServer().getWorld(element.getKey());
                    if (w.isPresent())
                        target = w.get().getChunk(element.getValue()).orElse(null);

                    if (target == null) //chunk is not loaded
                        toCache.remove(0);
                }
            }
            if (target != null) {
                try {
                    new CacheRunner(target, element).run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
//            //clean caches
//            synchronized (worldsMutex) {
//                for (ChunkCandidateCache cache : worldsCache.values()) {
//                    synchronized (cache.chunkMutex) {
//                        Set<Vector3i> oldKeys = cache.cacheAge.entrySet().stream()
//                                .filter(e->System.currentTimeMillis()-e.getValue()>300_000L)
//                                .map(Map.Entry::getKey)
//                                .collect(Collectors.toSet());
//                        for (Vector3i key : oldKeys) {
//                            cache.cacheAge.remove(key);
//                            cache.cache.remove(key);
//                        }
//                    }
//                }
//            }
        }
    }
    public static boolean isBusy() {
        synchronized (chunkQueueMutex) {
            return !toCache.isEmpty();
        }
    }

    //endregion
    //region World Cache
    // static stuff responsible for handling per-world caches

    private static Map<UUID, ChunkCandidateCache> worldsCache = new HashMap<>();
    private static final Object worldsMutex = new Object();
    public static void clearCache() {
        synchronized (worldsMutex) {
            worldsCache.clear();
        }
    }
    public static void clearCache(World world) {
        synchronized (worldsMutex) {
            worldsCache.remove(world.getUniqueId());
        }
        synchronized (chunkQueueMutex) {
            toCache.removeIf(pair->pair.getKey().equals(world.getUniqueId()));
        }
    }
    public static @Nullable ChunkCandidateCache getWorldCache(Chunk chunk) {
        synchronized (worldsMutex) {
            return worldsCache.get(chunk.getWorld().getUniqueId());
        }
    }
    public static @Nullable ChunkCandidateCache getWorldCache(World world) {
        synchronized (worldsMutex) {
            return worldsCache.get(world.getUniqueId());
        }
    }

    //endregion
}
