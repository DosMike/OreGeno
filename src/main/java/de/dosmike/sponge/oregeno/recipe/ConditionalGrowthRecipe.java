package de.dosmike.sponge.oregeno.recipe;

import de.dosmike.sponge.oregeno.pattern.Pattern;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.effect.sound.SoundCategories;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.weather.Lightning;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;

import java.util.Collection;
import java.util.Random;

public class ConditionalGrowthRecipe implements GrowthRecipe {

    private Pattern patternList;
    private BlockTypeEx result;
    private double probability;
    private int effect;
    public static final int EFFECT_SOUND = 1;
    public static final int EFFECT_EXPLOSION = 2;
    public static final int EFFECT_LIGHTNING = 3;
    private Consume consume;

    public ConditionalGrowthRecipe(BlockTypeEx result, double spawnProbability, int effect, Consume consumption, Pattern... patterns) {
        probability = Math.max(0.0, Math.min(1.0, spawnProbability));
        this.result = result;
        patternList = Pattern.And(patterns);
        this.effect = effect;
        consume = consumption;
    }
    public ConditionalGrowthRecipe(BlockTypeEx result, double spawnProbability, int effect, Consume consumption, Collection<Pattern> patterns) {
        probability = Math.max(0.0, Math.min(1.0, spawnProbability));
        this.result = result;
        patternList = Pattern.And(patterns);
        this.effect = effect;
        consume = consumption;
    }

    @Override
    public boolean isRecipeValidAt(Location<World> location) {
        return patternList.matches(location);
    }

    @Override
    public double getGrowthProbablility() {
        return probability;
    }

    @Override
    public BlockTypeEx getGrowthResult() {
        return result;
    }

    @Override
    public Consume growthConsumes() {
        return consume;
    }

    @Override
    public boolean tryGrowth(Random generator, Location<World> location) {
        boolean success = GrowthRecipe.super.tryGrowth(generator, location);
        if (success) {
            if (effect == EFFECT_SOUND) {
                if (!location.getBlockType().equals(BlockTypes.AIR))
                    location.getExtent().playSound(
                            location.getBlock().getType().getSoundGroup().getBreakSound(),
                            SoundCategories.BLOCK,
                            location.getPosition(),
                            1f);
                location.getExtent().playSound(
                        getGrowthResult().getType().getSoundGroup().getPlaceSound(),
                        SoundCategories.BLOCK,
                        location.getPosition(),
                        1f);
            } else if (effect == EFFECT_EXPLOSION) {
                location.getExtent().triggerExplosion(Explosion.builder()
                        .location(location)
                        .radius(1f)
                        .canCauseFire(false)
                        .shouldBreakBlocks(false)
                        .shouldDamageEntities(false)
                        .shouldPlaySmoke(true)
                        .build());
            } else if (effect == EFFECT_LIGHTNING) {
                int y = location.getExtent().getHighestPositionAt(location.getBlockPosition()).getY();
                if (location.getBlockY() + 1 >= y) {
                    Lightning lightning = (Lightning) location.getExtent().createEntity(EntityTypes.LIGHTNING, location.getBlockPosition());
                    lightning.setEffect(true);
                    location.getExtent().spawnEntity(lightning);
                }
            }
        }
        return success;
    }
}
