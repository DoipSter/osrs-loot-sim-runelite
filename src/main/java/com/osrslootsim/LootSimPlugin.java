package com.osrslootsim;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "OSRS Loot Sim",
	description = "Simulate kills at any boss, raid or clue from the side panel: the loot, its value and your chance of each rare drop by now. Uses osrslootsim.com",
	tags = {"loot", "drop", "simulator", "dry", "calculator", "luck", "boss", "raid", "clue"}
)
public class LootSimPlugin extends Plugin implements Account
{
	private static final String CONFIG_GROUP = "osrslootsim";
	private static final String XP_KEY = "xp";

	// Where RuneLite's own Chat Commands plugin keeps the kill counts it reads from chat, under the boss's name in
	// lower case. A few sources go by another name there than in the simulator.
	private static final String KILLCOUNT_GROUP = "killcount";
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

	private LootSimPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		panel = new LootSimPanel(new LootSimApi(okHttpClient, gson),
			new GameIcons(client, clientThread, itemManager, spriteManager), this);
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
		navButton = null;
		panel = null;
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
	public Integer killCount(String boss)
	{
		if (!known())
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
