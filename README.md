# OSRS Loot Sim

A RuneLite side panel for [osrslootsim.com](https://osrslootsim.com): pick a boss, raid, chest or clue, type a number of
kills and press Simulate.

- A row of settings for the boss you picked, where it has any: Challenge Mode and team size at a raid, skulled at
  revenants, on a slayer task.
- The loot of the run as item pictures with their stack counts, and its total value at Grand Exchange prices.
- Your chance of having seen each rare drop by that many kills, in a list you open when you want it.
- When a run has an insane drop (a collection log item rarer than 1/2,000, or anything worth 10m or more): a card
  with the item in the chatbox, and a burst of light with a golden loot beam on your character. Both go after a few
  seconds, and each has its own switch in the plugin's settings.
- **My KC** fills in your own kill count for the boss, from the official hiscores or the count RuneLite last saw in
  chat, whichever is higher. **Clear** takes the last run off the panel.
- A **Looting level** from 1 to 99, earned by simulating. It is saved per RuneScape account in RuneLite's own settings
  and is separate from the level on the website. Its icon plays the site's loot beam and dresses for Halloween,
  Thanksgiving and Christmas; both can be switched off in the plugin's settings.

## What it sends

The drop tables and loot rules live on osrslootsim.com, so they follow game updates without a new version of this
plugin. Opening the panel asks the site for its list of bosses, and picking a boss asks for that boss's settings. Each
press of Simulate sends the boss's name, the number of kills and the settings you chose. Like any request to a website, that shows the site your IP address, which it uses only to limit how
often one visitor can ask.

Nothing about you or your account is sent to osrslootsim.com: not your name, your kill counts, your level or anything
from the game. **My KC** looks your character's name up on the official hiscores, as RuneLite's own Hiscore panel does;
that request goes to Jagex. One
run is at most 1,000 kills, and runs are at least 3 seconds apart.

## Building

Java 11 or newer (the code targets Java 11).

```
gradlew build
```

`gradlew panelPreview` draws the panel to `build/panel.png` without starting the game: once as it opens and once after
a real run. `src/test/java/com/osrslootsim/LootSimPluginTest.java` starts RuneLite with the plugin built in.

## Licence

BSD 2-Clause, see [LICENSE](LICENSE). Item and boss pictures are drawn by the game client at run time; none are in
this repository. Old School RuneScape is a trademark of Jagex Ltd. This plugin is not affiliated with Jagex.
