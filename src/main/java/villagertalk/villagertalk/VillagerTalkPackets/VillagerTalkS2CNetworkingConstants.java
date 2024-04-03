package villagertalk.villagertalk.VillagerTalkPackets;

import net.minecraft.util.Identifier;

/**
 * VillagerTalkS2CNetworkingConstants
 * Identifiers for the VillagerTalk S2C Packets
 */
public class VillagerTalkS2CNetworkingConstants{
    public static final Identifier VILLAGERUI_OPEN_PACKET_ID = new Identifier("villager_talk", "open_chat_ui");
    public static final Identifier VILLAGERUI_CLOSE_PACKET_ID = new Identifier("villager_talk", "close_chat_ui");

    public static final Identifier VILLAGER_RESPONSE_PACKET = new Identifier("villager_talk", "villager_response");
}
