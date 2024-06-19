package villagertalk.villagertalk.VillagerTalkPackets;

import net.minecraft.util.Identifier;

/**
 * Constants for the client to server networking of the VillagerTalk mod.
 */
public class VillagerTalkC2SNetworkingConstants{
    /**
     * Identifier for the packet that sends the player's prompt to the villager's prompt.
     */
    public static final Identifier PLAYER_SENT_PROMPT = new Identifier("villager_talk", "player_prompt");

    /**
     * Identifier for the packet that requests the villager's initial message.
     */
    public static final Identifier INITIAL_VILLAGER_MESSAGE_REQUEST = new Identifier("villager_talk",
                                                                                     "initial_villager_message_request");
    /**
     * Identifier for the packet that tells the server that the villager's chat GUI has been closed.
     */
    public static final Identifier VILLAGER_CLOSED = new Identifier("villager_talk", "villager_closed");
}
