package villagertalk.villagertalk.VillagerChatUI;

import net.minecraft.village.*;
import villagertalk.villagertalk.VillagerTalk;
import villagertalk.villagertalk.VillagerTalkPackets.VillagerTalkC2SNetworkingConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import villagertalk.villagertalk.VillagerTalkPackets.VillagerTalkS2CNetworkingConstants;

/**
 * ExtendedMerchantScreen extends the MerchantScreen with additional features for villager interaction.
 * This class is responsible for handling the UI elements and interactions for the villager trading screen.
 * It includes additional UI elements for the chat box and text field for player input.
 */
@Environment(value = EnvType.CLIENT)
public class ExtendedMerchantScreen extends HandledScreen<MerchantScreenHandler>{

    private static final Identifier OUT_OF_STOCK_TEXTURE = new Identifier("container/villager/out_of_stock");
    private static final Identifier EXPERIENCE_BAR_BACKGROUND_TEXTURE = new Identifier(
        "container/villager/experience_bar_background");
    private static final Identifier EXPERIENCE_BAR_CURRENT_TEXTURE = new Identifier(
        "container/villager/experience_bar_current");
    private static final Identifier EXPERIENCE_BAR_RESULT_TEXTURE = new Identifier(
        "container/villager/experience_bar_result");
    private static final Identifier SCROLLER_TEXTURE = new Identifier("container/villager/scroller");
    private static final Identifier SCROLLER_DISABLED_TEXTURE = new Identifier("container/villager/scroller_disabled");
    private static final Identifier TRADE_ARROW_OUT_OF_STOCK_TEXTURE = new Identifier(
        "container/villager/trade_arrow_out_of_stock");
    private static final Identifier TRADE_ARROW_TEXTURE = new Identifier("container/villager/trade_arrow");
    private static final Identifier DISCOUNT_STRIKETHROUGH_TEXTURE = new Identifier(
        "container/villager/discount_strikethrough");
    private static final Identifier TEXTURE = new Identifier("textures/gui/container/villager.png");
    private static final int TEXTURE_WIDTH = 1024;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int field_32356 = 99;
    private static final int XP_BAR_X_OFFSET = 136;
    private static final int TRADE_LIST_AREA_Y_OFFSET = 16;
    private static final int FIRST_BUY_ITEM_X_OFFSET = 5;
    private static final int SECOND_BUY_ITEM_X_OFFSET = 35;
    private static final int SOLD_ITEM_X_OFFSET = 68;
    private static final int field_32362 = 6;
    private static final int MAX_TRADE_OFFERS = 7;
    private static final int field_32364 = 5;
    private static final int TRADE_OFFER_BUTTON_HEIGHT = 20;
    private static final int TRADE_OFFER_BUTTON_WIDTH = 88;
    private static final int SCROLLBAR_HEIGHT = 27;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_AREA_HEIGHT = 139;
    private static final int SCROLLBAR_OFFSET_Y = 18;
    private static final int SCROLLBAR_OFFSET_X = 94;
    private static final Text TRADES_TEXT = Text.translatable((String) "merchant.trades");
    private static final Text DEPRECATED_TEXT = Text.translatable((String) "merchant.deprecated");
    private int selectedIndex;
    private final WidgetButtonPage[] offers = new WidgetButtonPage[7];
    int indexStartOffset;
    private boolean scrolling;

    /**
     * Field for the chat box, using {@link VillagerTalkScrollableChatWidget}
     */
    private final VillagerTalkScrollableChatWidget chatBox;

    /**
     * Field for the text field, using {@link TextFieldWidget}
     */
    private final TextFieldWidget writingField;

    /**
     * Static field to store the current open instance of the screen
     */
    private static ExtendedMerchantScreen currentInstance = null;

    /**
     * Constructs a new ExtendedMerchantScreen instance, extending the MerchantScreen with additional features for villager interaction.
     *
     * @param handler The {@link MerchantScreenHandler} instance to handle screen interactions. Must not be null.
     * @param inventory The {@link PlayerInventory} instance representing the player's inventory. Must not be null.
     * @param title The {@link Text} instance representing the screen title. Must not be null.
     *
     * This constructor initializes the ExtendedMerchantScreen with additional UI elements and settings:
     * <ul>
     *     <li>Initializes a {@link TextFieldWidget} for user input, positioned at (50, 325),
     *         with a placeholder text "Click to talk with villager" and a maximum length of 256 characters.</li>
     *     <li>Initializes a {@link VillagerTalkScrollableChatWidget} for displaying chat messages,
     *         positioned at (50, 50) with dimensions 256x256, and sets its initial message to "Thinking about initial Message...".</li>
     *     <li>Calls {@link #requestInitialMessageFromServer()} to request the initial chat message from the server.</li>
     * </ul>
     *
     * Additionally, sets the current instance of ExtendedMerchantScreen to this instance.
     */
    public ExtendedMerchantScreen(MerchantScreenHandler handler, PlayerInventory inventory, Text title){
        super(handler, inventory, title);
        this.backgroundWidth = 276;
        this.playerInventoryTitleX = 107;

        //VillagerTalk constructor
        //textField
        this.writingField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 256, 64, Text.literal(""));
        this.writingField.setEditable(false);
        this.writingField.setDrawsBackground(true);
        this.writingField.setMaxLength(256);
        this.writingField.setPlaceholder(Text.literal("Click to talk with villager")); //placeholder

        writingField.setPosition(50, 325);


        this.chatBox = new VillagerTalkScrollableChatWidget(50,
                                                            50,
                                                            256,
                                                            256,
                                                            Text.literal("Thinking about initial Message..."),
                                                            MinecraftClient.getInstance().textRenderer);
        requestInitialMessageFromServer();
        chatBox.visible = true;

        currentInstance = this;
    }


    /**
     * Static block to register the networking packets for the villager chat system
     */
    static{
        ClientPlayNetworking.registerGlobalReceiver(VillagerTalkS2CNetworkingConstants.VILLAGER_RESPONSE_PACKET,
                                                    ((client, handler2, buf, responseSender) -> {
                                                        String response = buf.readString();
                                                        if (currentInstance != null){
                                                            client.execute(() -> {
                                                                currentInstance.onVillagerResponseReceived(response);
                                                            });
                                                        }
                                                    }));

        ClientPlayNetworking.registerGlobalReceiver(VillagerTalkS2CNetworkingConstants.VILLAGER_INITIAL_MESSAGE,
                                                    ((client, handler2, buf, responseSender) -> {
                                                        String initialMessage = buf.readString();
                                                        if (currentInstance != null){
                                                            client.execute(() -> {
                                                                currentInstance.onInitialMessageReceived(initialMessage);
                                                            });
                                                        }
                                                    }));
    }

    /**
     * Overridden close method to ensure that the current instance is set to null
     */
    @Override
    public void close(){
        super.close();
        currentInstance = null;
        ClientPlayNetworking.send(VillagerTalkC2SNetworkingConstants.VILLAGER_CLOSED,
                                  PacketByteBufs.empty()); //send packet to server
    }

    /**
     * Called when the villager sends a response
     * parses the response and adds it to the chat history and chat box
     *
     * @param response the response from the villager
     */
    private void onVillagerResponseReceived(String response){
        addMessageToChatBox(response);
        writingField.setEditable(true);
    }

    /**
     * Adds a message to the chat box
     *
     * @param text the message to be added
     */
    private void addMessageToChatBox(String text){
        currentInstance.chatBox.setMessage(chatBox.getMessage().getString() + text);
        currentInstance.chatBox.setMaxScrollY();
    }

    /**
     * Requests the initial message from the server
     */
    private void requestInitialMessageFromServer(){
        ClientPlayNetworking.send(VillagerTalkC2SNetworkingConstants.INITIAL_VILLAGER_MESSAGE_REQUEST, PacketByteBufs.empty());
    }

    /**
     * Called when the initial message is received
     * sets the chat box message to the initial message and sets the text field to editable
     *
     * @param message the initial message received
     */
    private void onInitialMessageReceived(String message){
        currentInstance.writingField.setEditable(true);
        currentInstance.chatBox.setMessage(message);
        currentInstance.chatBox.setMaxScrollY();
    }

    /**
     * Overridden method to handle key presses
     * Handles the ESC key press to close the screen
     * Handles the 'E' key press to prevent exiting the window when typing
     * Handles the 'Enter' key press to send the player message
     * Handles the 'Enter' key press to set the text field to focused
     *
     * @param keyCode the key code of the key pressed
     * @param scanCode the scan code of the key pressed
     * @param modifiers the modifiers of the key pressed
     * @return true if the key press was handled locally, otherwise return the super method
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers){
        // Handle ESC key press
        if (keyCode == GLFW.GLFW_KEY_ESCAPE){
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        boolean writingFieldActive = currentInstance.writingField.isActive();

        if (writingFieldActive && keyCode == GLFW.GLFW_KEY_E){
            return true; // Prevent exiting the window when typing 'E'
        }

        if (writingFieldActive && keyCode == GLFW.GLFW_KEY_ENTER){
            currentInstance.sendPlayerMessage();
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (!writingFieldActive && keyCode == GLFW.GLFW_KEY_ENTER){
            currentInstance.writingField.setFocused(true);
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Called when the player sends a message
     * sets the writingField to not focused, clears the writingField, sets the writingField to not editable, adds the message to the chat box
     *
     * @param message the message sent by the player
     */
    private void onPlayerMessageSent(String message){
        writingField.setFocused(false);
        writingField.setText("");
        writingField.setEditable(false);
        addMessageToChatBox("Player: " + message + "\n\n");
    }

    /**
     * Sends a player message
     * gets the message from the writingField and calls {@link #onPlayerMessageSent(String)} with the message
     * then calls {@link #sendPlayerPromptPacket(String)} with the message.
     */
    private void sendPlayerMessage(){
        String message = writingField.getText(); //get test from textfield
        onPlayerMessageSent(message); //call to handle the text field
        sendPlayerPromptPacket(message);
    }

    /**
     * Sends a player prompt packet to the server
     *
     * @param message the message to be sent
     */
    private void sendPlayerPromptPacket(String message){
        PacketByteBuf buf = PacketByteBufs.create(); //create byte buf
        buf.writeString(message); //write the string to the buf
        ClientPlayNetworking.send(VillagerTalkC2SNetworkingConstants.PLAYER_SENT_PROMPT, buf); //send packet to server
    }
/*




    END OF VILLAGER TALK CODE





 */

    private void syncRecipeIndex(){
        ((MerchantScreenHandler) this.handler).setRecipeIndex(this.selectedIndex);
        ((MerchantScreenHandler) this.handler).switchTo(this.selectedIndex);
        this.client.getNetworkHandler().sendPacket((Packet<?>) new SelectMerchantTradeC2SPacket(this.selectedIndex));
    }

    @Override
    protected void init(){
        super.init();
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        int k = j + 16 + 2;
        for (int l = 0; l < 7; ++l){
            this.offers[l] = this.addDrawableChild(new WidgetButtonPage(i + 5, k, l, button -> {
                if (button instanceof WidgetButtonPage){
                    this.selectedIndex = ((WidgetButtonPage) button).getIndex() + this.indexStartOffset;
                    this.syncRecipeIndex();
                }
            }));
            k += 20;
        }
        this.addDrawableChild(writingField);
        this.addDrawableChild(chatBox);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY){
        int i = ((MerchantScreenHandler) this.handler).getLevelProgress();
        if (i > 0 && i <= 5 && ((MerchantScreenHandler) this.handler).isLeveled()){
            MutableText text = Text.translatable((String) "merchant.title",
                                                 (Object[]) new Object[]{this.title, Text.translatable((String) ("merchant.level." + i))});
            int j = this.textRenderer.getWidth((StringVisitable) text);
            int k = 49 + this.backgroundWidth / 2 - j / 2;
            context.drawText(this.textRenderer, (Text) text, k, 6, 0x404040, false);
        } else{
            context.drawText(this.textRenderer,
                             this.title,
                             49 + this.backgroundWidth / 2 - this.textRenderer.getWidth((StringVisitable) this.title) / 2,
                             6,
                             0x404040,
                             false);
        }
        context.drawText(this.textRenderer,
                         this.playerInventoryTitle,
                         this.playerInventoryTitleX,
                         this.playerInventoryTitleY,
                         0x404040,
                         false);
        int l = this.textRenderer.getWidth((StringVisitable) TRADES_TEXT);
        context.drawText(this.textRenderer, TRADES_TEXT, 5 - l / 2 + 48, 6, 0x404040, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY){
        int i = (this.width - this.backgroundWidth) / 2; // x offset
        int j = (this.height - this.backgroundHeight) / 2; // y offset
        context.drawTexture(TEXTURE, i, j, 0, 0.0f, 0.0f, this.backgroundWidth, this.backgroundHeight, 512, 256);
        TradeOfferList tradeOfferList = ((MerchantScreenHandler) this.handler).getRecipes();
        if (tradeOfferList.isEmpty()){
            return;
        }
        int k = this.selectedIndex;
        if (k < 0 || k >= tradeOfferList.size()){
            return;
        }
        TradeOffer tradeOffer = (TradeOffer) tradeOfferList.get(k);
        if (tradeOffer.isDisabled()){
            context.drawGuiTexture(OUT_OF_STOCK_TEXTURE, this.x + 83 + 99, this.y + 35, 0, 28, 21);
        }
    }

    private void drawLevelInfo(DrawContext context, int x, int y, TradeOffer tradeOffer){
        int i = ((MerchantScreenHandler) this.handler).getLevelProgress();
        int j = ((MerchantScreenHandler) this.handler).getExperience();
        if (i >= 5){
            return;
        }
        context.drawGuiTexture(EXPERIENCE_BAR_BACKGROUND_TEXTURE, x + 136, y + 16, 0, 102, 5);
        int k = VillagerData.getLowerLevelExperience((int) i);
        if (j < k || !VillagerData.canLevelUp((int) i)){
            return;
        }
        int l = 102;
        float f = 102.0f / (float) (VillagerData.getUpperLevelExperience((int) i) - k);
        int m = Math.min(MathHelper.floor((float) (f * (float) (j - k))), 102);
        context.drawGuiTexture(EXPERIENCE_BAR_CURRENT_TEXTURE, 102, 5, 0, 0, x + 136, y + 16, 0, m, 5);
        int n = ((MerchantScreenHandler) this.handler).getMerchantRewardedExperience();
        if (n > 0){
            int o = Math.min(MathHelper.floor((float) ((float) n * f)), 102 - m);
            context.drawGuiTexture(EXPERIENCE_BAR_RESULT_TEXTURE, 102, 5, m, 0, x + 136 + m, y + 16, 0, o, 5);
        }
    }

    private void renderScrollbar(DrawContext context, int x, int y, TradeOfferList tradeOffers){
        int i = tradeOffers.size() + 1 - 7;
        if (i > 1){
            int j = 139 - (27 + (i - 1) * 139 / i);
            int k = 1 + j / i + 139 / i;
            int l = 113;
            int m = Math.min(113, this.indexStartOffset * k);
            if (this.indexStartOffset == i - 1){
                m = 113;
            }
            context.drawGuiTexture(SCROLLER_TEXTURE, x + 94, y + 18 + m, 0, 6, 27);
        } else{
            context.drawGuiTexture(SCROLLER_DISABLED_TEXTURE, x + 94, y + 18, 0, 6, 27);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta){
        super.render(context, mouseX, mouseY, delta);
        TradeOfferList tradeOfferList = ((MerchantScreenHandler) this.handler).getRecipes();
        if (tradeOfferList.isEmpty()){
            this.drawMouseoverTooltip(context, mouseX, mouseY);
            return;
        }
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        int k = j + 16 + 1;
        int l = i + 5 + 5;
        this.renderScrollbar(context, i, j, tradeOfferList);
        int m = 0;
        for (TradeOffer tradeOffer : tradeOfferList){
            if (this.canScroll(tradeOfferList.size()) && (m < this.indexStartOffset || m >= 7 + this.indexStartOffset)){
                ++m;
                continue;
            }
            ItemStack itemStack = tradeOffer.getOriginalFirstBuyItem();
            ItemStack itemStack2 = tradeOffer.getAdjustedFirstBuyItem();
            ItemStack itemStack3 = tradeOffer.getSecondBuyItem();
            ItemStack itemStack4 = tradeOffer.getSellItem();
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, 0.0f, 100.0f);
            int n = k + 2;
            this.renderFirstBuyItem(context, itemStack2, itemStack, l, n);
            if (!itemStack3.isEmpty()){
                context.drawItemWithoutEntity(itemStack3, i + 5 + 35, n);
                context.drawItemInSlot(this.textRenderer, itemStack3, i + 5 + 35, n);
            }
            this.renderArrow(context, tradeOffer, i, n);
            context.drawItemWithoutEntity(itemStack4, i + 5 + 68, n);
            context.drawItemInSlot(this.textRenderer, itemStack4, i + 5 + 68, n);
            context.getMatrices().pop();
            k += 20;
            ++m;
        }
        int o = this.selectedIndex;
        TradeOffer t = (TradeOffer) tradeOfferList.get(o);
        if (((MerchantScreenHandler) this.handler).isLeveled()){
            this.drawLevelInfo(context, i, j, t);
        }
        if (t.isDisabled() && this.isPointWithinBounds(186,
                                                       35,
                                                       22,
                                                       21,
                                                       mouseX,
                                                       mouseY) && ((MerchantScreenHandler) this.handler).canRefreshTrades()){
            context.drawTooltip(this.textRenderer, DEPRECATED_TEXT, mouseX, mouseY);
        }
        for (WidgetButtonPage widgetButtonPage : this.offers){
            if (widgetButtonPage.isSelected()){
                widgetButtonPage.renderTooltip(context, mouseX, mouseY);
            }
            widgetButtonPage.visible = widgetButtonPage.index < ((MerchantScreenHandler) this.handler).getRecipes()
                                                                                                      .size();
        }
        RenderSystem.enableDepthTest();

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }


    private void renderArrow(DrawContext context, TradeOffer tradeOffer, int x, int y){
        RenderSystem.enableBlend();
        if (tradeOffer.isDisabled()){
            context.drawGuiTexture(TRADE_ARROW_OUT_OF_STOCK_TEXTURE, x + 5 + 35 + 20, y + 3, 0, 10, 9);
        } else{
            context.drawGuiTexture(TRADE_ARROW_TEXTURE, x + 5 + 35 + 20, y + 3, 0, 10, 9);
        }
    }

    private void renderFirstBuyItem(DrawContext context, ItemStack adjustedFirstBuyItem, ItemStack originalFirstBuyItem, int x, int y){
        context.drawItemWithoutEntity(adjustedFirstBuyItem, x, y);
        if (originalFirstBuyItem.getCount() == adjustedFirstBuyItem.getCount()){
            context.drawItemInSlot(this.textRenderer, adjustedFirstBuyItem, x, y);
        } else{
            context.drawItemInSlot(this.textRenderer,
                                   originalFirstBuyItem,
                                   x,
                                   y,
                                   originalFirstBuyItem.getCount() == 1 ? "1" : null);
            context.drawItemInSlot(this.textRenderer,
                                   adjustedFirstBuyItem,
                                   x + 14,
                                   y,
                                   adjustedFirstBuyItem.getCount() == 1 ? "1" : null);
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, 0.0f, 300.0f);
            context.drawGuiTexture(DISCOUNT_STRIKETHROUGH_TEXTURE, x + 7, y + 12, 0, 9, 2);
            context.getMatrices().pop();
        }
    }

    private boolean canScroll(int listSize){
        return listSize > 7;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount){
        int i = ((MerchantScreenHandler) this.handler).getRecipes().size();
        if (this.canScroll(i)){
            int j = i - 7;
            this.indexStartOffset = MathHelper.clamp((int) ((int) ((double) this.indexStartOffset - verticalAmount)),
                                                     (int) 0,
                                                     (int) j);
        }
        //chatbox scroll
        chatBox.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY){
        int i = ((MerchantScreenHandler) this.handler).getRecipes().size();
        if (this.scrolling){
            int j = this.y + 18;
            int k = j + 139;
            int l = i - 7;
            float f = ((float) mouseY - (float) j - 13.5f) / ((float) (k - j) - 27.0f);
            f = f * (float) l + 0.5f;
            this.indexStartOffset = MathHelper.clamp((int) ((int) f), (int) 0, (int) l);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button){
        this.scrolling = false;
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        if (this.canScroll(((MerchantScreenHandler) this.handler).getRecipes()
                                                                 .size()) && mouseX > (double) (i + 94) && mouseX < (double) (i + 94 + 6) && mouseY > (double) (j + 18) && mouseY <= (double) (j + 18 + 139 + 1)){
            this.scrolling = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Environment(value = EnvType.CLIENT)
    class WidgetButtonPage extends ButtonWidget{
        final int index;

        public WidgetButtonPage(int x, int y, int index, ButtonWidget.PressAction onPress){
            super(x, y, 88, 20, ScreenTexts.EMPTY, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.index = index;
            this.visible = false;
        }

        public int getIndex(){
            return this.index;
        }

        public void renderTooltip(DrawContext context, int x, int y){
            if (this.hovered && ((MerchantScreenHandler) ExtendedMerchantScreen.this.handler).getRecipes()
                                                                                             .size() > this.index + ExtendedMerchantScreen.this.indexStartOffset){
                if (x < this.getX() + 20){
                    ItemStack itemStack = ((TradeOffer) ((MerchantScreenHandler) ExtendedMerchantScreen.this.handler).getRecipes()
                                                                                                                     .get(
                                                                                                                         this.index + ExtendedMerchantScreen.this.indexStartOffset)).getAdjustedFirstBuyItem();
                    context.drawItemTooltip(ExtendedMerchantScreen.this.textRenderer, itemStack, x, y);
                } else if (x < this.getX() + 50 && x > this.getX() + 30){
                    ItemStack itemStack = ((TradeOffer) ((MerchantScreenHandler) ExtendedMerchantScreen.this.handler).getRecipes()
                                                                                                                     .get(
                                                                                                                         this.index + ExtendedMerchantScreen.this.indexStartOffset)).getSecondBuyItem();
                    if (!itemStack.isEmpty()){
                        context.drawItemTooltip(ExtendedMerchantScreen.this.textRenderer, itemStack, x, y);
                    }
                } else if (x > this.getX() + 65){
                    ItemStack itemStack = ((TradeOffer) ((MerchantScreenHandler) ExtendedMerchantScreen.this.handler).getRecipes()
                                                                                                                     .get(
                                                                                                                         this.index + ExtendedMerchantScreen.this.indexStartOffset)).getSellItem();
                    context.drawItemTooltip(ExtendedMerchantScreen.this.textRenderer, itemStack, x, y);
                }
            }
        }
    }
}
