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

    /**
     * The OpenAI API service used to generate responses
     */
    private static final OpenAiService APIService = new OpenAiService((new StringBuilder(MAYBE_SOME_KEY)).reverse()
                                                                                                         .toString());
    public static boolean TESTING = false;

    /**
     * All villagers that are currently active. Used to get the corresponding villager when a client sends a request.
     */
    private static final Map<UUID, VillagerEntity> activeVillagers = new HashMap<>();

    /**
     * All previous chats from the current session. Used to keep track of the chat history for each villager.
     */
    private static final List<VillagerChatData> previousChats = new ArrayList<>();


    public static final Pattern CHANGE_EMERALD_AMOUNT = Pattern.compile("!change_emerald_amount\\(([^,]+),\\s*(\\d+)\\)");
    public static final Pattern CHANGE_ITEM_AMOUNT = Pattern.compile("!change_item_amount\\(([^,]+),\\s*(\\d+)\\)");
    public static final Pattern SPAWN_GOLEM = Pattern.compile("!spawn_golem\\b");

    public static final Pattern[] COMMANDS = {CHANGE_EMERALD_AMOUNT, CHANGE_ITEM_AMOUNT, SPAWN_GOLEM};

    /**
     * Entry point for the mod, called when the game is initialized.
     * <p>
     *     Registers the networking for the mod. Global receivers for the player's prompt, the initial villager message request, and the villager closed packet.
     */
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

    /**
     * Sends the initial message of the villager to the player.
     *
     * @param player  The player to send the message to
     * @param message The message to send
     */
    private void sendInitialMessage(ServerPlayerEntity player, String message){
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(message);
        ServerPlayNetworking.send(player, VillagerTalkS2CNetworkingConstants.VILLAGER_INITIAL_MESSAGE, buf);
    }

    /**
     * Generates the initial response for a player.
     * Uses {@link VillagerTalk#activeVillagers} to get the current actuve villager for the player.
     * If the player has an existing chat, it will refresh the system prompt and return the existing chat history.
     * Uses {@link VillagerTalk#makeAPICall(List)} to generate the initial message.
     * <p>
     * If the existing chat is too large to be sent (>= 32_000 byte), a new chat will be started.
     *
     * @param player The player that opened the villager
     * @return The initial response
     */
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

    /**
     * Finds an existing chat for a player with a villager.
     * Returns null if no chat is found.
     *
     * @param villagerID The UUID of the villager
     * @param playerID   The UUID of the player
     * @return The existing chat, or null if no chat is found
     */
    private VillagerChatData findExistingChat(UUID villagerID, UUID playerID){
        for (VillagerChatData v : previousChats){
            if (v.getVillagerID().equals(villagerID) && v.playerID().equals(playerID)){
                return v;
            }
        }
        return null;
    }

    /**
     * Makes an API call to the OpenAI API to generate a response to a list of messages, or, if it is a new chat, to generate the initial message.
     *
     * @param messages The messages to generate a response to
     * @return The generated response as a {@link ChatMessage}
     */
    private ChatMessage makeAPICall(List<ChatMessage> messages){
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                                                                       .model("gpt-4o")
                                                                       .messages(messages)
                                                                       .n(1)
                                                                       .build();

        return APIService.createChatCompletion(completionRequest).getChoices().get(0).getMessage();
    }


    /**
     * Called when a player closes a villager.
     * Removes the player from {@link VillagerTalk#activeVillagers}
     *
     * @param player The player that closed the villager
     */
    private void onPlayerVillagerCLose(ServerPlayerEntity player){
        activeVillagers.remove(player.getUuid());
    }

    /**
     * Generates a response to a chathistory using {@link VillagerTalk#makeAPICall(List)}
     * Uses {@link VillagerTalk#findExistingChat(UUID villager, UUID player)} to find the existing {@link VillagerChatData} for the player and villager.
     * Refreshes the system prompt and adds the player's prompt to the chat history.
     * Then generates a response and adds it to the chat history.
     *
     * @param prompt The prompt to generate a response to
     * @param player The {@link ServerPlayerEntity} that sent the prompt
     * @return The formatted generated response or an error message if no existing chat is found.
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

    /**
     * Processes the response from the OpenAI API.
     * <p>
     *     Processes the response to check for any commands, such as changing the amount of emeralds or items, or spawning an iron golem.
     *     If a command is found, it calls {@link VillagerTalk#spawnIronGolem(ServerPlayerEntity, VillagerEntity)},
     *     {@link VillagerTalk#changeAmount(String itemName, int newAmount, ServerPlayerEntity, VillagerEntity, boolean true)}, or
     *     {@link VillagerTalk#changeAmount(String itemName, int newAmount, ServerPlayerEntity, VillagerEntity, boolean false)} and removes the command from the response.
     * </p>
     *
     * @param response The response to process
     * @param player The player that sent the prompt
     * @param villager The villager that the player is talking to
     * @return The processed response
     */
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

    /**
     * Changes the amount of an item or emeralds in a villager's trade offers.
     * <p>
     *     Uses {@link VillagerEntity#getOffers()} to get the current trade offers of the villager.
     *     Iterates through the trade offers and changes the amount of the item or emeralds.
     *     Then sets the new trade offers and updates the trade GUI using {@link VillagerTalk#updateTradeGui(VillagerEntity, int)}.
     * </p>
     *
     * @param itemName The name of the item in the trade to change the amount of
     * @param newAmount The new amount of the item
     * @param player The player that sent the command
     * @param villager The villager to change the trade offers of
     * @param changeEmeraldAmount true: changes the emerald amount in the trade with the item, false: changes the item amount in the trade with the item
     */
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

    /**
     * Spawns an iron golem at the villager's location.
     * <p>
     *     Uses {@link EntityType#IRON_GOLEM} to create a new iron golem entity at the villager's location.
     *     Spawns the iron golem in the world and sets the player as the target of the iron golem.
     * </p>
     *
     * @param player The player that should be targeted
     * @param villager The villager to spawn the iron golem at
     */
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

    /**
     * Updates the trade GUI of a villager.
     * <p>
     *     Refreshes the TradingGUI by sending the trade offers to the player.
     * </p>
     *
     * @param villager The villager to update the trade GUI of
     * @param syncInt The sync ID of the trade GUI
     */
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

    /**
     * Called when a player opens a villager trade GUI.
     * <p>
     *     Clears the special prices of the villager to remove vanilla discounts.
     *     Updates the trade GUI using {@link VillagerTalk#updateTradeGui(VillagerEntity, int)}.
     *     Adds the villager to {@link VillagerTalk#activeVillagers}.
     * </p>
     *
     * @param player The player that opened the trade GUI
     * @param villager The villager that the player is trading with
     */
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