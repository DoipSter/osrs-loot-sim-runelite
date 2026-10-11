package com.osrslootsim;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "OSRS Loot Sim",
	description = "Simulate kills at any boss, raid or clue from the side panel: the loot, its value and your chance of each rare drop by now. Uses osrslootsim.com",
	// Kept the same as the tags in runelite-plugin.properties, which the Plugin Hub searches. A typed word finds the
	// plugin when it is part of one of these, or of a word in the name or the description.
	tags = {"loot", "drop", "drops", "drop rate", "drop table", "simulator", "simulation", "calculator", "calc", "odds", "probability", "rng", "luck",
		"lucky", "dry", "dry streak", "dryness", "kc", "kill count", "killcount", "bosses", "bossing", "pvm", "raids", "cox", "tob", "toa", "clues",
		"casket", "chest", "pet", "pets", "unique", "uniques", "purple", "collection log", "clog", "loot beam"}
)
public class LootSimPlugin extends Plugin implements Account
{
	static final String CONFIG_GROUP = "osrslootsim";
	private static final String XP_KEY = "xp";

	// Where RuneLite's own Chat Commands plugin keeps the kill counts it reads from chat, under the boss's name in
	// lower case. A few sources go by another name there than in the simulator.
	private static final String KILLCOUNT_GROUP = "killcount";
	// How the game announces a new collection log item: a line in chat, a pop-up at the top of the screen, or both
	private static final String COLLECTION_LOG_CHAT = "New item added to your collection log:";
	private static final String COLLECTION_LOG_POPUP = "Collection log";
	private static final String COLLECTION_LOG_POPUP_LEAD = "New item:";
	private static final Map<String, String> KILLCOUNT_NAMES = new HashMap<>();

	static
	{
		KILLCOUNT_NAMES.put("barrows", "barrows chests");
		KILLCOUNT_NAMES.put("moons of peril", "lunar chest");
		KILLCOUNT_NAMES.put("fortis colosseum", "sol heredit");
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private LootSimConfig config;

	@Inject
	private HiscoreClient hiscoreClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private EventBus eventBus;

	@Inject
	private RuneLiteConfig runeLiteConfig;

	private LootSimPanel panel;
	private NavigationButton navButton;
	private DropPopups popups;

	// The account's levels as last seen, to tell a level gained from the game simply reporting its stats (as it
	// does for every skill at login)
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
	// The game's pop-up at the top of the screen is announced in two steps: the second reads what the first set up
	private boolean notificationStarted;
	// Never shown. RuneLite opens a plugin's settings when "Configure" is picked on one of the plugin's overlays, and
	// gives plugins no other way to ask for that: this is the overlay it is told about.
	private final Overlay settingsHandle = new Overlay(this)
	{
		@Override
		public Dimension render(Graphics2D graphics)
		{
			return null;
		}
	};

	@Provides
	LootSimConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootSimConfig.class);
	}

	@Override
	protected void startUp()
	{
		GameIcons icons = new GameIcons(client, clientThread, itemManager, spriteManager);
		popups = new DropPopups(client, clientThread, config, icons, overlayManager, audioPlayer, runeLiteConfig);
		popups.start();
		panel = new LootSimPanel(new LootSimApi(okHttpClient, gson), icons, this, config);
		panel.onRun(popups::ran);
		panel.onSettings(() -> eventBus.post(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, getName()), settingsHandle)));
		panel.onLevelUp(() ->
		{
			if (config.levelUpBeam())
			{
				popups.burst();
			}
		});
		BufferedImage icon = ImageUtil.loadImageResource(LootSimPlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("OSRS Loot Sim")
			.icon(ImageUtil.resizeImage(icon, 16, 16))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		popups.stop();
		popups = null;
		levels.clear();
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.settingsChanged();
			}
		});
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Integer before = levels.put(event.getSkill(), event.getLevel());
		if (before != null && event.getLevel() > before && config.levelUpBeam() && popups != null)
		{
			popups.burst();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Another login may be another account: its levels are not gains over the last one's
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			levels.clear();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE || popups == null)
		{
			return;
		}
		String line = net.runelite.client.util.Text.removeTags(event.getMessage());
		if (line.startsWith(COLLECTION_LOG_CHAT))
		{
			popups.collected(line.substring(COLLECTION_LOG_CHAT.length()).trim());
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		looted(event.getItems());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		looted(event.getItems());
	}

	// Chests, raids, clues and the rest, as RuneLite's own Loot Tracker plugin reports them while it is switched on.
	// It reports kills too: two reports of one kill are one burst.
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		looted(event.getItems());
	}

	/**
	 * Loot arrived in the game: shows the loot beam when that is switched on and the loot is worth enough, all of it
	 * together at Grand Exchange prices.
	 */
	private void looted(Collection<ItemStack> items)
	{
		if (!config.lootBeam() || popups == null || items == null || items.isEmpty())
		{
			return;
		}
		long worth = 0;
		for (ItemStack item : items)
		{
			worth += (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
		}
		if (worth >= Math.max(0, Math.min(LootSimConfig.MOST_LOOT_VALUE, config.lootBeamValue())))
		{
			popups.burst();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		// The collection log's pop-up, for players who have its chat line switched off. Read the way RuneLite's own
		// Screenshot plugin reads it.
		if (event.getScriptId() == ScriptID.NOTIFICATION_START)
		{
			notificationStarted = true;
		}
		else if (event.getScriptId() == ScriptID.NOTIFICATION_DELAY && notificationStarted)
		{
			notificationStarted = false;
			String title = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
			if (COLLECTION_LOG_POPUP.equalsIgnoreCase(title) && popups != null)
			{
				// "New item:" and the item's name, on two lines
				String text = net.runelite.client.util.Text.removeTags(String.valueOf(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN))).trim();
				popups.collected(text.startsWith(COLLECTION_LOG_POPUP_LEAD) ? text.substring(COLLECTION_LOG_POPUP_LEAD.length()).trim() : text);
			}
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.accountChanged();
			}
		});
	}

	@Override
	public boolean known()
	{
		return configManager.getRSProfileKey() != null;
	}

	@Override
	public long xp()
	{
		if (!known())
		{
			return 0;
		}
		Integer xp = configManager.getRSProfileConfiguration(CONFIG_GROUP, XP_KEY, Integer.class);
		return xp == null ? 0 : Math.max(0, Math.min(xp, Looting.MAX_XP));
	}

	@Override
	public void addXp(long gained)
	{
		if (known() && gained > 0)
		{
			// 200,000,000 at most, so it fits a whole number setting
			configManager.setRSProfileConfiguration(CONFIG_GROUP, XP_KEY, (int) Math.min(Looting.MAX_XP, xp() + gained));
		}
	}

	@Override
	public void killCount(String boss, Consumer<Integer> answer)
	{
		Integer saved = savedKillCount(boss);
		HiscoreSkill row = Hiscores.count(boss);
		if (row == null)
		{
			answer.accept(saved);
			return;
		}
		// The official hiscores, asked the way RuneLite's own Hiscore panel asks them: by the character's name
		clientThread.invoke(() ->
		{
			Player player = client.getLocalPlayer();
			String name = player == null ? null : player.getName();
			if (name == null)
			{
				SwingUtilities.invokeLater(() -> answer.accept(saved));
				return;
			}
			hiscoreClient.lookupAsync(name, HiscoreEndpoint.fromWorldTypes(client.getWorldType())).whenComplete((result, error) ->
			{
				net.runelite.client.hiscore.Skill listed = result == null ? null : result.getSkill(row);
				int count = Math.max(saved == null ? 0 : saved, listed == null ? 0 : listed.getLevel());
				SwingUtilities.invokeLater(() -> answer.accept(count > 0 ? count : null));
			});
		});
	}

	/**
	 * The kill count RuneLite last read from chat for a boss, or null.
	 */
	private Integer savedKillCount(String boss)
	{
		if (!known() || Hiscores.otherUnit(boss))
		{
			return null;
		}
		String name = boss.toLowerCase(Locale.ENGLISH);
		String[] keys = {KILLCOUNT_NAMES.getOrDefault(name, name), name.startsWith("the ") ? name.substring(4) : name};
		for (String key : keys)
		{
			Integer count = configManager.getRSProfileConfiguration(KILLCOUNT_GROUP, key, Integer.class);
			if (count != null && count > 0)
			{
				return count;
			}
		}
		return null;
	}
}
