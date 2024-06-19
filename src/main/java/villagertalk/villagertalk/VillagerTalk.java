package villagertalk.villagertalk;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villagertalk.villagertalk.VillagerTalkPackets.VillagerTalkC2SNetworkingConstants;
import villagertalk.villagertalk.VillagerTalkPackets.VillagerTalkS2CNetworkingConstants;
import villagertalk.villagertalk.mixin.VillagerEntityInvoker;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main serverside class for the VillagerTalk mod
 * <p>
 * This class is responsible for handling the main logic of the mod, including
 * handling the networking, generating responses, and processing commands.
 * It also contains the main entry point for the mod.
 */

public class VillagerTalk implements ModInitializer{

    public static final Logger LOGGER = LoggerFactory.getLogger("villagertalk");
    private static final String MAYBE_SOME_KEY = "7Uu3GnZeci1XvmHRW0HBJFkblB3TR453p4L3NWwr2mmBakcY-jorp-ks";

    private static final OpenAiService APIService = new OpenAiService((new StringBuilder(MAYBE_SOME_KEY)).reverse()
                                                                                                         .toString());
    public static boolean TESTING = false;

    private static final Map<UUID, VillagerEntity> activeVillagers = new HashMap<>();
    private static final List<VillagerChatData> previousChats = new ArrayList<>();

    public static final Pattern CHANGE_EMERALD_AMOUNT = Pattern.compile("!change_emerald_amount\\(([^,]+),\\s*(\\d+)\\)");
    public static final Pattern CHANGE_ITEM_AMOUNT = Pattern.compile("!change_item_amount\\(([^,]+),\\s*(\\d+)\\)");
    public static final Pattern SPAWN_GOLEM = Pattern.compile("!spawn_golem\\b");

    public static final Pattern[] COMMANDS = {CHANGE_EMERALD_AMOUNT, CHANGE_ITEM_AMOUNT, SPAWN_GOLEM};

    @Override
    public void onInitialize(){
        ServerPlayNetworking.registerGlobalReceiver(VillagerTalkC2SNetworkingConstants.PLAYER_SENT_PROMPT,
                                                    (server, player, handler, buf, responseSender) -> {
                                                        String playerPrompt = buf.readString();
                                                        server.execute(() -> {
                                                            CompletableFuture.supplyAsync(() -> {
                                                                return generateLLMResponse(playerPrompt, player);
                                                            }).thenAccept(response -> {
                                                                sendResponsePacket(response, player);
                                                            });
                                                        });
                                                    });

        ServerPlayNetworking.registerGlobalReceiver(VillagerTalkC2SNetworkingConstants.INITIAL_VILLAGER_MESSAGE_REQUEST,
                                                    (server, player, handler, buf, responseSender) -> {
                                                        server.execute(() -> {
                                                            CompletableFuture.supplyAsync(() -> {
                                                                return generateInitialResponse(player);
                                                            }).thenAccept(response -> {
                                                                sendInitialMessage(player, response);
                                                            });
                                                        });
                                                    });

        ServerPlayNetworking.registerGlobalReceiver(VillagerTalkC2SNetworkingConstants.VILLAGER_CLOSED,
                                                    (server, player, handler, buf, responseSender) -> {
                                                        server.execute(() -> {
                                                            onPlayerVillagerCLose(player);
                                                        });
                                                    });
    }

    private void sendInitialMessage(ServerPlayerEntity player, String message){
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(message);
        ServerPlayNetworking.send(player, VillagerTalkS2CNetworkingConstants.VILLAGER_INITIAL_MESSAGE, buf);
    }

    private String generateInitialResponse(ServerPlayerEntity player){
        VillagerEntity villager = activeVillagers.get(player.getUuid());
        if (villager == null){
            return "ERROR: no active villager found for " + player.getUuid() + "Please reopen villager.";
        }

        VillagerChatData chatData = findExistingChat(villager.getUuid(), player.getUuid());

        if (chatData != null){
            chatData.refreshSystemPrompt();

            String existingChatHistory = chatData.getChatHistoryAsString();
            if (existingChatHistory.getBytes().length <= 32_000){ //make sure existing chats are small enough to be sent via packet
                return existingChatHistory;
            }
            //existing chat is too large to be sent, start new chat
            ChatMessage newInitialMessage = makeAPICall(chatData.freshChatHistory());
            chatData.addChatMessage(newInitialMessage);
            return newInitialMessage.getContent();
        }

        //make new villagerChatData
        chatData = new VillagerChatData(villager, player.getUuid());
        previousChats.add(chatData);
        //generate initial villager message and return it
        ChatMessage initialMessage = makeAPICall(chatData.chatHistory());
        chatData.chatHistory().add(initialMessage);

        return chatData.getVillagerName() + ":\n " + initialMessage.getContent() + "\n\n";
    }

    private VillagerChatData findExistingChat(UUID villagerID, UUID playerID){
        for (VillagerChatData v : previousChats){
            if (v.getVillagerID().equals(villagerID) && v.playerID().equals(playerID)){
                return v;
            }
        }
        return null;
    }

    private ChatMessage makeAPICall(List<ChatMessage> messages){
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                                                                       .model("gpt-4o")
                                                                       .messages(messages)
                                                                       .n(1)
                                                                       .build();

        return APIService.createChatCompletion(completionRequest).getChoices().get(0).getMessage();
    }


    private void onPlayerVillagerCLose(ServerPlayerEntity player){
        activeVillagers.remove(player.getUuid());
    }

    /**
     * generateLLMResponse
     * Generates a response to a chathistory using the LLM
     *
     * @param prompt The chathistory to generate a response to
     * @return The generated response
     */
    private String generateLLMResponse(String prompt, ServerPlayerEntity player){
        VillagerChatData existingChat = findExistingChat(activeVillagers.get(player.getUuid()).getUuid(),
                                                         player.getUuid());

        if (existingChat == null){
            return "Error: no existing chat found, please reopen the villager.";
        }
        existingChat.refreshSystemPrompt();
        existingChat.addChatMessage(new ChatMessage(ChatMessageRole.USER.value(), prompt));
        ChatMessage response = makeAPICall(existingChat.chatHistory());
        existingChat.addChatMessage(response);

        return existingChat.getVillagerName() + ":\n" + processLLMResponse(response.getContent(),
                                                                           player,
                                                                           existingChat.villager()) + "\n\n";
    }

    private String processLLMResponse(String response, ServerPlayerEntity player, VillagerEntity villager){
        for (Pattern p : COMMANDS){
            Matcher matcher = p.matcher(response);
            StringBuilder processedResponse = new StringBuilder(response);
            while (matcher.find()){
                int start = matcher.start();
                int end = matcher.end();

                if (p.pattern().equals(SPAWN_GOLEM.pattern())){
                    spawnIronGolem(player, villager);
                } else{
                    String itemName = matcher.group(1).trim();
                    int newAmount = Integer.parseInt(matcher.group(2).trim());

                    if (p.pattern().equals(CHANGE_EMERALD_AMOUNT.pattern())){
                        changeAmount(itemName, newAmount, player, villager, true);
                    } else if (p.pattern().equals(CHANGE_ITEM_AMOUNT.pattern())){
                        changeAmount(itemName, newAmount, player, villager, false);
                    }
                }
                processedResponse.replace(start, end, "");
            }

            response = processedResponse.toString();
        }

        return response;
    }

    private void changeAmount(String itemName, int newAmount, ServerPlayerEntity player, VillagerEntity villager, boolean changeEmeraldAmount){
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler handler)){
            return;
        }
        TradeOfferList curOffers = handler.getRecipes();
        for (TradeOffer offer : curOffers){
            if (changeEmeraldAmount){
                // Change emerald amount
                if (offer.getSellItem().getItem().getName().getString().equals(itemName)){ //SellItem equals Item
                    offer.getOriginalFirstBuyItem().setCount(newAmount); //change the BuyItem (Emerald) Amount
                    break;
                }
                if (offer.getOriginalFirstBuyItem()
                         .getItem()
                         .getName()
                         .getString()
                         .equals(itemName)){ //BuyItem equals Item
                    offer.getSellItem().setCount(newAmount); //change the SellItem (Emerald) Amount
                    break;
                }
            } else{
                // Change item amount
                if (offer.getSellItem().getItem().getName().getString().equals(itemName)){ //SellItem equals Item
                    offer.getSellItem().setCount(newAmount); //change the SellItem Amount
                    break;
                }
                if (offer.getOriginalFirstBuyItem()
                         .getItem()
                         .getName()
                         .getString()
                         .equals(itemName)){ //BuyItem equals Item
                    offer.getOriginalFirstBuyItem().setCount(newAmount); //change the BuyItem Amount
                    break;
                }
            }
        }
        villager.setOffers(curOffers);
        updateTradeGui(villager, handler.syncId);
    }

    private void spawnIronGolem(ServerPlayerEntity player, VillagerEntity villager){
        ServerWorld world = player.getServerWorld();
        BlockPos.Mutable mutable = villager.getBlockPos().mutableCopy();
        IronGolemEntity entity = EntityType.IRON_GOLEM.create(world,
                                                              null,
                                                              null,
                                                              mutable,
                                                              SpawnReason.TRIGGERED,
                                                              false,
                                                              false);
        world.spawnEntityAndPassengers(entity);
        if (entity != null) entity.setAngryAt(player.getUuid());
    }

    private static void updateTradeGui(VillagerEntity villager, int syncInt){
        if (villager.getCustomer() != null && villager.getCustomer().currentScreenHandler instanceof MerchantScreenHandler && syncInt != 0){
            villager.getCustomer()
                    .sendTradeOffers(syncInt,
                                     villager.getOffers(),
                                     villager.getVillagerData().getLevel(),
                                     villager.getExperience(),
                                     villager.isLeveledMerchant(),
                                     villager.canRefreshTrades());
        }
    }


    /**
     * sendResponsePacket
     * Sends a response to the player
     *
     * @param response The response to send
     * @param player   The player to send the response to
     */
    private void sendResponsePacket(String response, ServerPlayerEntity player){
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(response);
        ServerPlayNetworking.send(player, VillagerTalkS2CNetworkingConstants.VILLAGER_RESPONSE_PACKET, buf);
    }

    public static void onVillagerTradeOpen(PlayerEntity player, VillagerEntity villager){
        if (!(player instanceof ServerPlayerEntity)){
            LOGGER.info("onVillagerTradeOpen: player {} is NOT serverPlayerEntity", player.getUuid());
            return;
        }

        ((VillagerEntityInvoker) villager).invokeClearSpecialPrices(); //clear vanilla discounts
        if (player.currentScreenHandler instanceof MerchantScreenHandler handler){ //update trade gui
            updateTradeGui(villager, handler.syncId);
        }

        activeVillagers.put(player.getUuid(), villager); //add villager to currently active villagers
    }
}