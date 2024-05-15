package villagertalk.villagertalk.VillagerTalkPackets;

import net.minecraft.util.Identifier;

public class VillagerTalkC2SNetworkingConstants{
    public static final Identifier PLAYER_SENT_PROMPT =
        new Identifier("villager_talk", "player_prompt");

    public static final Identifier INITIAL_VILLAGER_MESSAGE_REQUEST = new Identifier("villager_talk", "initial_villager_message_request");

    public static final Identifier VILLAGER_CLOSED = new Identifier("villager_talk", "villager_closed");
}
