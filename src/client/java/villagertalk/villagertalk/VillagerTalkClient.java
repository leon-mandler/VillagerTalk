package villagertalk.villagertalk;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.util.Identifier;
public class VillagerTalkClient implements ClientModInitializer {

	private static final Identifier VILLAGER_TALK_BACKGROUND_TEXTURE = new Identifier("villagertalk","textures/villagertalk_chat_background.png");

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.


		//not necessary anymore, deprecated
		/*ClientPlayNetworking.registerGlobalReceiver(VillagerTalkS2CNetworkingConstants.VILLAGERUI_OPEN_PACKET_ID, ((client, handler, buf, responseSender) -> {
			//draw chat ui on render thread
			client.execute(() -> {
				// TODO
			});
		}));

		ClientPlayNetworking.registerGlobalReceiver(VillagerTalkS2CNetworkingConstants.VILLAGERUI_CLOSE_PACKET_ID, ((client, handler, buf, responseSender) -> {
			//close chat ui on render thread
			client.execute(() -> {
				// TODO

			});
		}));*/
	}
}