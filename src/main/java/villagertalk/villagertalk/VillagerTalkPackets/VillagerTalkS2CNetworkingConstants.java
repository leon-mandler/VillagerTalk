package villagertalk.villagertalk.VillagerTalkPackets;

import net.minecraft.util.Identifier;

/**
    * Constants for the server to client networking of the VillagerTalk mod.
 */
public class VillagerTalkS2CNetworkingConstants{

    /**
     * Identifier for the packet that sends the villager's response to the player's prompt.
     */
    public static final Identifier VILLAGER_RESPONSE_PACKET = new Identifier("villager_talk", "villager_response");

    /**
     * Identifier for the packet that sends the villager's initial message to the player.
     */
    public static final Identifier VILLAGER_INITIAL_MESSAGE = new Identifier("villager_talk", "initial_villager_message");

}
