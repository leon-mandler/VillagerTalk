package villagertalk.villagertalk;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villagertalk.villagertalk.VillagerTalkPackets.VillagerTalkC2SNetworkingConstants;
import villagertalk.villagertalk.VillagerTalkPackets.VillagerTalkS2CNetworkingConstants;



public class VillagerTalk implements ModInitializer{
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("villagertalk");

    public static boolean TESTING = false;
    @Override
    public void onInitialize(){
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        ServerPlayNetworking.registerGlobalReceiver(VillagerTalkC2SNetworkingConstants.PLAYER_SENT_PROMPT, (server, player, handler, buf, responseSender) -> {
            String prompt = buf.readString();
            server.execute(() -> {
                if(TESTING){
                    System.out.println("Received prompt from player: " + prompt);
                }
                onPlayerPromptReceived(prompt, player);
            });
        });
    }

    /**
     * onPlayerPromptReceived
     * Called when the player sends a prompt to the server
     * sends a response to the player
     * @param prompt The prompt sent by the player
     * @param player The player who sent the prompt
     */
    private void onPlayerPromptReceived(String prompt, ServerPlayerEntity player){
        String response = generateLLMResponse(prompt);
        sendResponsePacket(response, player);
    }

    /**
     * generateLLMResponse
     * Generates a response to a prompt using the LLM
     * @param prompt The prompt to generate a response to
     * @return The generated response
     */
    private String generateLLMResponse(String prompt){
        return "Test Response, lorem ipsum blallballall lajd9uiawhnduanwdiuawndiuawnbdiuawhndaiuwhbdiauwbdiazuwbd";
    }

    /**
     * sendResponsePacket
     * Sends a response to the player
     * @param response The response to send
     * @param player The player to send the response to
     */
    private void sendResponsePacket(String response, ServerPlayerEntity player){
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(response);
        ServerPlayNetworking.send(player, VillagerTalkS2CNetworkingConstants.VILLAGER_RESPONSE_PACKET, buf);
    }



    public static void onVillagerTradeOpen(PlayerEntity player, VillagerEntity villager){
        if (!(player instanceof ServerPlayerEntity)){
            LOGGER.info("onVillagerTradeOpen: player " + player.getName().getString() + " is NOT serverPlayerEntity");
            return;
        }
        sendUIOpenPacket(player, villager);
    }

    private static void sendUIOpenPacket(PlayerEntity player, VillagerEntity villager){
        PacketByteBuf buf = PacketByteBufs.empty();
        //TODO write relevant data for ui creation to buf
        ServerPlayNetworking.send((ServerPlayerEntity) player, VillagerTalkS2CNetworkingConstants.VILLAGERUI_OPEN_PACKET_ID, buf);
    }

    public static void onVillagerTradeClose(PlayerEntity player){
        if (!(player instanceof ServerPlayerEntity)){
            LOGGER.info("onVillagerTradeClose: player " + player.getName().getString() + " is NOT serverPlayerEntity");
            return;
        }
        sendUIClosePacket(player);
    }

    private static void sendUIClosePacket(PlayerEntity player){
        ServerPlayNetworking.send((ServerPlayerEntity) player, VillagerTalkS2CNetworkingConstants.VILLAGERUI_CLOSE_PACKET_ID, PacketByteBufs.empty());
    }
}