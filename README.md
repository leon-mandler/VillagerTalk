# VillagerTalk

## Overview
VillagerTalk is a Minecraft mod aimed at transforming the player's interaction with Villager NPCs by introducing dynamic negotiation and bartering mechanics. 
This mod is built for the Fabric modding platform.

### Video
https://youtu.be/OnSIS82DSUk

## Features

### Dynamic Negotiation System
- **Interactive Chat:** Players can engage in a chat-based dialogue with villagers, using a custom chat window integrated into the existing villager trading HUD.
- **Negotiation and Bartering:** Through the chat, players can negotiate trade prices, potentially securing better deals based on their interaction's quality.
- **AI-Powered Responses:** Utilizing the OpenAi GPT-4o API, VillagerTalk generates contextually appropriate responses from villagers, making each conversation unique and immersive.

### Enhanced Trading HUD
- **Custom Chat Interface:** The mod redesigns the trading HUD to include a chat window, allowing for seamless interaction without disrupting gameplay flow.


### Installation
For some obscure reason, the .jar file that I tried to build does not work correctly (I have troubleshot for 5 hours now).
So the next best way to play the mod is by cloning the repository and running it directly:
1. Clone the repository with IntelliJ.
2. Wait for gradle to download everything.
3. Run the gradle build task.
4. Now there should be the "Minecraft Client" run configuration available, if not: run the gradle task "configureClientLaunch".
5. Run "Minecraft Client"

If it doesn't work, there are some troubleshooting tips on https://fabricmc.net/wiki/tutorial:setup
### Usage

After Minecraft has started:
1. Maximise the window
2. Go to Options -> Video Settings -> Set "GUI Scale" to 2
3. Back to the main menu -> Singleplayer -> Create New world -> Game Mode Creative -> Create New World
4. Then you can open the chat with T and use the command "/locate structure minecraft:village_<desert/plains/snowy/etc.>" to find the nearest Village of your choice
5. After the command has run you can click on the green coordinates to input a teleport command into the chat.
6. After teleporting to a village, just right click a villager to open its inventory.
7. Interact/right click any villager to open the trading HUD.
8. Click on the writing field, or press enter, to enter the writing field.
9. Type a message.

### Current Implementation Status & Additional Infos

- Pretty much everything is implemented, except Villagers remembering Conversations indefinitely. Instead, the chats are persistent in memory, so only for one continuous game session. On a server this would be until the server restarts. So in one game session, the villagers each have the same personality every time and all of the previous chats are visible.
- The villagers can change the emerald, or the item amounts. 2nd items that are necessary for some trades are ignored (They are mostly irrelevant.)
- When the villager is threatened, it **spawns an iron golem** at its position that attacks the player. (The golem aggression only works in Survival mode, and in closed spaces the golem might suffocate in a wall lol)
- Currently, my **API Key** is hardcoded in the source code. It isn't expensive, in all our testing we have used 0.70€, so feel free to test as much as you want.
- The code is pretty well documented with **JavaDoc**. (documentation/JavaDoc/index.html)







