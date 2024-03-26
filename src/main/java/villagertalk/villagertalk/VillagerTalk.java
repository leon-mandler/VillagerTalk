package villagertalk.villagertalk;

import net.fabricmc.api.ModInitializer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

public class VillagerTalk implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("villagertalk");
	public static int numDetects = 0;
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		UseEntityCallback.EVENT.register((PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hitResult) -> {
			if(entity.getType() == EntityType.VILLAGER && !player.isSpectator() && villagerHasJob(entity)){
				//open UI
				//Generate LLM Prompt
				//etc.
				numDetects++;
				LOGGER.info("Success, Villager Click detected! NumDetects: " + numDetects);
			}
            return ActionResult.PASS;
        });
	}

	private boolean villagerHasJob(Entity entity){
		VillagerProfession profession = ((VillagerEntity) entity).getVillagerData().getProfession();
		return  !(profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT);
	}
}