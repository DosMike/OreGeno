package de.dosmike.sponge.oregeno;

import de.dosmike.sponge.oregeno.recipe.RecipeRegitry;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.channel.MessageReceiver;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;

import java.util.LinkedList;
import java.util.List;

public class PerformanceReport  implements Runnable{

    private static class DataPoints<T> {
        private int max;
        private LinkedList<T> points = new LinkedList<>();

        public DataPoints(int maxPoints) {
            max = maxPoints;
        }
        public void pushPoint(T data) {
            points.add(data);
            if (points.size() > max) points.pop();
        }

        public T getValue(int index) {
            return points.get(index);
        }
        public T latestValue(int index) {
            return points.getLast();
        }
        public List<T> getValues(int... index) {
            List<T> selected = new LinkedList<>();
            for (int i : index) if (i < points.size()) selected.add(points.get(i));
            return selected;
        }
        public List<T> asList() {
            return new LinkedList<>(points);
        }

        public void clear() {
            points.clear();
        }
    }

    public static final int DATAPOINTS = 35;
    public static final int TIMESTEP = 5;

    private static DataPoints<Double> growthTickMicros = new DataPoints<>(DATAPOINTS);
    private static DataPoints<Integer> growthTickRuns = new DataPoints<>(DATAPOINTS);
    private static DataPoints<Integer> chunkCacheQueue = new DataPoints<>(DATAPOINTS);
    private static DataPoints<Long> chunkCacheMillis = new DataPoints<>(DATAPOINTS);
    private static DataPoints<Integer> chunkCacheRuns = new DataPoints<>(DATAPOINTS);
    private static final Object updateMutex = new Object();

    private static TextColor[] colorSteps = new TextColor[]{
            TextColors.BLUE,
            TextColors.DARK_AQUA,
            TextColors.DARK_GREEN,
            TextColors.GREEN,
            TextColors.YELLOW,
            TextColors.GOLD,
            TextColors.RED,
            TextColors.DARK_RED,
    };
    private static char[] charSteps = new char[]{'\u2581','\u2582','\u2583','\u2584','\u2585','\u2586','\u2587','\u2588'};
    @Override
    public void run() {
        long totalTime;
        int totalRuns;
        int totalSize;
        synchronized (updateMutex) {
            { // Fetch Growth-Tick data
                synchronized (measureGrowthMutex) {
                    totalTime = measureGrowthNanos;
                    measureGrowthNanos = 0L;
                    totalRuns = measureGrowthRuns;
                    measureGrowthRuns = 0;
                }
                double average = measureGrowthNanos / (TIMESTEP * 1000.0 * measureGrowthRuns); //us
                if (average > 50_000 && !ChunkCandidateCache.isBusy()) {
                    OreGeno.w("Growth-tick took %.3fms for %d recipes while fully cached!", average / 1000.0, RecipeRegitry.count());
                }
//        OreGeno.l("Average: %.2fus (%.2fms / %d runs)", average, totalNanos / 1000000.0, totalRuns);
                growthTickMicros.pushPoint(totalTime / 1000.0);
                growthTickRuns.pushPoint(totalRuns);
            }
            { //Fetch Chunk cache data
                synchronized (measureQueueMutex) {
                    totalTime = measureQueueMillis;
                    measureQueueMillis = 0L;
                    totalRuns = measureQueueRuns;
                    measureQueueRuns = 0;
                    totalSize = measureQueueCurrentSize;
                    measureQueueCurrentSize = 0;
                }
                chunkCacheMillis.pushPoint(totalTime);
                chunkCacheRuns.pushPoint(totalRuns);
                chunkCacheQueue.pushPoint(totalSize);
            }
        }
    }

    // Performance Monitoring Growth-Tick
    private static long measureGrowthNanos = 0L;
    private static int measureGrowthRuns = 0;
    private static final Object measureGrowthMutex = new Object();

    public static void pushNSPT(long nspt) {
        synchronized (measureGrowthMutex) {
            measureGrowthNanos += nspt;
            measureGrowthRuns++;
        }
    }

    // Performance Monitoring Chunk Cache
    private static int measureQueueCurrentSize = 0;
    private static long measureQueueMillis = 0L;
    private static int measureQueueRuns = 0;
    private static final Object measureQueueMutex = new Object();

    /** nspc is the time a recent cach runner took,
     * nowSize is the size of the remaining queue */
    public static void pushQueue(long mspc, int nowSize) {
        synchronized (measureQueueMutex) {
            measureQueueMillis += mspc;
            measureQueueRuns ++;
            measureQueueCurrentSize = nowSize;
        }
    }
    public static void pushQueue(int nowSize) {
        synchronized (measureQueueMutex) {
            measureQueueCurrentSize = nowSize;
        }
    }

    // Pretty printing
    private static List<Text> reportCache = new LinkedList<>();
    private static Long reportTimestamp = 0L;

    public static void printReport(MessageReceiver receiver) {
        if (System.currentTimeMillis() - reportTimestamp > 1000) {
            Text.Builder growthTickMsg = Text.builder("Growth Tick: ");
            Text.Builder growthTickGraph = Text.builder();
            Text.Builder cacheTimeMsg = Text.builder("Cache Time: ");
            Text.Builder cacheTimeGraph = Text.builder();
            Text.Builder cacheSizeMsg = Text.builder("Cache Size: ");
            Text.Builder cacheSizeGraph = Text.builder();
            synchronized (updateMutex) {
                int totalSteps = growthTickRuns.asList().size();
                int oldest = (totalSteps - 1) * TIMESTEP; //oldest value age in seconds

                double totalGrowthUSPT = 0.0;
                int totalGrowthRuns = 0;
                double totalCacheUSPT = 0.0;
                int totalCacheRuns = 0;
                int totalCacheQueue = 0;
                double v, a; int c;
                int s;
                for (int i = 0; i < totalSteps; i++) {
                    v = growthTickMicros.getValue(i);
                    c = growthTickRuns.getValue(i);
                    totalGrowthUSPT += v;
                    totalGrowthRuns += c;
                    a = c == 0 ? 0 : v / c; // in us
                    s = toIndex(a, 50_000.0); //50 ms
                    growthTickGraph.append(Text.builder(charSteps[s])
                            .color(colorSteps[s])
                            .onHover(TextActions.showText(Text.of(
                                    TextColors.WHITE, "Value Age: ", TextColors.GRAY, oldest - i * TIMESTEP, "s", Text.NEW_LINE,
                                    TextColors.WHITE, "Average (1/t): ", colorSteps[s], formatMicros(a), Text.NEW_LINE,
                                    TextColors.WHITE, "Time Sum (ms): ", TextColors.GRAY, String.format("%.2f", v / 1000), Text.NEW_LINE,
                                    TextColors.WHITE, "Tick Count: ", TextColors.GRAY, c
                            )))
                            .build());

                    v = chunkCacheMillis.getValue(i);
                    c = chunkCacheRuns.getValue(i);
                    totalCacheUSPT += v;
                    totalCacheRuns += c;
                    a = c == 0 ? 0 : v / c;
                    s = toIndex(a, 500.0); //more than 500 ms critical? this is rather arbitrary
                    cacheTimeGraph.append(Text.builder(charSteps[s])
                            .color(colorSteps[s])
                            .onHover(TextActions.showText(Text.of(
                                    TextColors.WHITE, "Value Age: ", TextColors.GRAY, oldest - i * TIMESTEP, "s", Text.NEW_LINE,
                                    TextColors.WHITE, "Average (ms/t): ", colorSteps[s], String.format("%.2f", a), Text.NEW_LINE,
                                    TextColors.WHITE, "Time Sum (s): ", TextColors.GRAY, String.format("%.2f", v / 1000), Text.NEW_LINE,
                                    TextColors.WHITE, "Chunks Cached: ", TextColors.GRAY, c
                            )))
                            .build());

                    v = chunkCacheQueue.getValue(i);
                    totalCacheQueue += v;
                    s = toIndex(v, 500.0); //more than 500 chunk in the queue
                    cacheSizeGraph.append(Text.builder(charSteps[s])
                            .color(colorSteps[s])
                            .onHover(TextActions.showText(Text.of(
                                    TextColors.WHITE, "Value Age: ", TextColors.GRAY, oldest - i * TIMESTEP, "s", Text.NEW_LINE,
                                    TextColors.WHITE, "Cache Queue Size: ", colorSteps[s], v
                            )))
                            .build());
                }
                growthTickMsg.append(Text.of(TextColors.GRAY, String.format("Average: %.3fms (%d runs over %ds)", totalGrowthRuns==0?0: totalGrowthUSPT / (1000 * totalGrowthRuns), totalGrowthRuns, oldest)));
                cacheTimeMsg.append(Text.of(TextColors.GRAY, String.format("Average: %.3fms (%d runs over: %ds)", totalCacheRuns==0?0: totalCacheUSPT / (1000 * totalCacheRuns), totalGrowthRuns, oldest)));
                cacheSizeMsg.append(Text.of(TextColors.GRAY, String.format("Average: %.3f (over: %ds)", totalCacheQueue / (double) oldest, oldest)));
            }
            reportCache.clear();
            reportCache.add(growthTickMsg.build());
            reportCache.add(growthTickGraph.build());
            reportCache.add(cacheTimeMsg.build());
            reportCache.add(cacheTimeGraph.build());
            reportCache.add(cacheSizeMsg.build());
            reportCache.add(cacheSizeGraph.build());
            reportTimestamp = System.currentTimeMillis();
        }
        receiver.sendMessage(Text.of("================== OreGeno Performance =================="));
        receiver.sendMessages(reportCache);
    }
    private static int toIndex(Double value, Double max) {
        int intmax = charSteps.length-1;
        return (int)Math.max(0,Math.min(intmax,Math.round(value*intmax/max)));
    }
    private static String formatMicros(double micros) {
        if (micros < 1000)
            return String.format("%.3f us", micros);
        else
            return String.format("%.3f ms", micros/1000);
    }

}
