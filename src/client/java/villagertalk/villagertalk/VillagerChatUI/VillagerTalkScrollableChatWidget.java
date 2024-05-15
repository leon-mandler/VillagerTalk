package villagertalk.villagertalk.VillagerChatUI;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.Text;

public class VillagerTalkScrollableChatWidget extends ScrollableTextWidget{


    public VillagerTalkScrollableChatWidget(int x, int y, int width, int height, Text message, TextRenderer textRenderer){
        super(x, y, width, height, message, textRenderer);
    }

    public void setMaxScrollY(){
        setScrollY(getMaxScrollY());
    }
}
