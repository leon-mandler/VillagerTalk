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

public final class VillagerChatData{
    private List<ChatMessage> chatHistory;
    private final VillagerEntity villager;
    private final UUID villagerID;
    private final UUID playerID;
    private String systemPrompt;
    private final String villagerName;
    private final int age;
    private final String attitude;
    private String profession;
    private String biome;
    private final int persuasiveness;

    private final Random random = new Random();

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

    private String formatSystemPrompt(){
        return SYSTEM_PROMPT_TEMPLATE.formatted(this.villagerName,
                                                this.age,
                                                this.profession,
                                                this.biome,
                                                this.attitude,
                                                this.persuasiveness,
                                                formatTradeOffersIntoString(this.villager.getOffers()));
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
        return selling.append(buying).append("\n").toString();
    }

    private String generateVillagerName(){
        String beginning = BEGINNING_SYLLABLES[random.nextInt(BEGINNING_SYLLABLES.length)];
        String middle = MIDDLE_SYLLABLES[random.nextInt(MIDDLE_SYLLABLES.length)];
        String ending = ENDING_SYLLABLES[random.nextInt(ENDING_SYLLABLES.length)];

        return beginning + middle + ending;
    }

    @Override
    public String toString(){
        return "VillagerChatData[" + "chatHistory=" + chatHistory + ", " + "villager=" + villager + ", " + "playerID=" + playerID + ", " + "systemPrompt=" + systemPrompt + ']';
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

    public String getChatHistoryAsString(){
        StringBuilder s = new StringBuilder();
        for (int i = 1, length = chatHistory.size(); i < length; i++){
            ChatMessage c = chatHistory.get(i);
            s.append((i % 2 != 0) ? villagerName + ":\n" : "Player:\n").append(c.getContent()).append("\n\n");
        }
        return removeCommandsFromChatHistory(s.toString());
    }

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

    public List<ChatMessage> freshChatHistory(){
        chatHistory = new ArrayList<>();
        this.profession = villager.getVillagerData().getProfession().toString();
        this.biome = villager.getVillagerData().getType().toString();

        this.systemPrompt = formatSystemPrompt();

        chatHistory.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), this.systemPrompt));
        return chatHistory;
    }

    public void refreshSystemPrompt(){
        this.profession = villager.getVillagerData().getProfession().toString();
        this.biome = villager.getVillagerData().getType().toString();
        this.systemPrompt = formatSystemPrompt();
        this.chatHistory.get(0).setContent(systemPrompt);
    }

    public void addChatMessage(ChatMessage message){
        chatHistory.add(message);
    }

    @Override
    public boolean equals(Object obj){
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (VillagerChatData) obj;
        return Objects.equals(this.chatHistory, that.chatHistory) && Objects.equals(this.villager,
                                                                                    that.villager) && this.playerID == that.playerID && Objects.equals(
            this.systemPrompt,
            that.systemPrompt) && this.age == that.age && Objects.equals(this.attitude,
                                                                         that.attitude) && this.persuasiveness == that.persuasiveness;
    }

    @Override
    public int hashCode(){
        return Objects.hash(chatHistory, villager, playerID, systemPrompt, age, attitude, persuasiveness);
    }

    private static final String[] BEGINNING_SYLLABLES = {"Ael", "Bran", "Ced", "Dor", "El", "Fin", "Gar", "Har", "Ish", "Jen", "Kel", "Lor", "Mar", "Nor", "Or", "Pel", "Quen", "Ros", "Sol", "Tor", "Ul", "Vin", "Wen", "Xan", "Yor", "Zen"};

    private static final String[] MIDDLE_SYLLABLES = {"an", "ar", "en", "il", "ir", "on", "or", "ur"};

    private static final String[] ENDING_SYLLABLES = {"dor", "fin", "gar", "hen", "is", "lor", "mar", "nir", "or", "ros", "tan", "wen", "xan", "yor", "zen"};

    private static final String[] ATTITUDES = {"friendly", "grumpy", "cheerful", "curious", "reserved", "helpful", "suspicious", "jovial", "impatient", "anxious"};

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

    UUID getVillagerID(){
        return villagerID;
    }
}
