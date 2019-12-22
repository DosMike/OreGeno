package de.dosmike.sponge.oregeno;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

public class CommandRegistra {
	public static void register () {
		Sponge.getCommandManager().register(OreGeno.instance, CommandSpec.builder()
				.permission("oregeno.command.base")
				.child(CommandSpec.builder()
						.permission("oregeno.command.performance")
						.executor((src,args)->{
							PerformanceReport.printReport(src);
							return CommandResult.success();
						})
						.build(), "performance")
				.child(CommandSpec.builder()
						.permission("oregeno.command.tickspeed")
						.arguments(GenericArguments.optional(GenericArguments.integer(Text.of("tickspeed"))))
						.executor((src,args)->{
							if (!args.hasAny("tickspeed")) {
								src.sendMessage(Text.of(TextColors.YELLOW,"Changing the growth-tick speed is not recommended and thus temporary."));
								src.sendMessage(Text.of(TextColors.YELLOW,"This tick speed is not linked to the Games default ticks, but calculated the same way."));
								src.sendMessage(Text.of("The default value is ",TextColors.YELLOW,"3",TextColors.RESET," - Current tick speed is ",TextColors.YELLOW,OreGeno.getTickSpeed()));
							} else {
								int tickspeed = args.<Integer>getOne("tickspeed").get();
								tickspeed = Math.max(0, tickspeed);
								OreGeno.setTickSpeed(tickspeed);
								src.sendMessage(Text.of("Growth-Tick speed was set ", TextColors.YELLOW, tickspeed));
							}
							return CommandResult.success();
						})
						.build(), "tickspeed")
				.build(), "OreGeno");
	}
}
