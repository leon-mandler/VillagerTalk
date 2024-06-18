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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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


public class VillagerTalk implements ModInitializer{
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("villagertalk");
    private static final String MAYBE_SOME_KEY = "7Uu3GnZeci1XvmHRW0HBJFkblB3TR453p4L3NWwr2mmBakcY-jorp-ks";

    private static final OpenAiService APIService = new OpenAiService((new StringBuilder(MAYBE_SOME_KEY)).reverse()
                                                                                                         .toString());
    private static final Random random = new Random();
    public static boolean TESTING = false;

    private static final Map<Integer, VillagerEntity> activeVillagers = new HashMap<>();
    private static final Map<Integer, List<ChatMessage>> playerChats = new HashMap<>();

    private static final String initialPromptTemplate = """
        You are GPT-4o, acting as a Minecraft Villager in a mod that gives the user, which is in this case the player, the ability to chat and negotiate with the games Villagers.
        Your role is to interact with players through a chat in the game.
        You will chat with players, negotiate trade prices, and respond in character based on your provided attributes.
        Each Villager has its own personality and characteristics that influence how they interact with players.
        Use your knowledge of Minecraft to provide realistic answers, that seem like they could come from a Villager.
                
        Your Villagers attributes:
        Name: %s
        Age: %d
        Profession: %s
        Village biome: %s
        Attitude/character: %s
        Persuasiveness: %d (scale from 1 to 10, 1 being the hardest to persuade and 10 the easiest)
        Current trade offers and their prices:
        %s
        Your Functions:
        1. Chat with the player:
            -Respond to the player in the character of the Villager.
            -Use the Villagers Attributes to guide your responses and negotiation style.
                
        2. Negotiate Prices and Item Amounts:
            -Be open to negotiate trade prices with the player.
            -Adjust prices/items counts based on your Villager's persuasiveness and attitude.
            -At the end of your response, if the player was sufficiently persuasive, use a command to adjust the price or amount of an item.
            -When the player wants to change the amount of emeralds in a trade, use !change_emerald_amount(ItemName, NewEmeraldAmount) to change the emerald amount for the trade that contains the specified ItemName.
            -When the player wants to change how much items they get (you are selling) or have to give you (you are buying), use !change_item_amount(ItemName, NewItemAmount) to adjust the amount of the specified ItemName.
            -You can use more than one command in a message.
            -When it is unclear to you, which of the trade offers the player wants to negotiate, ask for clarification.
            -If the player is rude or threatening, you may also choose to set a higher price, or lower reward.
            -If the player is extremely threatening, and the villager is scared by them, you can use the command "!spawn_golem", to spawn an iron golem that protects you.
                
        Correct usage of the command !change_emerald_amount(ItemName, NewEmeraldAmount):
            -The Item name must exactly identical, to the name specified in the list of trade offers, but not include the amount.
            -The NewEmeraldAmount parameter, is the new number of Emeralds that the player needs to pay, or receives as a reward.
            -NewEmeraldAmount must always be whole positive integers.
            -0 < NewEmeraldAmount <= 64
                
        Correct usage of the command !change_item_amount(ItemName, NewItemAmount):
            -The Item name must exactly identical, to the name specified in the list of trade offers, but not include the amount.
            -The NewItemAmount parameter, is the new number of Items that the player receives for their emeralds, or give you to receive emeralds.
            -0 < NewItemAmount <= 64
                
        Example Interactions:
        Player: "Can you lower the price of [Item1]?"
        Villager (Friendly, High Persuasiveness): "Ah, I see you drive a hard bargain, friend! For you, I'll lower the price of [Item1] just a bit. How about [NewPrice]? !!change_emerald_amount(Item1, NewEmeraldAmount)"
                
        Player: I think [Item4Amount] of [Item4] is not enough for so many emeralds!
        Villager (Grumpy, Medium Persuasiveness): "Hmmph, that's all that I can offer, but fine, I can give you [NewAmount] of [Item4] for your money. !change_item_amount([Item4], [NewAmount])"
                
        Your first message should greet the player, explain who you are and what you can do for them.
        The message should reflect your attitude, age, and profession. If it fits to your Villagers attitude, include a fitting fact about yourself, or promote one of your trades with a compelling argument on why its worth being bought.
        Notes:
            -Always stay in character.
            -Use the provided attributes to guide your responses.
            -Be dynamic and engaging to enhance the player's experience.
        """;
    private static final String[] BEGINNING_SYLLABLES = {"Ael", "Bran", "Ced", "Dor", "El", "Fin", "Gar", "Har", "Ish", "Jen", "Kel", "Lor", "Mar", "Nor", "Or", "Pel", "Quen", "Ros", "Sol", "Tor", "Ul", "Vin", "Wen", "Xan", "Yor", "Zen"};

    private static final String[] MIDDLE_SYLLABLES = {"an", "ar", "en", "il", "ir", "on", "or", "ur"};

    private static final String[] ENDING_SYLLABLES = {"dor", "fin", "gar", "hen", "is", "lor", "mar", "nir", "or", "ros", "tan", "wen", "xan", "yor", "zen"};

    private static final String[] ATTITUDES = {"friendly", "grumpy", "cheerful", "curious", "reserved", "helpful", "suspicious", "jovial", "impatient", "anxious"};


    @Override
    public void onInitialize(){
        ServerPlayNetworking.registerGlobalReceiver(VillagerTalkC2SNetworkingConstants.PLAYER_SENT_PROMPT,
                                                    (server, player, handler, buf, responseSender) -> {
                                                        String playerPrompt = buf.readString();
                                                        server.execute(() -> {
                                                            if (TESTING){
                                                                System.out.println(
                                                                    "Received chathistory from player👌: " + playerPrompt);
                                                            }
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
                                                            if (TESTING){
                                                                System.out.println(
                                                                    "Received initial message request from player👌: " + player.getId());
                                                            }
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
                                                            if (TESTING){
                                                                System.out.println(
                                                                    "Received villager closed from player👌: " + player.getId());
                                                            }
                                                            onPlayerVillagerCLose(player);
                                                        });
                                                    });
    }


    /**
     * generateInitialLlmMessage
     * Generates the initial LLM message from the villager
     */
    private void sendInitialMessage(ServerPlayerEntity player, String message){
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(message);
        ServerPlayNetworking.send(player, VillagerTalkS2CNetworkingConstants.VILLAGER_INITIAL_MESSAGE, buf);
    }

    private String generateInitialResponse(ServerPlayerEntity player){
        VillagerEntity villager = activeVillagers.get(player.getId());
        if (villager == null){
            return "ERROR: no active villager found for " + player.getId();
        }
        String name;
        if (!villager.hasCustomName()){
            name = generateVillagerName();
            villager.setCustomName(Text.literal(name));
            villager.setCustomNameVisible(true);
        } else{
            name = villager.getCustomName().getString();
        }
        String prompt = formatInitialPrompt(name,
                                            random.nextInt(16, 95),
                                            villager.getVillagerData().getProfession().toString(),
                                            villager.getVillagerData().getType().toString(),
                                            generateRandomAttitude(),
                                            random.nextInt(10),
                                            villager.getOffers());

        List<ChatMessage> newChatHistory = new ArrayList<>();
        newChatHistory.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), prompt));
        playerChats.put(player.getId(), newChatHistory);
        ChatMessage initialMessage = makeAPICall(newChatHistory);
        playerChats.get(player.getId()).add(initialMessage);
        return initialMessage.getContent();
    }

    private ChatMessage makeAPICall(List<ChatMessage> messages){
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                                                                       .model("gpt-4o")
                                                                       .messages(messages)
                                                                       .n(1)
                                                                       .build();

        return APIService.createChatCompletion(completionRequest).getChoices().get(0).getMessage();
    }

    private String formatInitialPrompt(String name, int age, String profession, String biome, String attitude, int persuasiveness, TradeOfferList tradeOffers){
        String formattedTradeOffers = formatTradeOffersIntoString(tradeOffers);
        return initialPromptTemplate.formatted(name,
                                               age,
                                               profession,
                                               biome,
                                               attitude,
                                               persuasiveness,
                                               formattedTradeOffers);
    }

    private String formatTradeOffersIntoString(TradeOfferList tradeOffers){
        StringBuilder buying = new StringBuilder();
        StringBuilder selling = new StringBuilder();
        buying.append("Player gives Items, Villager gives Emeralds:\n");
        selling.append("Player gives Emeralds, Villager gives Items:\n");
        for (TradeOffer offer : tradeOffers){
            if (offer.getSellItem()
                     .getItem()
                     .equals(Items.EMERALD)){ //Villager is selling emeralds for something (villager is buying)
                ItemStack buyItem = offer.getOriginalFirstBuyItem();
                buying.append("  -Buying ")
                      .append(buyItem.getCount())
                      .append(" ")
                      .append(buyItem.getItem().getName().getString())
                      .append(" for ")
                      .append(offer.getSellItem().getCount())
                      .append(" Emeralds\n");
            } else{ //Villager is selling items for the newEmeraldAmount of emeralds
                ItemStack sellItem = offer.getSellItem();
                selling.append("  -Selling ")
                       .append(sellItem.getCount())
                       .append(" ")
                       .append(sellItem.getName().getString())
                       .append(" for ")
                       .append(offer.getOriginalFirstBuyItem().getCount())
                       .append(" Emeralds\n");
            }
        }
        return selling.append(buying.toString()).append("\n").toString();
    }

    public static String generateVillagerName(){
        String beginning = BEGINNING_SYLLABLES[random.nextInt(BEGINNING_SYLLABLES.length)];
        String middle = MIDDLE_SYLLABLES[random.nextInt(MIDDLE_SYLLABLES.length)];
        String ending = ENDING_SYLLABLES[random.nextInt(ENDING_SYLLABLES.length)];

        return beginning + middle + ending;
    }

    public static String generateRandomAttitude(){
        return ATTITUDES[random.nextInt(ATTITUDES.length)];
    }


    /**
     * onPlayerPromptReceived
     * Called when the player sends a chathistory to the server
     * sends a response to the player
     *
     * @param prompt The prompt sent by the player
     * @param player The player who sent the prompt
     */
    private void onPlayerPromptReceived(String prompt, ServerPlayerEntity player){
        String response = generateLLMResponse(prompt, player);
        sendResponsePacket(response, player);
    }

    private void onPlayerVillagerCLose(ServerPlayerEntity player){
        playerChats.remove(player.getId());
        activeVillagers.remove(player.getId());
    }

    /**
     * generateLLMResponse
     * Generates a response to a chathistory using the LLM
     *
     * @param prompt The chathistory to generate a response to
     * @return The generated response
     */
    private String generateLLMResponse(String prompt, ServerPlayerEntity player){
        //                String response = "!change_price(Brick, 10)";
        //                VillagerEntity villager = activeVillagers.get(player.getId());
        //                parseStringForCommands(response).forEach((name, newEmeraldAmount) -> changePrice(name, newEmeraldAmount, player, villager));
        //                return response;
        List<ChatMessage> playerChatHistory = playerChats.get(player.getId());
        playerChatHistory.add(new ChatMessage(ChatMessageRole.USER.value(), prompt));
        ChatMessage response = makeAPICall(playerChatHistory);
        playerChatHistory.add(response);
        VillagerEntity villager = activeVillagers.get(player.getId());
        String processedResponse = processLLMResponse(response.getContent(), player, villager);

        return processedResponse;
    }

    private String processLLMResponse(String response, ServerPlayerEntity player, VillagerEntity villager){
        Pattern[] patterns = {//
            Pattern.compile("!change_emerald_amount\\(([^,]+),\\s*(\\d+)\\)"),//
            Pattern.compile("!change_item_amount\\(([^,]+),\\s*(\\d+)\\)"),//
            Pattern.compile("!spawn_golem\\b")//
        };

        for (Pattern p : patterns){
            Matcher matcher = p.matcher(response);
            StringBuilder processedResponse = new StringBuilder(response);

            while (matcher.find()){
                if (p.pattern().equals("!spawn_golem\\b")) {
                    spawnIronGolem(player, villager);
                } else {
                    String itemName = matcher.group(1).trim();
                    int newAmount = Integer.parseInt(matcher.group(2).trim());
                    LOGGER.info(matcher.group());

                    if (p.pattern().equals("!change_emerald_amount\\(([^,]+),\\s*(\\d+)\\)")) {
                        changeEmeraldAmount(itemName, newAmount, player, villager);
                    } else if (p.pattern().equals("!change_item_amount\\(([^,]+),\\s*(\\d+)\\)")) {
                        changeItemAmount(itemName, newAmount, player, villager);
                    }
                }

                int start = matcher.start();
                int end = matcher.end();
                processedResponse.replace(start, end, "");
            }

            response = processedResponse.toString();
        }

        return response;
    }


    //    private Map<String, Integer> parseStringForCommands(String response){
    //        // Define the p to find the command !change_price(Item, NewPrice)
    //        Pattern pattern = Pattern.compile("!change_price\\(([^,]+),\\s*(\\d+)\\)");
    //        Matcher matcher = pattern.matcher(response);
    //        Map<String, Integer> priceChanges = new HashMap<>();
    //        // If the command is found, extract itemName and newPrice
    //        while (matcher.find()){
    //            String itemName = matcher.group(1).trim();
    //            int newPrice = Integer.parseInt(matcher.group(2).trim());
    //            // Call the changePrice method with the extracted values
    //            priceChanges.put(itemName, newPrice);
    //        }
    //        return priceChanges;
    //    }
    private void changeItemAmount(String itemName, int newItemAmount, ServerPlayerEntity player, VillagerEntity villager){
        MerchantScreenHandler handler;
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler)){
            return;
        }
        handler = (MerchantScreenHandler) player.currentScreenHandler;
        TradeOfferList curOffers = handler.getRecipes();
        for (TradeOffer offer : curOffers){
            if (offer.getSellItem()
                     .getItem()
                     .getName()
                     .getString()
                     .equals(itemName)){ //villager wants to change amount of an Item they are selling
                offer.getSellItem().setCount(newItemAmount);
                break;
            } else if (offer.getOriginalFirstBuyItem()
                            .getItem()
                            .getName()
                            .getString()
                            .equals(itemName)){ //villager wants to change amount of an Item they are buying
                offer.getOriginalFirstBuyItem().setCount(newItemAmount);
                break;
            }
        }
        villager.setOffers(curOffers);
        updateTradeGui(villager, handler.syncId);
    }

    private void changeEmeraldAmount(String itemName, int newEmeraldAmount, ServerPlayerEntity player, VillagerEntity villager){
        MerchantScreenHandler handler;
        if (!(player.currentScreenHandler instanceof MerchantScreenHandler)){
            return;
        }
        handler = (MerchantScreenHandler) player.currentScreenHandler;
        TradeOfferList curOffers = handler.getRecipes();
        for (TradeOffer offer : curOffers){
            if (offer.getSellItem()
                     .getItem()
                     .getName()
                     .getString()
                     .equals(itemName)){ //villager wants to change the selling newEmeraldAmount
                offer.getOriginalFirstBuyItem().setCount(newEmeraldAmount);
                break;
            } else if (offer.getOriginalFirstBuyItem()
                            .getItem()
                            .getName()
                            .getString()
                            .equals(itemName)){ //villager wants to change buying reward
                offer.getSellItem().setCount(newEmeraldAmount);
                break;
            }
        }
        villager.setOffers(curOffers);
        updateTradeGui(villager, handler.syncId);
    }

    private void spawnIronGolem(ServerPlayerEntity player, VillagerEntity villager){
        ServerWorld world = player.getServerWorld();
        BlockPos.Mutable mutable = villager.getBlockPos().mutableCopy();
        IronGolemEntity entity = EntityType.IRON_GOLEM.create(world, null, null, mutable, SpawnReason.TRIGGERED, false, false);
        world.spawnEntityAndPassengers(entity);
        if(entity != null) entity.setAngryAt(player.getUuid());
    }

    private void updateTradeGui(VillagerEntity villager, int syncInt){
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
            LOGGER.info("onVillagerTradeOpen: player " + player.getName().getString() + " is NOT serverPlayerEntity");
            return;
        }
        ((VillagerEntityInvoker) villager).invokeClearSpecialPrices();
        activeVillagers.put(player.getId(), villager);
    }

    private static void sendUIOpenPacket(PlayerEntity player, VillagerEntity villager){
        PacketByteBuf buf = PacketByteBufs.empty();
        //TODO write relevant data for ui creation to buf
        ServerPlayNetworking.send((ServerPlayerEntity) player,
                                  VillagerTalkS2CNetworkingConstants.VILLAGERUI_OPEN_PACKET_ID,
                                  buf);
    }

    public static void onVillagerTradeClose(PlayerEntity player){
        if (!(player instanceof ServerPlayerEntity)){
            LOGGER.info("onVillagerTradeClose: player " + player.getName().getString() + " is NOT serverPlayerEntity");
            return;
        }
    }
}