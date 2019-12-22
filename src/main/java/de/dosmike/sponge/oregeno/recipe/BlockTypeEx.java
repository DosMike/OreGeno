package de.dosmike.sponge.oregeno.recipe;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.data.DataQuery;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Optional;

/** extended Blocktype as bridge to 1.13 combining BlockType and Meta */
public class BlockTypeEx {

    private BlockType type;
    private Optional<Integer> meta;

    public BlockTypeEx(BlockType type) {
        this.type = type;
        this.meta = Optional.empty();
    }
    public BlockTypeEx(BlockType type, int meta) {
        this.type = type;
        this.meta = Optional.of(meta);
    }

    public BlockTypeEx(BlockState state) {
        this.type = state.getType();
        this.meta = state.toContainer().getInt(DataQuery.of('/', "UnsafeDamage"));
    }

    public BlockTypeEx(Location<World> location) {
        this(location.getBlock());
    }

    public BlockState getBlockState() {
        if (!meta.isPresent()) return type.getDefaultState();
        BlockState dummy = type.getDefaultState();
        DataView view = dummy.toContainer().set(DataQuery.of('/',"UnsafeDamage"), meta.orElse(0));
        return BlockState.builder().build(view).orElse(dummy);
    }

    public BlockType getType() {
        return type;
    }

    /** @return empty if no meta was set = default state */
    public Optional<Integer> getMeta() {
        return meta;
    }

    public void placeAt(Location<World> location) {
        location.setBlock(getBlockState());
    }

    public static BlockTypeEx fromString(String blockType) {
        int ex = blockType.indexOf('@');
        if (ex >= 0) {
            BlockType type = Sponge.getRegistry().getType(BlockType.class, blockType.substring(0, ex)).orElseThrow(()->new IllegalArgumentException("No such <BLOCKTYPE>: "+blockType));
            int meta = Integer.parseInt(blockType.substring(ex+1));
            return new BlockTypeEx(type, meta);
        } else {
            BlockType type = Sponge.getRegistry().getType(BlockType.class, blockType).orElseThrow(()->new IllegalArgumentException("No such <BLOCKTYPE>: "+blockType));
            return new BlockTypeEx(type);
        }
    }

    /** ignores meta */
    public boolean equals(BlockType type) {
        return this.type.equals(type);
    }

    public boolean equals(BlockTypeEx other) {
        return this.type.equals(other.type) &&
                this.meta.isPresent() == other.meta.isPresent() &&
                (!meta.isPresent() || this.meta.get()==other.meta.get());
    }

    public boolean equals(BlockState state) {
        return equals(new BlockTypeEx(state));
    }

    @Override
    public String toString() {
        return meta.map(meta -> type.toString() + "@" + meta).orElseGet(() -> type.toString());
    }
}
