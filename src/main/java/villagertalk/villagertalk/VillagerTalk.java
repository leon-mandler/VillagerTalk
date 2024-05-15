package villagertalk.villagertalk;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import villagertalk.villagertalk.VillagerTalkPackets.VillagerTalkC2SNetworkingConstants;
import villagertalk.villagertalk.VillagerTalkPackets.VillagerTalkS2CNetworkingConstants;
import villagertalk.villagertalk.mixin.MerchantScreenHandlerAccessor;
import villagertalk.villagertalk.mixin.VillagerEntityInvoker;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class VillagerTalk implements ModInitializer{
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("villagertalk");
    private static final OpenAiService APIService = new OpenAiService(
        "sk-proj-LpPnXvPW4zcF3iARsGtgT3BlbkFJHPV7wa5LDBYJuW9EfNAl");
    private static final Random random = new Random();
    public static boolean TESTING = false;

    private static final Map<Integer, VillagerEntity> activeVillagers = new HashMap<>();
    private static final Map<Integer, List<ChatMessage>> playerChats = new HashMap<>();

    private static final String initialPromptTemplate = """
        You are GPT-4, acting as a Minecraft Villager in a mod that gives the user, which is in this case the player, the ability to chat and negotiate with the games Villagers.
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
        
        2. Negotiate Prices:
            -Be open to negotiate trade prices with the player.
            -Adjust prices based on your Villager's persuasiveness and attitude.
            -At the end of your response, if the player was sufficiently persuasive, use the command !change_price(ItemName, NewPrice/NewReward) to adjust the price of an item.
            -When it is unclear to you, which of the trade offers the player wants to negotiate, ask for clarification.
            -If the player is rude or threatening, you may also choose to set a higher price, or lower reward.
        
        Correct usage of the command !change_price(ItemName, NewPrice/NewReward):
            -The Item name must exactly identical, to the name specified in the list of trade offers, but not include the amount.
            -The NewPrice/NewReward parameter, is the new number of Emeralds that the player needs to pay, or receives as a reward.
            -NewPrice/NewReward must always be whole positive integers.
        
        Example Interactions:
        Player: "Can you lower the price of [Item1]?"
        Villager (Friendly, High Persuasiveness): "Ah, I see you drive a hard bargain, friend! For you, I'll lower the price of [Item1] just a bit. How about [NewPrice]? !change_price(Item1, [NewPrice])"
        
        Player: I think [Item4] for [EmeraldReward4] Emeralds is too low!
        Villager (Grumpy, Medium Persuasiveness): "Hmmph, that's all that I can offer, but fine, I'll increase it to [NewReward] Emeralds. !change_price([Item4], [NewReward])"
        
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
                                                            onPlayerPromptReceived(playerPrompt, player);
                                                        });
                                                    });

        ServerPlayNetworking.registerGlobalReceiver(VillagerTalkC2SNetworkingConstants.INITIAL_VILLAGER_MESSAGE_REQUEST,
                                                    (server, player, handler, buf, responseSender) -> {
                                                        server.execute(() -> {
                                                            if (TESTING){
                                                                System.out.println(
                                                                    "Received initial message request from player👌: " + player.getId());

                                                            }
                                                            sendInitialMessage(player);
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
    private void sendInitialMessage(ServerPlayerEntity player){
        PacketByteBuf buf = PacketByteBufs.create();
        String initialResponse = generateInitialResponse(player);
        buf.writeString(initialResponse);
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
                                            villager.age,
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
            } else{ //Villager is selling items for the price of emeralds
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
//        String response = "!change_price(Brick, 10)";
//        VillagerEntity villager = activeVillagers.get(player.getId());
//        parseStringForCommands(response).forEach((name, price) -> changePrice(name, price, player, villager));
//        return response;
        List<ChatMessage> playerChatHistory = playerChats.get(player.getId());
        playerChatHistory.add(new ChatMessage(ChatMessageRole.USER.value(), prompt));
        ChatMessage response = makeAPICall(playerChatHistory);
        playerChatHistory.add(response);
        VillagerEntity villager = activeVillagers.get(player.getId());
        parseStringForCommands(response.getContent()).forEach((name, price) -> changePrice(name, price, player, villager));
        return response.getContent();
    }

    private Map<String, Integer> parseStringForCommands(String response){
        // Define the pattern to find the command !change_price(Item, NewPrice)
        Pattern pattern = Pattern.compile("!change_price\\(([^,]+),\\s*(\\d+)\\)");
        Matcher matcher = pattern.matcher(response);
        Map<String, Integer> priceChanges = new HashMap<>();
        // If the command is found, extract itemName and newPrice
        while (matcher.find()){
            String itemName = matcher.group(1).trim();
            int newPrice = Integer.parseInt(matcher.group(2).trim());
            // Call the changePrice method with the extracted values
            priceChanges.put(itemName, newPrice);
        }
        return priceChanges;
    }

    private void changePrice(String itemName, int price, ServerPlayerEntity player, VillagerEntity villager){
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
                     .equals(itemName)){ //villager wants to change the selling price
                offer.getOriginalFirstBuyItem().setCount(price);
                break;
            } else if (offer.getOriginalFirstBuyItem()
                            .getItem()
                            .getName()
                            .getString()
                            .equals(itemName)){ //villager wants to change buying reward
                offer.getSellItem().setCount(price);
                break;
            }
        }
        handler.setCanRefreshTrades(true);
        handler.setOffers(curOffers);
        handler.sendContentUpdates();
        handler.updateToClient();
        ((MerchantScreenHandlerAccessor) handler).getMerchantInventory().updateOffers();
//        System.out.println(formatTradeOffersIntoString(handler.getRecipes()));
//        System.out.println(formatTradeOffersIntoString(villager.getOffers()));
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