package com.osrslootsim;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.laf.RuneLiteLAF;
import okhttp3.OkHttpClient;

/**
 * Draws the side panel to PNG files without starting the game: once as it opens and once after a real run against
 * the simulator. Run it with "gradlew panelPreview" (see build.gradle). The pictures are stand-ins read from a
 * checkout of the website, since the game's own sprites need the game.
 */
public final class PanelPreview
{
	private static final int WIDTH = 242;
	private static final int MIN_HEIGHT = 503;
	// The moving skill icon is drawn at this frame of its loop: the loot beam at full height
	private static final int HELD_FRAME = 21;

	public static void main(String[] args) throws Exception
	{
		String out = args.length > 0 ? args[0] : "build/panel.png";
		LootSimApi.base = args.length > 1 ? args[1] : LootSimApi.SITE;
		String site = args.length > 2 ? args[2] : "";

		RuneLiteLAF.setup();
		FakeAccount account = new FakeAccount(43_808);
		LootSimPanel[] made = new LootSimPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			made[0] = new LootSimPanel(new LootSimApi(new OkHttpClient(), new Gson()), new FileIcons(site), account, new LootSimConfig()
			{
			});
			made[0].onActivate();
		});
		LootSimPanel panel = made[0];

		Thread.sleep(2500);   // the boss list
		draw(panel, out.replace(".png", "-start.png"));

		// The boss box: emptied it is a drop-down of the usual bosses, with text in it a search of everything
		BossPicker picker = panel.picker();
		SwingUtilities.invokeAndWait(() ->
		{
			picker.type("x");
			picker.type("");
		});
		draw(panel, out.replace(".png", "-empty.png"));
		for (String text : new String[]{"cox", "vork", "gob", "clue", "zzzz", "ab"})
		{
			SwingUtilities.invokeAndWait(() ->
			{
				picker.type(text);
				List<String> rows = picker.shown();
				System.out.println("\"" + text + "\" -> " + rows.size() + ": " + rows.subList(0, Math.min(6, rows.size())));
			});
		}
		draw(panel, out.replace(".png", "-search.png"));
		String wanted = args.length > 3 ? args[3] : "";
		SwingUtilities.invokeAndWait(() ->
		{
			picker.type(wanted.isEmpty() ? "vorkath" : wanted);
			picker.choose(0);
			System.out.println("picked: " + picker.selected());
		});

		SwingUtilities.invokeAndWait(panel::simulate);
		long deadline = System.currentTimeMillis() + 30_000;
		boolean[] done = new boolean[1];
		while (!done[0] && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(100);
			SwingUtilities.invokeAndWait(() -> done[0] = panel.hasRun() || !panel.statusText().isEmpty());
		}
		Thread.sleep(300);
		boolean ran = panel.hasRun();
		String status = panel.statusText();
		draw(panel, out);   // the chances block closed, as it starts
		SwingUtilities.invokeAndWait(() -> panel.showChances(true));
		draw(panel, out.replace(".png", "-open.png"));
		SwingUtilities.invokeAndWait(panel::clearRun);
		draw(panel, out.replace(".png", "-cleared.png"));
		SwingUtilities.invokeAndWait(() -> System.out.println("after Clear, a run is shown: " + panel.hasRun()));
		System.out.println("ran: " + ran + ", status: \"" + status + "\", xp now " + account.xp());
		System.exit(ran ? 0 : 1);
	}

	private static void draw(LootSimPanel panel, String file) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			panel.skillIcon().hold(HELD_FRAME);
			JPanel outer = panel.getWrappedPanel();
			int height = Math.max(MIN_HEIGHT, panel.getPreferredSize().height + 2);
			outer.setSize(WIDTH, height);
			layout(outer);
			BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			outer.print(g);
			g.dispose();
			try
			{
				File target = new File(file);
				if (target.getParentFile() != null)
				{
					target.getParentFile().mkdirs();
				}
				ImageIO.write(image, "png", target);
				System.out.println("wrote " + target.getAbsolutePath() + " " + WIDTH + "x" + height);
			}
			catch (IOException e)
			{
				throw new IllegalStateException(e);
			}
		});
	}

	// A panel that is not on screen is never laid out by Swing itself
	private static void layout(Component component)
	{
		synchronized (component.getTreeLock())
		{
			component.doLayout();
			if (component instanceof Container)
			{
				for (Component child : ((Container) component).getComponents())
				{
					layout(child);
				}
			}
		}
	}

	private static final class FakeAccount implements Account
	{
		private long xp;

		FakeAccount(long xp)
		{
			this.xp = xp;
		}

		@Override
		public boolean known()
		{
			return true;
		}

		@Override
		public long xp()
		{
			return xp;
		}

		@Override
		public void addXp(long gained)
		{
			xp += gained;
		}

		@Override
		public void killCount(String boss, Consumer<Integer> answer)
		{
			answer.accept(2300);
		}
	}

	/**
	 * Item and boss pictures from the website's own copies, named the way the website names them.
	 */
	private static final class FileIcons implements Icons
	{
		private static final int[] COIN_STACKS = {1, 2, 3, 4, 5, 25, 100, 250, 1000, 10000};

		private final Path site;
		private final Map<String, String> files = new HashMap<>();

		FileIcons(String site)
		{
			this.site = site.isEmpty() ? null : Paths.get(site);
			if (this.site == null)
			{
				return;
			}
			try (Reader reader = Files.newBufferedReader(this.site.resolve("public/data/mapping.json"), StandardCharsets.UTF_8))
			{
				List<List<Object>> rows = new Gson().fromJson(reader, new TypeToken<List<List<Object>>>()
				{
				}.getType());
				for (List<Object> row : rows)
				{
					files.put(String.valueOf(row.get(1)).toLowerCase(Locale.ENGLISH), String.valueOf(row.get(3)));
				}
			}
			catch (IOException e)
			{
				System.out.println("no item list at " + site + ": " + e.getMessage());
			}
		}

		@Override
		public void item(JLabel label, int id, String name, long quantity)
		{
			BufferedImage canvas = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = canvas.createGraphics();
			BufferedImage sprite = read("public/wimg/o/" + fileName(file(name, quantity)));
			if (sprite != null)
			{
				g.drawImage(sprite, (36 - sprite.getWidth()) / 2, (32 - sprite.getHeight()) / 2, null);
			}
			else
			{
				g.setColor(Color.GRAY);
				g.drawRect(8, 6, 19, 19);
			}
			if (quantity > 1)
			{
				String text = quantity < 100_000 ? String.valueOf(quantity) : quantity < 10_000_000 ? quantity / 1000 + "K" : quantity / 1_000_000 + "M";
				g.setFont(FontManager.getRunescapeSmallFont());
				g.setColor(Color.BLACK);
				g.drawString(text, 1, 11);
				g.setColor(quantity < 100_000 ? Color.YELLOW : quantity < 10_000_000 ? Color.WHITE : Color.GREEN);
				g.drawString(text, 0, 10);
			}
			g.dispose();
			label.setIcon(new ImageIcon(canvas));
		}

		@Override
		public void boss(String name, Consumer<BufferedImage> use)
		{
			String key = name.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
			key = key.startsWith("cluescroll") ? "cluescrollsall" : key.equals("barrows") ? "barrowschests" : key;
			BufferedImage image = read("public/img/activities/" + key + ".png");
			if (image != null)
			{
				use.accept(image);
			}
		}

		private String file(String name, long quantity)
		{
			String key = name.toLowerCase(Locale.ENGLISH);
			if (key.equals("coins"))
			{
				int stack = 1;
				for (int step : COIN_STACKS)
				{
					stack = quantity >= step ? step : stack;
				}
				return "Coins " + stack + ".png";
			}
			return files.getOrDefault(key, name + ".png");
		}

		private BufferedImage read(String path)
		{
			if (site == null)
			{
				return null;
			}
			try
			{
				File file = site.resolve(path).toFile();
				return file.isFile() ? ImageIO.read(file) : null;
			}
			catch (IOException e)
			{
				return null;
			}
		}

		// wimgName() in the website's src/wimg-name.js: a readable slug plus a hash of the wiki file name
		private static String fileName(String wikiFile)
		{
			String n = wikiFile.replace(' ', '_');
			int dot = n.lastIndexOf('.');
			String ext = dot > 0 && n.substring(dot + 1).matches("[A-Za-z0-9]+") ? n.substring(dot + 1).toLowerCase(Locale.ENGLISH) : "png";
			int hash = 0x811c9dc5;
			for (int i = 0; i < n.length(); i++)
			{
				hash ^= n.charAt(i);
				hash *= 0x01000193;
			}
			String slug = n.toLowerCase(Locale.ENGLISH).replaceAll("\\.[a-z0-9]+$", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
			slug = slug.length() > 40 ? slug.substring(0, 40) : slug;
			return (slug.isEmpty() ? "img" : slug) + "-" + String.format("%08x", hash) + "." + ext;
		}
	}
}
