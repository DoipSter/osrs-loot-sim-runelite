package com.osrslootsim;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * The side panel: pick a boss, type a number of kills, press Simulate. Under it the run's loot, its value, the chance
 * of the rarest drops by that many kills, and the account's Looting level.
 */
final class LootSimPanel extends PluginPanel
{
	// One press each for the bosses people simulate most; the picker under them lists the rest
	private static final String[] QUICK = {"Vorkath", "Zulrah", "Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"};
	private static final int COLUMNS = 5;
	private static final int MAX_CELLS = 50;
	private static final int DEFAULT_MAX_KILLS = 1000;
	// The least time between two runs. The site allows 20 a minute, so a row of fast presses would be turned away.
	private static final int COOLDOWN_MS = 3000;
	private static final int GAP = 7;
	// The site's own address for adding its Discord bot (/kill); the tag tells the site's visit count where it came from
	private static final String DISCORD_URL = LootSimApi.SITE + "/discord?utm_source=runelite";
	private static final int TEXT_WIDTH = PANEL_WIDTH - 2 * BORDER_OFFSET;
	private static final Color HEADER_COLOR = ColorScheme.DARKER_GRAY_COLOR.darker();
	private static final Font SMALL = FontManager.getRunescapeSmallFont();

	private final LootSimApi api;
	private final Icons icons;
	private final Account account;
	private final LootSimConfig config;

	private final JLabel levelLabel = label("", Color.WHITE);
	private final JLabel gainLabel = label("", ColorScheme.GRAND_EXCHANGE_PRICE);
	private final LevelBar levelBar = new LevelBar();
	private final SkillIcon skillIcon = new SkillIcon();
	private final JPanel levelPanel = new JPanel(new BorderLayout(0, 3));

	private final BossPicker picker;
	private final Map<String, JButton> quickButtons = new LinkedHashMap<>();

	private final JTextField killsField = new JTextField("100");
	private final JButton kcButton = new JButton("My KC");
	private final JButton simulateButton = new JButton("Simulate");
	private final JButton clearButton = new JButton("Clear");
	private final JPanel status = new JPanel(new GridLayout(0, 1));

	private final JPanel lootPanel = new JPanel(new BorderLayout(0, 1));
	private final JLabel lootTitle = label("", Color.WHITE);
	private final JLabel lootTotal = label("", ColorScheme.GRAND_EXCHANGE_PRICE);
	private final JPanel lootGrid = new JPanel(new GridLayout(0, COLUMNS, 1, 1));
	private final JLabel moreLabel = label("", ColorScheme.LIGHT_GRAY_COLOR);

	private final JPanel dryPanel = new JPanel(new BorderLayout(0, 3));
	// The header of the chances block: pressing it opens or closes the rows under it
	private final JButton dryToggle = new JButton();
	private final JPanel dryRows = new JPanel(new GridLayout(0, 1, 0, 2));

	private int maxKills = DEFAULT_MAX_KILLS;
	private boolean bossesLoaded;
	private boolean bossesLoading;
	private boolean hasRun;
	// Closed until the player opens it, then as they left it for the runs that follow
	private boolean dryOpen;
	private String statusText = "";
	private String siteUrl = LootSimApi.SITE;

	LootSimPanel(LootSimApi api, Icons icons, Account account, LootSimConfig config)
	{
		this.api = api;
		this.icons = icons;
		this.account = account;
		this.config = config;
		setLayout(new Stack(GAP));

		JLabel title = label("OSRS Loot Sim", Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setIcon(new ImageIcon(ImageUtil.resizeImage(ImageUtil.loadImageResource(LootSimPanel.class, "icon.png"), 20, 20)));
		title.setIconTextGap(6);
		addRow(title);

		JPanel levelText = clear(new JPanel(new BorderLayout(6, 0)));
		levelText.add(skillIcon, BorderLayout.WEST);
		levelText.add(levelLabel, BorderLayout.CENTER);
		levelText.add(gainLabel, BorderLayout.EAST);
		clear(levelPanel);
		levelPanel.add(levelText, BorderLayout.CENTER);
		levelPanel.add(levelBar, BorderLayout.SOUTH);
		addRow(levelPanel);

		picker = new BossPicker(icons, Arrays.asList(QUICK), QUICK[0]);
		picker.onChange(this::bossChanged);
		addRow(picker);

		JPanel quick = clear(new JPanel(new GridLayout(1, QUICK.length, 4, 0)));
		for (String name : QUICK)
		{
			JButton button = new JButton();
			button.setToolTipText(name);
			button.getAccessibleContext().setAccessibleName(name);
			button.setPreferredSize(new Dimension(0, 30));
			button.setFocusPainted(false);
			button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			button.addActionListener(e -> picker.select(name));
			icons.boss(name, image -> button.setIcon(new ImageIcon(image)));
			quickButtons.put(name, button);
			quick.add(button);
		}
		addRow(quick);

		JLabel killsLabel = label("Kills", ColorScheme.LIGHT_GRAY_COLOR);
		killsLabel.setLabelFor(killsField);
		killsField.setFont(SMALL);
		((AbstractDocument) killsField.getDocument()).setDocumentFilter(new DigitsOnly(5));
		killsField.addActionListener(e -> simulate());
		kcButton.setFont(SMALL);
		kcButton.setFocusPainted(false);
		kcButton.setToolTipText("Fill in your own kill count for this boss, from the hiscores");
		kcButton.addActionListener(e -> fillKillCount());
		JPanel killsRow = clear(new JPanel(new BorderLayout(6, 0)));
		killsRow.add(killsLabel, BorderLayout.WEST);
		killsRow.add(killsField, BorderLayout.CENTER);
		killsRow.add(kcButton, BorderLayout.EAST);
		addRow(killsRow);

		simulateButton.setFont(SMALL);
		simulateButton.setFocusPainted(false);
		simulateButton.setBackground(ColorScheme.BRAND_ORANGE);
		simulateButton.setForeground(ColorScheme.DARKER_GRAY_COLOR);
		simulateButton.setPreferredSize(new Dimension(0, 30));
		simulateButton.addActionListener(e -> simulate());
		clearButton.setFont(SMALL);
		clearButton.setFocusPainted(false);
		clearButton.setBackground(ColorScheme.PROGRESS_ERROR_COLOR);
		clearButton.setForeground(Color.WHITE);
		clearButton.setMargin(new Insets(2, 4, 2, 4));   // the look and feel's own side margins would cut the word short
		clearButton.setPreferredSize(new Dimension(56, 30));
		clearButton.setToolTipText("Clear the loot shown below");
		clearButton.setEnabled(false);
		clearButton.addActionListener(e -> clearRun());
		JPanel runRow = clear(new JPanel(new BorderLayout(4, 0)));
		runRow.add(simulateButton, BorderLayout.CENTER);
		runRow.add(clearButton, BorderLayout.EAST);
		addRow(runRow);

		clear(status).setVisible(false);
		addRow(status);

		JPanel lootHead = new JPanel(new BorderLayout(8, 0));
		lootHead.setBackground(HEADER_COLOR);
		lootHead.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		lootHead.add(lootTitle, BorderLayout.CENTER);
		lootHead.add(lootTotal, BorderLayout.EAST);
		lootGrid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		moreLabel.setOpaque(true);
		moreLabel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		moreLabel.setHorizontalAlignment(SwingConstants.CENTER);
		moreLabel.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		lootPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		lootPanel.add(lootHead, BorderLayout.NORTH);
		lootPanel.add(lootGrid, BorderLayout.CENTER);
		lootPanel.add(moreLabel, BorderLayout.SOUTH);
		lootPanel.setVisible(false);
		addRow(lootPanel);

		dryToggle.setFont(SMALL);
		dryToggle.setFocusPainted(false);
		dryToggle.setHorizontalAlignment(SwingConstants.LEFT);
		dryToggle.setIconTextGap(8);
		dryToggle.setBackground(HEADER_COLOR);
		dryToggle.setForeground(Color.WHITE);
		dryToggle.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		dryToggle.addActionListener(e -> showChances(!dryOpen));
		clear(dryRows);
		clear(dryPanel);
		dryPanel.add(dryToggle, BorderLayout.NORTH);
		dryPanel.add(dryRows, BorderLayout.CENTER);
		dryPanel.setVisible(false);
		addRow(dryPanel);
		showChances(false);

		JPanel links = clear(new JPanel(new GridLayout(0, 1)));
		links.add(link("Open on osrslootsim.com", "The full simulator in your browser", () -> LinkBrowser.browse(siteUrl)));
		links.add(link("Add the Discord bot", "Simulate kills in your own Discord server with /kill", () -> LinkBrowser.browse(DISCORD_URL)));
		addRow(links);

		bossChanged();
		showLevel(0);
		dressIcon();
	}

	@Override
	public void onActivate()
	{
		loadBosses();
		showLevel(0);
		dressIcon();   // the date may have moved into or out of a holiday since the panel was last open
		skillIcon.play(true);
	}

	@Override
	public void onDeactivate()
	{
		skillIcon.play(false);
	}

	/**
	 * One of the plugin's settings changed.
	 */
	void settingsChanged()
	{
		dressIcon();
	}

	SkillIcon skillIcon()
	{
		return skillIcon;
	}

	/**
	 * Opens or closes the list of chances: every rare drop of the boss, with the chance of having seen it by now.
	 */
	void showChances(boolean open)
	{
		dryOpen = open;
		dryToggle.setIcon(new Chevron(open));
		dryToggle.setToolTipText(open ? "Hide the list" : "Show your chance of each rare drop by now");
		dryRows.setVisible(open);
		revalidate();
		repaint();
	}

	private void dressIcon()
	{
		skillIcon.set(config.holidayIcon() ? Season.on(LocalDate.now()) : Season.NONE, config.animatedIcon());
		revalidate();
	}

	/**
	 * Another RuneScape account logged in: show its level.
	 */
	void accountChanged()
	{
		showLevel(0);
	}

	boolean hasRun()
	{
		return hasRun;
	}

	String statusText()
	{
		return statusText;
	}

	BossPicker picker()
	{
		return picker;
	}

	void simulate()
	{
		String boss = picker.selected();
		if (boss == null || !simulateButton.isEnabled())
		{
			return;
		}
		int kills = kills();
		if (kills < 1 || kills > maxKills)
		{
			say("Kills must be from 1 to " + Text.number(maxKills) + ".");
			return;
		}
		say("");
		simulateButton.setEnabled(false);
		simulateButton.setText("Simulating...");
		long started = System.currentTimeMillis();
		api.simulate(boss, kills, run ->
		{
			showRun(run);
			rest(started);
		}, error ->
		{
			say(error);
			rest(started);
		});
	}

	/**
	 * Gives the Simulate button back once the answer is in and the pause between runs is over.
	 */
	private void rest(long started)
	{
		simulateButton.setText("Simulate");
		int left = (int) Math.max(1, COOLDOWN_MS - (System.currentTimeMillis() - started));
		Timer timer = new Timer(left, e -> simulateButton.setEnabled(true));
		timer.setRepeats(false);
		timer.start();
	}

	private void showRun(LootSimApi.Run run)
	{
		String name = Text.plain(run.name, 60);
		String unit = Text.plural(run.unit);
		lootTitle.setText(name + " x " + Text.number(run.kills));
		lootTitle.setToolTipText(name + ", " + Text.number(run.kills) + " " + unit);
		lootTotal.setText(Text.coins(run.total));
		lootTotal.setToolTipText(Text.number(run.total) + " gp");

		lootGrid.removeAll();
		int shown = Math.min(run.items.size(), MAX_CELLS);
		for (LootSimApi.Item item : run.items.subList(0, shown))
		{
			lootGrid.add(cell(item));
		}
		for (int i = shown; i % COLUMNS != 0; i++)
		{
			lootGrid.add(cell(null));   // fills the last row, as the Loot Tracker does
		}
		int more = run.items.size() - shown + Math.max(0, run.more);
		moreLabel.setText("+" + Text.number(more) + " more " + (more == 1 ? "item" : "items"));
		moreLabel.setVisible(more > 0);
		lootPanel.setVisible(true);

		dryRows.removeAll();
		for (LootSimApi.Dry dry : run.dry)
		{
			dryRows.add(dryRow(dry));
		}
		dryToggle.setText("Chance by " + Text.number(run.kills) + " " + unit + " (" + run.dry.size() + ")");
		dryPanel.setVisible(!run.dry.isEmpty());

		siteUrl = run.url != null && run.url.startsWith(LootSimApi.SITE + "/") ? run.url : LootSimApi.SITE;
		hasRun = true;
		clearButton.setEnabled(true);

		if (account.known() && run.xp > 0)
		{
			int before = Looting.level(account.xp());
			account.addXp(run.xp);
			int after = Looting.level(account.xp());
			showLevel(run.xp);
			if (after > before)
			{
				say("Looting level " + after + "!");
			}
		}
		else
		{
			showLevel(0);
		}
		revalidate();
		repaint();
	}

	/**
	 * Takes the last run off the panel. The Looting level and the boss stay.
	 */
	void clearRun()
	{
		lootGrid.removeAll();
		dryRows.removeAll();
		lootPanel.setVisible(false);
		dryPanel.setVisible(false);
		hasRun = false;
		siteUrl = LootSimApi.SITE;
		clearButton.setEnabled(false);
		showLevel(0);   // the "+xp" belonged to the run that is gone
		say("");
	}

	private JLabel cell(LootSimApi.Item item)
	{
		JLabel cell = new JLabel();
		cell.setOpaque(true);
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setHorizontalAlignment(SwingConstants.CENTER);
		cell.setPreferredSize(new Dimension(0, 36));
		if (item != null)
		{
			String name = Text.plain(item.name, 60);
			cell.setToolTipText(name + " x " + Text.number(item.qty) + " (" + Text.number(item.value) + " gp)");
			if (item.rare)
			{
				cell.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
			}
			icons.item(cell, item.id, name, item.qty);
		}
		return cell;
	}

	private JPanel dryRow(LootSimApi.Dry dry)
	{
		String name = Text.plain(dry.name, 60);
		JLabel picture = new JLabel();
		picture.setHorizontalAlignment(SwingConstants.CENTER);
		picture.setPreferredSize(new Dimension(36, 32));
		icons.item(picture, dry.id, name, 1);

		JPanel text = clear(new JPanel(new GridLayout(2, 1)));
		text.add(label(name, ColorScheme.TEXT_COLOR));
		text.add(label(Text.rate(dry.rate), ColorScheme.LIGHT_GRAY_COLOR));

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 6));
		row.setToolTipText(name + ": " + Text.rate(dry.rate) + ", " + Text.percent(dry.chance) + " chance of at least one by now");
		row.add(picture, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		row.add(label(Text.percent(dry.chance), Color.WHITE), BorderLayout.EAST);
		return row;
	}

	/**
	 * The level row. gained: the xp the last run gave, shown beside the level until the next change.
	 */
	private void showLevel(long gained)
	{
		if (!account.known())
		{
			levelLabel.setText("Log in to earn Looting xp");
			levelLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			gainLabel.setText("");
			levelBar.set(0);
			levelPanel.setToolTipText("Your Looting level is saved per RuneScape account");
			return;
		}
		long xp = account.xp();
		int level = Looting.level(xp);
		levelLabel.setText("Looting " + level);
		levelLabel.setForeground(Color.WHITE);
		gainLabel.setText(gained > 0 ? "+" + Text.number(gained) + " xp" : "");
		levelBar.set(Looting.progress(xp));
		String tip = Text.number(xp) + " xp" + (level >= 99 ? "" : ", " + Text.number(Looting.toNext(xp)) + " to level " + (level + 1));
		levelPanel.setToolTipText(tip);
		levelLabel.setToolTipText(tip);
	}

	private void fillKillCount()
	{
		String boss = picker.selected();
		if (boss == null)
		{
			return;
		}
		if (!account.known())
		{
			say("Log in first: kill counts are saved per account.");
			return;
		}
		say("Looking up your kill count...");
		kcButton.setEnabled(false);
		account.killCount(boss, count ->
		{
			kcButton.setEnabled(true);
			if (!boss.equals(picker.selected()))
			{
				say("");   // another boss was picked while the hiscores answered
				return;
			}
			if (count == null)
			{
				say("No kill count found for " + boss + ". The hiscores list a boss only after a minimum number of kills.");
				return;
			}
			killsField.setText(String.valueOf(Math.min(count, maxKills)));
			say(count > maxKills ? "Your count is " + Text.number(count) + ". One run goes up to " + Text.number(maxKills) + "." : "");
		});
	}

	private int kills()
	{
		try
		{
			return Integer.parseInt(killsField.getText().trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private void bossChanged()
	{
		String selected = picker.selected();
		for (Map.Entry<String, JButton> quick : quickButtons.entrySet())
		{
			boolean on = quick.getKey().equals(selected);
			quick.getValue().setBorder(BorderFactory.createLineBorder(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR));
		}
	}

	/**
	 * Asks the site for the boss box's lists the first time the panel is opened. Until they arrive, and if they never
	 * do, the box holds the five quick bosses.
	 */
	private void loadBosses()
	{
		if (bossesLoaded || bossesLoading)
		{
			return;
		}
		bossesLoading = true;
		api.bosses(list ->
		{
			bossesLoading = false;
			bossesLoaded = true;
			if (list.maxKills > 0)
			{
				maxKills = Math.min(list.maxKills, 99999);
			}
			Set<String> names = new LinkedHashSet<>();
			for (LootSimApi.Boss boss : list.bosses)
			{
				String name = Text.plain(boss.name, 60);
				if (!name.isEmpty())
				{
					names.add(name);
				}
			}
			for (String name : QUICK)
			{
				names.add(name);
			}
			List<String> all = new ArrayList<>();
			if (list.all != null)
			{
				for (String name : list.all)
				{
					String clean = Text.plain(name, 60);
					if (!clean.isEmpty())
					{
						all.add(clean);
					}
				}
			}
			Set<String> known = new LinkedHashSet<>(names);
			known.addAll(all);
			Map<String, String> aliases = new HashMap<>();
			if (list.aliases != null)
			{
				for (Map.Entry<String, String> alias : list.aliases.entrySet())
				{
					String name = Text.plain(alias.getValue(), 60);
					if (alias.getKey() != null && known.contains(name))
					{
						aliases.put(Text.plain(alias.getKey(), 40), name);
					}
				}
			}
			picker.setBosses(new ArrayList<>(names), all, aliases);
		}, error -> bossesLoading = false);
	}

	/**
	 * A line under the Simulate button, wrapped to the panel's width; an empty text hides it.
	 */
	private void say(String text)
	{
		statusText = text;
		status.removeAll();
		for (String line : wrap(text, getFontMetrics(SMALL), TEXT_WIDTH))
		{
			status.add(label(line, ColorScheme.LIGHT_GRAY_COLOR));
		}
		status.setVisible(!text.isEmpty());
		revalidate();
		repaint();
	}

	private static List<String> wrap(String text, FontMetrics metrics, int width)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+"))
		{
			if (line.length() > 0 && metrics.stringWidth(line + " " + word) > width)
			{
				lines.add(line.toString());
				line.setLength(0);
			}
			line.append(line.length() > 0 ? " " : "").append(word);
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines;
	}

	private void addRow(JComponent row)
	{
		add(row);
	}

	/**
	 * A label in the game's small font. Its text is never read as HTML, whatever it starts with.
	 */
	private static JLabel label(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.putClientProperty("html.disable", Boolean.TRUE);
		label.setFont(SMALL);
		label.setForeground(color);
		return label;
	}

	/**
	 * A line of orange text that opens something in the browser when pressed.
	 */
	private static JButton link(String text, String tip, Runnable open)
	{
		JButton link = new JButton(text);
		link.setFont(SMALL);
		link.setForeground(ColorScheme.BRAND_ORANGE);
		link.setToolTipText(tip);
		link.setContentAreaFilled(false);
		link.setBorderPainted(false);
		link.setFocusPainted(false);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addActionListener(e -> open.run());
		return link;
	}

	private static JPanel clear(JPanel panel)
	{
		panel.setOpaque(false);
		return panel;
	}

	/**
	 * Rows from top to bottom, each as wide as the panel and as tall as it asks to be. Hidden rows take no room, and a
	 * row that asks for more width than the panel has is cut to fit, so a long boss name shortens instead of pushing
	 * the panel wider.
	 */
	private static final class Stack implements LayoutManager
	{
		private final int gap;

		Stack(int gap)
		{
			this.gap = gap;
		}

		@Override
		public void addLayoutComponent(String name, Component component)
		{
		}

		@Override
		public void removeLayoutComponent(Component component)
		{
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			Insets insets = parent.getInsets();
			int height = 0;
			int shown = 0;
			for (Component row : parent.getComponents())
			{
				if (row.isVisible())
				{
					height += row.getPreferredSize().height;
					shown++;
				}
			}
			return new Dimension(PANEL_WIDTH, insets.top + insets.bottom + height + gap * Math.max(0, shown - 1));
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent)
		{
			Insets insets = parent.getInsets();
			int width = parent.getWidth() - insets.left - insets.right;
			int y = insets.top;
			for (Component row : parent.getComponents())
			{
				if (row.isVisible())
				{
					int height = row.getPreferredSize().height;
					row.setBounds(insets.left, y, width, height);
					y += height + gap;
				}
			}
		}
	}

	/**
	 * The thin bar under the level: how far through the level the account is.
	 */
	private static final class LevelBar extends JComponent
	{
		private double fraction;

		LevelBar()
		{
			setPreferredSize(new Dimension(0, 5));
		}

		void set(double fraction)
		{
			this.fraction = Math.max(0, Math.min(1, fraction));
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(ColorScheme.BRAND_ORANGE);
			g.fillRect(0, 0, (int) Math.round(getWidth() * fraction), getHeight());
		}
	}

	/**
	 * Lets only digits into the kills box, up to a length.
	 */
	private static final class DigitsOnly extends DocumentFilter
	{
		private final int max;

		DigitsOnly(int max)
		{
			this.max = max;
		}

		@Override
		public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException
		{
			replace(fb, offset, 0, text, attr);
		}

		@Override
		public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attr) throws BadLocationException
		{
			String digits = text == null ? "" : text.replaceAll("[^0-9]", "");
			int room = max - (fb.getDocument().getLength() - length);
			if (room <= 0 && !digits.isEmpty())
			{
				return;
			}
			super.replace(fb, offset, length, digits.length() > room ? digits.substring(0, Math.max(0, room)) : digits, attr);
		}
	}
}
