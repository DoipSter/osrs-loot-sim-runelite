package com.osrslootsim;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Pictures from the game itself: item sprites by item ID, boss pictures from the hiscores' sprites.
 */
final class GameIcons implements Icons
{
	private static final int ITEMS_PER_FRAME = 2500;

	// The simulator's names for sources the hiscores spell another way. Every clue tier shares the hiscores' one clue
	// picture.
	private static final Map<String, String> HISCORE_NAMES = new HashMap<>();

	static
	{
		HISCORE_NAMES.put("barrows", "barrows chests");
		HISCORE_NAMES.put("the nightmare", "nightmare");
		HISCORE_NAMES.put("moons of peril", "lunar chests");
		HISCORE_NAMES.put("fortis colosseum", "sol heredit");
		HISCORE_NAMES.put("guardians of the rift", "rifts closed");
	}

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;

	// Item IDs by lower-case name, for the items the simulator has no ID for (untradeables: pets, keys, clue scrolls).
	// Read from the game's own item list the first time one is needed, a slice per frame. Client thread only.
	private final Map<String, Integer> ids = new HashMap<>();
	private final List<Runnable> waiting = new ArrayList<>();
	private int scanned;
	private boolean scanning;
	private boolean indexed;

	GameIcons(Client client, ClientThread clientThread, ItemManager itemManager, SpriteManager spriteManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
	}

	@Override
	public void item(JLabel label, int id, String name, long quantity)
	{
		int count = (int) Math.max(1, Math.min(quantity, Integer.MAX_VALUE));
		if (id > 0)
		{
			itemManager.getImage(id, count, count > 1).addTo(label);
			return;
		}
		byName(name, found -> SwingUtilities.invokeLater(() -> itemManager.getImage(found, count, count > 1).addTo(label)));
	}

	@Override
	public void boss(String name, Consumer<BufferedImage> use)
	{
		String key = name.toLowerCase(Locale.ENGLISH);
		String wanted = key.startsWith("clue scroll (") ? "clue scrolls (all)" : HISCORE_NAMES.getOrDefault(key, key);
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getSpriteId() > 0 && skill.getName().toLowerCase(Locale.ENGLISH).equals(wanted))
			{
				spriteManager.getSpriteAsync(skill.getSpriteId(), 0, image -> SwingUtilities.invokeLater(() -> use.accept(image)));
				return;
			}
		}
	}

	private void byName(String name, IntConsumer found)
	{
		String key = name.toLowerCase(Locale.ENGLISH);
		clientThread.invokeLater(() ->
		{
			Runnable look = () ->
			{
				Integer id = ids.get(key);
				if (id != null)
				{
					found.accept(id);
				}
			};
			if (indexed)
			{
				look.run();
				return;
			}
			waiting.add(look);
			if (!scanning)
			{
				scanning = true;
				clientThread.invokeLater((BooleanSupplier) this::scan);
			}
		});
	}

	/**
	 * Reads the next slice of the game's item list. Runs once a frame until it returns true.
	 */
	private boolean scan()
	{
		if (client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
		{
			return false;   // the game's files are still loading
		}
		int total = client.getItemCount();
		int end = Math.min(total, scanned + ITEMS_PER_FRAME);
		for (; scanned < end; scanned++)
		{
			ItemComposition item = client.getItemDefinition(scanned);
			// The bank note and placeholder of an item share its name: the item itself is the one to draw
			if (item == null || item.getNote() != -1 || item.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			String name = item.getName();
			if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("null"))
			{
				ids.putIfAbsent(name.toLowerCase(Locale.ENGLISH), scanned);
			}
		}
		if (scanned < total)
		{
			return false;
		}
		indexed = true;
		scanning = false;
		for (Runnable look : waiting)
		{
			look.run();
		}
		waiting.clear();
		return true;
	}
}
