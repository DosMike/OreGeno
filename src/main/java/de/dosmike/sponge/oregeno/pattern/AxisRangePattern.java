package de.dosmike.sponge.oregeno.pattern;

import org.spongepowered.api.util.Axis;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.regex.Matcher;

public class AxisRangePattern implements Pattern {

    Axis axis;
    long min;
    long max;
    public AxisRangePattern(Axis axis, long limit1, long limit2) {
        this.axis = axis;
        this.min = Math.min(limit1, limit2);
        this.max = Math.max(limit1, limit2);
    }

    private static final java.util.regex.Pattern comparePattern1 = java.util.regex.Pattern.compile("(-?[0-9]+<=?)?([XYZ])(<=?-?[0-9]+)?");
    private static final java.util.regex.Pattern comparePattern2 = java.util.regex.Pattern.compile("(-?[0-9]+)(<=?|>=?|==?)([XYZ])|([XYZ])(<=?|>=?|==?)(-?[0-9]+)");
    public AxisRangePattern(String comparison) {
        Matcher matcher;
        matcher = comparePattern1.matcher(comparison.toUpperCase());
        if (matcher.matches()) {
            try {
                String minVal = matcher.group(1);
                String maxVal = matcher.group(3);
                if (minVal != null || maxVal != null) {
                    axis = Axis.valueOf(matcher.group(2));
                    if (minVal != null) {
                        min = Long.parseLong(minVal.substring(0, minVal.indexOf('<')));
                        if (!minVal.endsWith("=")) min++; //pull limit to be always inclusive
                    }
                    if (maxVal != null) {
                        boolean inclusive = maxVal.charAt(1)=='=';
                        max = Long.parseLong(maxVal.substring(inclusive?2:1));
                        if (!inclusive) min--; //pull limit to be always inclusive
                    }
                    return; //valid configuration
                }
            } catch (Exception ignore) {}
        }
        matcher = comparePattern2.matcher(comparison.toUpperCase());
        if (matcher.matches()) {
            String leftVal = matcher.group(1);
            String comparator = matcher.group(2);
            String rightVal = matcher.group(3);
            //left number?
            try {
                long limit = Long.parseLong(leftVal);
                axis = Axis.valueOf(rightVal);
                switch (comparator) {
                    case "=":
                    case "==":
                        min = max = limit;
                        break;
                    case "<":
                        min = limit+1;
                        break;
                    case "<=":
                        min = limit;
                        break;
                    case ">":
                        max = limit-1;
                        break;
                    case ">=":
                        max = limit;
                        break;
                    default:
                        throw new IllegalStateException("Unsupported comparator passed check");
                }
                return; //valid configuration
            } catch (Exception ignore) {}
            //right number?
            try {
                long limit = Long.parseLong(leftVal);
                axis = Axis.valueOf(rightVal);
                switch (comparator) {
                    case "=":
                    case "==":
                        min = max = limit;
                        break;
                    case "<":
                        max = limit-1;
                        break;
                    case "<=":
                        max = limit;
                        break;
                    case ">":
                        min = limit+1;
                        break;
                    case ">=":
                        min = limit;
                        break;
                    default:
                        throw new IllegalStateException("Unsupported comparator passed check");
                }
                return; //valid configuration
            } catch (Exception ignore) {}
        }
        throw new IllegalArgumentException("Could not parse axis range pattern");
    }

    @Override
    public boolean matches(Location<World> location) {
        double at = axis.getComponent(location.getPosition());
        long blockAt = (long)Math.floor(at);
        return min <= blockAt && blockAt <= max;
    }

}
