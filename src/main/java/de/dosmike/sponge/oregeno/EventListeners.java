package de.dosmike.sponge.oregeno;

import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.world.UnloadWorldEvent;
import org.spongepowered.api.event.world.chunk.LoadChunkEvent;

import java.util.Optional;

public class EventListeners {

    @Listener
    public void onLoadChunk(LoadChunkEvent event) {
//        ChunkCandidateCache.cacheLater(event.getTargetChunk());
    }

    @Listener
    public void onUnloadWorld(UnloadWorldEvent event) {
        ChunkCandidateCache.clearCache(event.getTargetWorld());
    }

    @Listener
    public void onChangeBlock(ChangeBlockEvent event) {
        //touch the chunk, so it may be re-cached
        event.getTransactions().stream()
                .map(t->t.getOriginal().getLocation())
                .filter(Optional::isPresent).map(Optional::get)
                .findAny().ifPresent(ChunkCandidateCache::touch);
    }

}
