package de.dosmike.sponge.oregeno.pattern;

import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

@FunctionalInterface
public interface Pattern {

    boolean matches(Location<World> location);

    static Pattern Or(Pattern... patterns) {
        if (patterns == null || patterns.length == 0) return (loc)->true;
        if (patterns.length == 1) return patterns[0];
        return (loc)->{for (Pattern p : patterns) if (p.matches(loc)) return true; return false;};
    }
    static Pattern Or(Collection<Pattern> patterns) {
        if (patterns == null || patterns.size() == 0) return (loc)->true;
        if (patterns.size() == 1) return patterns.iterator().next(); //unwrap
        return (loc)->{for (Pattern p : patterns) if (p.matches(loc)) return true; return false;};
    }

    static Pattern Not(Pattern pattern) {
        return (loc)->!pattern.matches(loc);
    }

    static Pattern And(Pattern... patterns) {
        if (patterns == null || patterns.length == 0) return (loc)->true;
        if (patterns.length == 1) return patterns[0];
        return (loc)->{for (Pattern p : patterns) if (!p.matches(loc)) return false; return true;};
    }
    static Pattern And(Collection<Pattern> patterns) {
        if (patterns == null || patterns.size() == 0) return (loc)->true;
        if (patterns.size() == 1) return patterns.iterator().next(); //unwrap
        return (loc)->{for (Pattern p : patterns) if (!p.matches(loc)) return false; return true;};
    }

    //Region builder
    class Builder {
        private List<Pattern> elements = new LinkedList<>();
        private boolean isand;
        private Builder(boolean and) {
            isand = and;
        }
        public Builder addPattern(Pattern pattern) {
            elements.add(pattern);
            return this;
        }
        public Pattern build() {
            return isand ? Pattern.And(elements) : Pattern.Or(elements);
        }
    }

    /** @param and true to and-concat patterns, false to or-concat patterns */
    static Builder builder(boolean and) {
        return new Builder(and);
    }
    //endregion

}
