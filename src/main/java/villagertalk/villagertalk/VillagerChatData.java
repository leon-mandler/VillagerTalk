package villagertalk.villagertalk;

import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VillagerChatData holds the data of a VillagerEntity and the chat history of the player and the villager.
 * Also generates the system prompt that is displayed to the player when they start chatting with the villager, and contains various String formatting methods.
 */
public final class VillagerChatData{
    /**
     * The chat history of the player and the villager.
     */
    private List<ChatMessage> chatHistory;
    /**
     * The VillagerEntity that the player is chatting with.
     */
    private final VillagerEntity villager;
    /**
     * The UUID of the VillagerEntity.
     */
    private final UUID villagerID;
    /**
     * The UUID of the player that is chatting with the villager.
     */
    private final UUID playerID;
    /**
     * The system prompt that is displayed to the player when they start chatting with the villager.
     */
    private String systemPrompt;
    private final String villagerName;
    private final int age;
    private final String attitude;
    private String profession;
    private String biome;
    private final int persuasiveness;

    private final Random random = new Random();

    /**
     * Constructs a new VillagerChatData instance for the given villager and player.
     *
     * @param villager The VillagerEntity instance representing the villager.
     * @param playerID The UUID of the player interacting with the villager.
     *                 <p>
     *                 Initializes various fields related to the villager and player interaction:
     *                 <ul>
     *                     <li>{@link VillagerChatData#villager} is set to the provided villager.</li>
     *                     <li>{@link VillagerChatData#villagerID} is set to the UUID of the villager.</li>
     *                     <li>{@link VillagerChatData#playerID} is set to the provided player ID.</li>
     *                     <li>{@link VillagerChatData#profession} is set to the villager's profession as a string.</li>
     *                     <li>{@link VillagerChatData#biome} is set to the villager's biome type as a string.</li>
     *                 </ul>
     *                 <p>
     *                 If the villager does not have a custom name, a new name is generated using
     *                 {@link VillagerChatData#generateVillagerName()} and set to the villager.
     *                 The villager's name is stored in {@link VillagerChatData#villagerName}.
     *                 <p>
     *                 The following fields are initialized with random values:
     *                 <ul>
     *                     <li>{@link VillagerChatData#age} is set to a random integer between 16 and 95.</li>
     *                     <li>{@link VillagerChatData#attitude} is set to a random value from
     *                         the ATTITUDES array.</li>
     *                     <li>{@link VillagerChatData#persuasiveness} is set to a random integer
     *                         between 1 and 10.</li>
     *                 </ul>
     *                 <p>
     *                 Finally, the {@link VillagerChatData#chatHistory} is initialized with a new
     *                 chat history using {@link VillagerChatData#freshChatHistory()}.
     */
    public VillagerChatData(VillagerEntity villager, UUID playerID){
        this.villager = villager;
        this.villagerID = villager.getUuid();
        this.playerID = playerID;
        this.profession = villager.getVillagerData().getProfession().toString();
        this.biome = villager.getVillagerData().getType().toString();

        if (!villager.hasCustomName()){
            villagerName = generateVillagerName();
            villager.setCustomName(Text.literal(villagerName));
            villager.setCustomNameVisible(true);
        } else{
            villagerName = villager.getCustomName().getString();
        }

        this.age = random.nextInt(16, 95);
        this.attitude = ATTITUDES[random.nextInt(ATTITUDES.length)];
        this.persuasiveness = random.nextInt(1, 10);
        this.chatHistory = freshChatHistory();
    }

    /**
     * Formats the system prompt string using the villager's attributes.
     * <p>
     * This method uses {@link  VillagerChatData#SYSTEM_PROMPT_TEMPLATE} to create a formatted
     * string that includes the villager's name, age, profession, biome, attitude,
     * persuasiveness, and trade offers.
     *
     * @return A formatted string that represents the system prompt, populated
     * with the villager's current attributes and trade offers.
     * <p>
     * The formatted string includes:
     * <ul>
     *     <li>The villager's name ({@link #villagerName}).</li>
     *     <li>The villager's age ({@link #age}).</li>
     *     <li>The villager's profession ({@link #profession}).</li>
     *     <li>The villager's biome ({@link #biome}).</li>
     *     <li>The villager's attitude ({@link #attitude}).</li>
     *     <li>The villager's persuasiveness ({@link #persuasiveness}).</li>
     *     <li>The villager's trade offers, formatted as a string using
     *         {@link VillagerChatData#formatTradeOffersIntoString(TradeOfferList)}.</li>
     * </ul>
     */
    private String formatSystemPrompt(){
        return SYSTEM_PROMPT_TEMPLATE.formatted(this.villagerName,
                                                this.age,
                                                this.profession,
                                                this.biome,
                                                this.attitude,
                                                this.persuasiveness,
                                                formatTradeOffersIntoString(this.villager.getOffers()));
    }

    /**
     * Formats the trade offers into a string for display in the system prompt.
     * <p>
     * This method takes a {@link TradeOfferList} of trade offers and formats them
     * into a string that represents the trade offers in a readable format.
     *
     * @param tradeOffers The list of trade offers to format.
     * @return A formatted string that represents the trade offers in the list.
     * <p>
     * The formatted string includes:
     * <ul>
     *     <li>A list of trade offers where the villager is selling items for emeralds.</li>
     *     <li>A list of trade offers where the villager is buying items with emeralds.</li>
     * </ul>
     */
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
        return selling.append(buying).append("\n").toString();
    }

    /**
     * Generates a random name for the villager.
     * <p>
     * This method generates a random name for the villager using a combination of
     * beginning, middle, and ending syllables from the {@link VillagerChatData#BEGINNING_SYLLABLES},
     * {@link VillagerChatData#MIDDLE_SYLLABLES}, and {@link VillagerChatData#ENDING_SYLLABLES} arrays.
     *
     * @return A randomly generated name.
     */
    private String generateVillagerName(){
        String beginning = BEGINNING_SYLLABLES[random.nextInt(BEGINNING_SYLLABLES.length)];
        String middle = MIDDLE_SYLLABLES[random.nextInt(MIDDLE_SYLLABLES.length)];
        String ending = ENDING_SYLLABLES[random.nextInt(ENDING_SYLLABLES.length)];

        return beginning + middle + ending;
    }

    public List<ChatMessage> chatHistory(){
        return chatHistory;
    }

    public VillagerEntity villager(){
        return villager;
    }

    public UUID playerID(){
        return playerID;
    }

    public String systemPrompt(){
        return systemPrompt;
    }

    public int age(){
        return age;
    }

    public String attitude(){
        return attitude;
    }

    public int persuasiveness(){
        return persuasiveness;
    }

    public String getVillagerName(){
        return villagerName;
    }

    UUID getVillagerID(){
        return villagerID;
    }

    /**
     * Formats {@link VillagerChatData#chatHistory} as a string, formatted for display in the chat UI.
     *
     * @return A formatted string that represents the chat history.
     */
    public String getChatHistoryAsString(){
        StringBuilder s = new StringBuilder();
        for (int i = 1, length = chatHistory.size(); i < length; i++){
            ChatMessage c = chatHistory.get(i);
            s.append((i % 2 != 0) ? villagerName + ":\n" : "Player:\n").append(c.getContent()).append("\n\n");
        }
        return removeCommandsFromChatHistory(s.toString());
    }

    /**
     * Removes commands from the chat history string, by replacing them with an empty string.
     * Uses the {@link VillagerTalk#COMMANDS} list of patterns to match and remove commands.
     *
     * @param chat The chat history string to process.
     * @return The chat history string with commands removed.
     */
    private String removeCommandsFromChatHistory(String chat){
        StringBuilder processedResponse = new StringBuilder(chat);
        for (Pattern p : VillagerTalk.COMMANDS){
            Matcher matcher = p.matcher(chat);
            while (matcher.find()){
                int start = matcher.start();
                int end = matcher.end();
                processedResponse.replace(start, end, "");
            }
        }
        return processedResponse.toString();
    }

    /**
     * Clears and reinitializes the chat history with a refreshed system prompt.
     *
     * @return A list of chat messages representing the fresh chat history.
     */
    public List<ChatMessage> freshChatHistory(){
        chatHistory = new ArrayList<>();
        this.profession = villager.getVillagerData().getProfession().toString();
        this.biome = villager.getVillagerData().getType().toString();

        this.systemPrompt = formatSystemPrompt();

        chatHistory.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), this.systemPrompt));
        return chatHistory;
    }

    /**
     * Refreshes the system prompt with the current villager attributes.
     */
    public void refreshSystemPrompt(){
        this.profession = villager.getVillagerData().getProfession().toString();
        this.biome = villager.getVillagerData().getType().toString();
        this.systemPrompt = formatSystemPrompt();
        this.chatHistory.get(0).setContent(systemPrompt);
    }

    /**
     * Adds a {@link ChatMessage} to the {@link VillagerChatData#chatHistory}
     *
     * @param message The {@link ChatMessage} to add to the chat history.
     */
    public void addChatMessage(ChatMessage message){
        chatHistory.add(message);
    }

    private static final String[] BEGINNING_SYLLABLES = {"Ael", "Bran", "Ced", "Dor", "El", "Fin", "Gar", "Har", "Ish", "Jen", "Kel", "Lor", "Mar", "Nor", "Or", "Pel", "Quen", "Ros", "Sol", "Tor", "Ul", "Vin", "Wen", "Xan", "Yor", "Zen"};

    private static final String[] MIDDLE_SYLLABLES = {"an", "ar", "en", "il", "ir", "on", "or", "ur"};

    private static final String[] ENDING_SYLLABLES = {"dor", "fin", "gar", "hen", "is", "lor", "mar", "nir", "or", "ros", "tan", "wen", "xan", "yor", "zen"};

    /**
     * The list of possible attitudes that a villager can have.
     */
    private static final String[] ATTITUDES = {"friendly", "grumpy", "cheerful", "curious", "reserved", "helpful", "suspicious", "jovial", "impatient", "anxious"};

    /**
     * The system prompt template that used for the system prompt of the LLM.
     * Can be formatted with the villager's attributes to create an individual system prompt.
     */
    public static final String SYSTEM_PROMPT_TEMPLATE = """
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
            -Respond to the player in the character of the villager.
            -Use the villagers attributes to guide your responses and negotiation style.
            -Never put the villagers response in quotation marks.
            -You are allowed to use '*' to represent actions of the villager, but never use it for narration.
        
        2. Negotiate Prices and Item Amounts:
            -Adjust prices/items counts based on your villager's persuasiveness and attitude.
            -At the end of your response, if the player was sufficiently persuasive, use a command to adjust the price or amount of an item.
            -When the player wants to change the amount of emeralds in a trade, use !change_emerald_amount(ItemName, NewEmeraldAmount) to change the emerald amount for the trade that contains the specified ItemName.
            -When the player wants to change how many items they get (you are selling) or have to give you (you are buying), use !change_item_amount(ItemName, NewItemAmount) to adjust the amount of the specified ItemName.
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
}
