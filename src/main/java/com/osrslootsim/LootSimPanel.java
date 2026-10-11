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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
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
	private static final int TEXT_WIDTH = PANEL_WIDTH - 2 * BORDER_OFFSET;
	private static final Color HEADER_COLOR = ColorScheme.DARKER_GRAY_COLOR.darker();
	private static final Icon NO_BOSS_ICON = new ImageIcon(new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB));
	private static final Font SMALL = FontManager.getRunescapeSmallFont();

	private final LootSimApi api;
	private final Icons icons;
	private final Account account;

	private final JLabel levelLabel = label("", Color.WHITE);
	private final JLabel gainLabel = label("", ColorScheme.GRAND_EXCHANGE_PRICE);
	private final LevelBar levelBar = new LevelBar();
	private final JPanel levelPanel = new JPanel(new BorderLayout(0, 3));

	private final DefaultComboBoxModel<String> bossModel = new DefaultComboBoxModel<>(QUICK);
	private final JComboBox<String> bossBox = new JComboBox<>(bossModel);
	private final Map<String, JButton> quickButtons = new LinkedHashMap<>();
	private final Map<String, Icon> bossIcons = new HashMap<>();
	private final Set<String> bossIconsAsked = new HashSet<>();

	private final JTextField killsField = new JTextField("100");
	private final JButton kcButton = new JButton("My KC");
	private final JButton simulateButton = new JButton("Simulate");
	private final JPanel status = new JPanel(new GridLayout(0, 1));

	private final JPanel lootPanel = new JPanel(new BorderLayout(0, 1));
	private final JLabel lootTitle = label("", Color.WHITE);
	private final JLabel lootTotal = label("", ColorScheme.GRAND_EXCHANGE_PRICE);
	private final JPanel lootGrid = new JPanel(new GridLayout(0, COLUMNS, 1, 1));
	private final JLabel moreLabel = label("", ColorScheme.LIGHT_GRAY_COLOR);

	private final JPanel dryPanel = new JPanel(new BorderLayout(0, 3));
	private final JLabel dryTitle = label("", ColorScheme.LIGHT_GRAY_COLOR);
	private final JPanel dryRows = new JPanel(new GridLayout(0, 1, 0, 2));

	private int maxKills = DEFAULT_MAX_KILLS;
	private boolean bossesLoaded;
	private boolean bossesLoading;
	private boolean fillingBosses;
	private boolean hasRun;
	private String statusText = "";
	private String siteUrl = LootSimApi.SITE;

	LootSimPanel(LootSimApi api, Icons icons, Account account)
	{
		this.api = api;
		this.icons = icons;
		this.account = account;
		setLayout(new Stack(GAP));

		JLabel title = label("OSRS Loot Sim", Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setIcon(new ImageIcon(ImageUtil.resizeImage(ImageUtil.loadImageResource(LootSimPanel.class, "icon.png"), 20, 20)));
		title.setIconTextGap(6);
		addRow(title);

		levelLabel.setIcon(new ImageIcon(ImageUtil.loadImageResource(LootSimPanel.class, "looting.png")));
		levelLabel.setIconTextGap(6);
		JPanel levelText = clear(new JPanel(new BorderLayout(6, 0)));
		levelText.add(levelLabel, BorderLayout.CENTER);
		levelText.add(gainLabel, BorderLayout.EAST);
		clear(levelPanel);
		levelPanel.add(levelText, BorderLayout.CENTER);
		levelPanel.add(levelBar, BorderLayout.SOUTH);
		addRow(levelPanel);

		bossBox.setFont(SMALL);
		bossBox.setRenderer(new BossRenderer());
		bossBox.setPrototypeDisplayValue("Chambers of Xeric");
		bossBox.setMaximumRowCount(12);
		bossBox.getAccessibleContext().setAccessibleName("Boss");
		bossBox.addActionListener(e -> bossChanged());
		addRow(bossBox);

		JPanel quick = clear(new JPanel(new GridLayout(1, QUICK.length, 4, 0)));
		for (String name : QUICK)
		{
			JButton button = new JButton();
			button.setToolTipText(name);
			button.getAccessibleContext().setAccessibleName(name);
			button.setPreferredSize(new Dimension(0, 30));
			button.setFocusPainted(false);
			button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			button.addActionListener(e -> bossBox.setSelectedItem(name));
			icons.boss(name, image ->
			{
				ImageIcon icon = new ImageIcon(image);
				bossIcons.put(name, icon);
				button.setIcon(icon);
				bossBox.repaint();
			});
			bossIconsAsked.add(name);
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
		kcButton.setToolTipText("Fill in your own kill count for this boss");
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
		addRow(simulateButton);

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

		clear(dryRows);
		clear(dryPanel);
		dryPanel.add(dryTitle, BorderLayout.NORTH);
		dryPanel.add(dryRows, BorderLayout.CENTER);
		dryPanel.setVisible(false);
		addRow(dryPanel);

		JButton siteLink = new JButton("Open on osrslootsim.com");
		siteLink.setFont(SMALL);
		siteLink.setForeground(ColorScheme.BRAND_ORANGE);
		siteLink.setContentAreaFilled(false);
		siteLink.setBorderPainted(false);
		siteLink.setFocusPainted(false);
		siteLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		siteLink.addActionListener(e -> LinkBrowser.browse(siteUrl));
		addRow(siteLink);

		bossChanged();
		showLevel(0);
	}

	@Override
	public void onActivate()
	{
		loadBosses();
		showLevel(0);
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

	void simulate()
	{
		String boss = (String) bossBox.getSelectedItem();
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
			show(run);
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

	private void show(LootSimApi.Run run)
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
		dryTitle.setText("Chance by " + Text.number(run.kills) + " " + unit);
		dryPanel.setVisible(!run.dry.isEmpty());

		siteUrl = run.url != null && run.url.startsWith(LootSimApi.SITE + "/") ? run.url : LootSimApi.SITE;
		hasRun = true;

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
		String boss = (String) bossBox.getSelectedItem();
		if (boss == null)
		{
			return;
		}
		if (!account.known())
		{
			say("Log in first: kill counts are saved per account.");
			return;
		}
		Integer count = account.killCount(boss);
		if (count == null)
		{
			say("No kill count saved for " + boss + " yet. RuneLite reads it from chat after a kill.");
			return;
		}
		killsField.setText(String.valueOf(Math.min(count, maxKills)));
		say(count > maxKills ? "Your count is " + Text.number(count) + ". One run goes up to " + Text.number(maxKills) + "." : "");
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
		if (fillingBosses)
		{
			return;
		}
		Object selected = bossBox.getSelectedItem();
		for (Map.Entry<String, JButton> quick : quickButtons.entrySet())
		{
			boolean on = quick.getKey().equals(selected);
			quick.getValue().setBorder(BorderFactory.createLineBorder(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR));
		}
	}

	/**
	 * Asks the site for the picker's list the first time the panel is opened. Until it arrives, and if it never does,
	 * the picker holds the five quick bosses.
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
			Object selected = bossBox.getSelectedItem();
			fillingBosses = true;
			bossModel.removeAllElements();
			bossModel.addAll(names);
			bossBox.setSelectedItem(names.contains(selected) ? selected : QUICK[0]);
			fillingBosses = false;
			bossChanged();
		}, error -> bossesLoading = false);
	}

	private Icon bossIcon(String name)
	{
		if (bossIconsAsked.add(name))
		{
			icons.boss(name, image ->
			{
				bossIcons.put(name, new ImageIcon(image));
				bossBox.repaint();
			});
		}
		return bossIcons.getOrDefault(name, NO_BOSS_ICON);
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
	 * A boss in the picker: its hiscores picture, when it has one, and its name.
	 */
	private final class BossRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
		{
			super.getListCellRendererComponent(list, value, index, selected, focused);
			putClientProperty("html.disable", Boolean.TRUE);
			String name = value == null ? "" : value.toString();
			setText(name);
			setFont(SMALL);
			setIcon(name.isEmpty() ? NO_BOSS_ICON : bossIcon(name));
			setIconTextGap(8);
			return this;
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
