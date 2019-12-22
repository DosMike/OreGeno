package de.dosmike.sponge.oregeno;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.Locatable;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Plugin(id="oregeno", name="oregeno", version="1.0", authors={"DosMike"})
public class OreGeno {
	
	public static void main(String[] args) { System.err.println("This plugin can not be run as executable!"); }
	
	static OreGeno instance;
	public static OreGeno getInstance() { return instance; }
	
	private SpongeExecutorService asyncScheduler = null;
	private SpongeExecutorService syncScheduler = null;

	public static SpongeExecutorService getAsyncScheduler() { return instance.asyncScheduler; }
	public static SpongeExecutorService getSyncScheduler() { return instance.syncScheduler; }

	public PluginContainer getContainer() { return Sponge.getPluginManager().fromInstance(this).get(); }
	
	@Inject
	private Logger logger;
	public static void l(String format, Object... args) { instance.logger.info(String.format(format, args)); }
	public static void w(String format, Object... args) { instance.logger.warn(String.format(format, args)); }
	
	public static Random rng = new Random(System.currentTimeMillis());
	
	/// --- === Main Plugin stuff === --- \\\

	private static int tickSpeed = 3;
	public static int getTickSpeed() {
		return tickSpeed;
	}
	public static void setTickSpeed(int tickspeed) {
		OreGeno.tickSpeed = tickspeed;
	}

	@Inject
	@ConfigDir(sharedRoot = false)
	private Path privateConfigDir;
	
	@Listener
	public void onServerInit(GameInitializationEvent event) {
		instance = this;
		
		asyncScheduler = Sponge.getScheduler().createAsyncExecutor(this);
		syncScheduler = Sponge.getScheduler().createSyncExecutor(this);
		
		Sponge.getEventManager().registerListeners(this, new EventListeners());
	}
	
	@Listener
	public void onServerStart(GameStartedServerEvent event) {
		CommandRegistra.register();
		
		loadConfigs();
		startTimers();
		
		l("Enjoy your pasta");
	}

	@Listener
	public void onReload(GameReloadEvent event) {
		loadConfigs();
		ChunkCandidateCache.clearCache();
	}

	@Listener
	public void onServerStopping(GameStoppingEvent event) {
		stopTimers();
	}
	
	public void loadConfigs() {
		File rules = privateConfigDir.resolve("rules.txt").toFile();
		if (!rules.exists()) ConfigParser.writeDefault(rules);
		ConfigParser.parse(rules);


	}
	
	public static void stopTimers() {
		Set<Task> tasks = Sponge.getScheduler().getScheduledTasks(instance);
		for (Task t : tasks) t.cancel();
	}

	private static Set<Location<World>> playerLocationCache = new HashSet<>();
	private static final Object playerLocationMutex = new Object();
	public static Collection<Location<World>> getPlayerLocationCache(World world) {
		synchronized (playerLocationMutex) {
			Set<Location<World>> tmp = new HashSet<>(playerLocationCache); //copy to actually not modify the mutex-ed version
			tmp.removeIf(loc->!world.isLoaded() || !loc.getExtent().equals(world));
			return tmp;
		}
	}

	private static ChunkCandidateCache cache = new ChunkCandidateCache();
	private static PerformanceReport performanceMonitor = new PerformanceReport();
	public static void startTimers() {

		Sponge.getScheduler().createTaskBuilder()
				.intervalTicks(1)
				.name("OreGeneo async Growth Tick")
				.execute(new RandomTickExecutor())
				.async()
				.submit(OreGeno.instance);
		getAsyncScheduler().submit(cache);
		Sponge.getScheduler().createTaskBuilder()
				.intervalTicks(1)
				.name("OreGeno sync Player Location cacher")
				.execute(()->{
					synchronized (playerLocationMutex) {
						playerLocationCache = Sponge.getServer().getOnlinePlayers().stream()
								.map(Locatable::getLocation)
								.collect(Collectors.toSet());
					}
				})
				.submit(OreGeno.instance);
		Sponge.getScheduler().createTaskBuilder()
				.intervalTicks(20*5) //every 5 seconds
				.name("OreGeno sync Performance Monitor")
				.execute(performanceMonitor)
				.submit(OreGeno.instance);
	}
}
