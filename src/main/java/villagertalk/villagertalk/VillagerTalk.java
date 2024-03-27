package villagertalk.villagertalk;

import net.fabricmc.api.ModInitializer;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerTalk implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("villagertalk");


	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

	}

	public static void onVillagerTradeOpen(PlayerEntity player, VillagerEntity villager){
		LOGGER.info("opened");
	}
	public static void onVillagerTradeClose(PlayerEntity serverPlayerEntity){
		LOGGER.info("closed");
	}
}