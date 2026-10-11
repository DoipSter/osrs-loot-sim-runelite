# OSRS Loot Sim

A RuneLite side panel for [osrslootsim.com](https://osrslootsim.com): pick a boss, raid, chest or clue, type a number of
kills and press Simulate.

- The loot of the run as item pictures with their stack counts, and its total value at Grand Exchange prices.
- Your chance of having seen each rare drop by that many kills, in a list you open when you want it.
- **My KC** fills in your own kill count for the boss, from the counts RuneLite already keeps. **Clear** takes the
  last run off the panel.
- A **Looting level** from 1 to 99, earned by simulating. It is saved per RuneScape account in RuneLite's own settings
  and is separate from the level on the website. Its icon plays the site's loot beam and dresses for Halloween,
  Thanksgiving and Christmas; both can be switched off in the plugin's settings.

## What it sends

The drop tables and loot rules live on osrslootsim.com, so they follow game updates without a new version of this
plugin. Opening the panel asks the site for its list of bosses. Each press of Simulate sends the boss's name and the
number of kills. Like any request to a website, that shows the site your IP address, which it uses only to limit how
often one visitor can ask.

Nothing about you or your account is sent: not your name, your kill counts, your level or anything from the game. One
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
