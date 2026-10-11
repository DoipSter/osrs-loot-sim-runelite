package com.osrslootsim;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The boss box at the top of the panel: a search bar that is also a drop-down. Click it, or its arrow, and it lists
 * the usual bosses; type and it lists every boss, raid, chest, clue and monster whose name fits. The list opens
 * inside the panel, under the box.
 */
final class BossPicker extends JPanel
{
	private static final int VISIBLE_ROWS = 8;
	private static final int ROW_HEIGHT = 24;
	private static final int MAX_RESULTS = 60;
	private static final String NO_MATCH = "";
	private static final Icon NO_ICON = new ImageIcon(new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB));
	private static final Icon GLASS = new Glass();
	private static final Font SMALL = FontManager.getRunescapeSmallFont();

	private final Icons icons;
	private final JLabel picture = new JLabel(NO_ICON);
	private final JTextField field = new JTextField();
	private final JButton arrow = new JButton(new Arrow());
	private final DefaultListModel<String> results = new DefaultListModel<>();
	private final JList<String> list = new JList<>(results);
	private final JScrollPane scroll = new JScrollPane(list);
	private final Map<String, Icon> pictures = new HashMap<>();
	private final Set<String> asked = new HashSet<>();

	// What the list shows before anything is typed, and everything a search looks through. Short names ("cox",
	// "toa") lead to the source they stand for.
	private List<String> usual = new ArrayList<>();
	private List<String> all = new ArrayList<>();
	private Map<String, String> aliases = new HashMap<>();

	private String selected;
	private Runnable onChange = () ->
	{
	};
	private boolean open;
	private boolean typed;
	private boolean settingText;

	BossPicker(Icons icons, List<String> usual, String selected)
	{
		super(new BorderLayout(0, 2));
		this.icons = icons;
		this.usual = new ArrayList<>(usual);
		this.all = new ArrayList<>(usual);
		this.selected = selected;
		setOpaque(false);

		picture.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));
		field.setFont(SMALL);
		field.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setToolTipText("Type to search every boss, raid, chest, clue and monster");
		field.getAccessibleContext().setAccessibleName("Boss");
		arrow.setToolTipText("Show the list");
		arrow.getAccessibleContext().setAccessibleName("Show the list of bosses");
		arrow.setFocusable(false);   // so pressing it leaves the typing in the box alone
		arrow.setContentAreaFilled(false);
		arrow.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 8));

		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bar.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		bar.setPreferredSize(new Dimension(0, 30));
		bar.add(picture, BorderLayout.WEST);
		bar.add(field, BorderLayout.CENTER);
		bar.add(arrow, BorderLayout.EAST);
		add(bar, BorderLayout.NORTH);

		list.setFont(SMALL);
		list.setFocusable(false);   // the box keeps the keyboard while the list is open
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(ROW_HEIGHT);
		list.setFixedCellWidth(100);   // any width: the list is stretched to the box, and a fixed one spares measuring every row
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		list.setCellRenderer(new Row());
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setFocusable(false);
		scroll.setVisible(false);
		add(scroll, BorderLayout.CENTER);

		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				edited();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				edited();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
			}
		});
		field.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (!open)
				{
					openList(false);
					field.selectAll();   // so typing replaces the boss's name
				}
			}
		});
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				close();
			}
		});
		field.addActionListener(e ->
		{
			if (open)
			{
				choose(Math.max(0, list.getSelectedIndex()));
			}
		});
		key("DOWN", () -> step(1));
		key("UP", () -> step(-1));
		key("ESCAPE", this::close);
		arrow.addActionListener(e ->
		{
			if (open)
			{
				close();
				return;
			}
			field.requestFocusInWindow();
			openList(false);
			field.selectAll();
		});
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int row = list.locationToIndex(e.getPoint());
				if (row >= 0 && list.getCellBounds(row, row).contains(e.getPoint()))
				{
					list.setSelectedIndex(row);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				int row = list.locationToIndex(e.getPoint());
				if (row >= 0 && list.getCellBounds(row, row).contains(e.getPoint()))
				{
					choose(row);
				}
			}
		};
		list.addMouseMotionListener(mouse);
		list.addMouseListener(mouse);

		showSelected();
	}

	String selected()
	{
		return selected;
	}

	void onChange(Runnable onChange)
	{
		this.onChange = onChange;
	}

	/**
	 * Picks a boss from outside the box: a quick button, or the list arriving from the site.
	 */
	void select(String name)
	{
		selected = name;
		open = false;
		scroll.setVisible(false);
		showSelected();
		refit();
		onChange.run();
	}

	/**
	 * The lists from the site. all and aliases may be empty: an older site sends only the usual list, and a search
	 * then looks through that.
	 */
	void setBosses(List<String> usual, List<String> all, Map<String, String> aliases)
	{
		this.usual = new ArrayList<>(usual);
		Set<String> names = new LinkedHashSet<>(usual);
		names.addAll(all);
		this.all = new ArrayList<>(names);
		this.aliases = new HashMap<>(aliases);
		if (open)
		{
			fill();
		}
	}

	/**
	 * Puts text in the box as if it was typed. For the panel preview.
	 */
	void type(String text)
	{
		field.setText(text);
	}

	/**
	 * The rows of the open list, top first; empty when it is closed or nothing fits. For the panel preview.
	 */
	List<String> shown()
	{
		List<String> rows = new ArrayList<>();
		for (int i = 0; open && i < results.size(); i++)
		{
			if (!NO_MATCH.equals(results.get(i)))
			{
				rows.add(results.get(i));
			}
		}
		return rows;
	}

	/**
	 * Picks a row of the open list. Nothing happens when the list is closed or the row is the "no match" line.
	 */
	void choose(int row)
	{
		if (!open || row < 0 || row >= results.size() || NO_MATCH.equals(results.get(row)))
		{
			return;
		}
		select(results.get(row));
		field.transferFocus();   // on to the next control, so a later click on the box starts a fresh search
	}

	private void edited()
	{
		if (settingText)
		{
			return;
		}
		openList(true);
	}

	private void openList(boolean typedNow)
	{
		open = true;
		typed = typedNow;
		picture.setIcon(GLASS);
		fill();
	}

	private void close()
	{
		if (!open)
		{
			return;
		}
		open = false;
		scroll.setVisible(false);
		showSelected();
		refit();
	}

	private void step(int by)
	{
		if (!open)
		{
			openList(false);
			return;
		}
		if (results.isEmpty())
		{
			return;
		}
		int row = Math.max(0, Math.min(results.size() - 1, list.getSelectedIndex() + by));
		list.setSelectedIndex(row);
		list.ensureIndexIsVisible(row);
	}

	private void fill()
	{
		String text = field.getText().trim();
		List<String> rows = typed && !text.isEmpty() ? search(text) : usual;
		results.clear();
		for (String name : rows)
		{
			results.addElement(name);
		}
		if (results.isEmpty())
		{
			results.addElement(NO_MATCH);
		}
		list.setVisibleRowCount(Math.min(VISIBLE_ROWS, results.size()));
		int at = typed ? 0 : Math.max(0, rows.indexOf(selected));
		list.setSelectedIndex(at);
		list.ensureIndexIsVisible(at);
		scroll.setVisible(true);
		refit();
	}

	/**
	 * The names that fit the text: what a short name stands for first, then names that start with the text, then
	 * names that contain it. Within each, the usual bosses come before the rest, and shorter names before longer.
	 */
	private List<String> search(String text)
	{
		String wanted = plain(text);
		if (wanted.isEmpty())
		{
			return usual;
		}
		List<String> named = new ArrayList<>();
		for (Map.Entry<String, String> alias : aliases.entrySet())
		{
			if (plain(alias.getKey()).startsWith(wanted) && !named.contains(alias.getValue()))
			{
				named.add(alias.getValue());
			}
		}
		List<String> starts = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String name : all)
		{
			String plain = plain(name);
			if (plain.startsWith(wanted))
			{
				starts.add(name);
			}
			else if (plain.contains(wanted))
			{
				contains.add(name);
			}
		}
		Set<String> common = new HashSet<>(usual);
		Comparator<String> order = Comparator.<String, Boolean>comparing(name -> !common.contains(name))
			.thenComparingInt(String::length).thenComparing(Comparator.naturalOrder());
		Collections.sort(named, order);
		Collections.sort(starts, order);
		Collections.sort(contains, order);
		Set<String> found = new LinkedHashSet<>(named);
		found.addAll(starts);
		found.addAll(contains);
		List<String> rows = new ArrayList<>(found);
		return rows.size() > MAX_RESULTS ? rows.subList(0, MAX_RESULTS) : rows;
	}

	private static String plain(String name)
	{
		return name.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9 ]", "").trim();
	}

	private void showSelected()
	{
		settingText = true;
		field.setText(selected == null ? "" : selected);
		field.setCaretPosition(0);
		settingText = false;
		picture.setIcon(selected == null ? NO_ICON : picture(selected));
	}

	private Icon picture(String name)
	{
		if (asked.add(name))
		{
			icons.boss(name, image ->
			{
				pictures.put(name, new ImageIcon(boxed(image)));
				if (name.equals(selected) && !open)
				{
					picture.setIcon(pictures.get(name));
				}
				list.repaint();
			});
		}
		return pictures.getOrDefault(name, NO_ICON);
	}

	// Hiscores pictures differ in width: centred in one box size, the names beside them line up
	private static BufferedImage boxed(BufferedImage image)
	{
		BufferedImage box = new BufferedImage(NO_ICON.getIconWidth(), NO_ICON.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		double scale = Math.min(1, Math.min((double) box.getWidth() / image.getWidth(), (double) box.getHeight() / image.getHeight()));
		int width = (int) Math.round(image.getWidth() * scale);
		int height = (int) Math.round(image.getHeight() * scale);
		Graphics2D g = box.createGraphics();
		g.drawImage(image, (box.getWidth() - width) / 2, (box.getHeight() - height) / 2, width, height, null);
		g.dispose();
		return box;
	}

	// The list opening or closing changes how tall this box is: have the panel lay itself out again
	private void refit()
	{
		revalidate();
		repaint();
	}

	private void key(String stroke, Runnable action)
	{
		field.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(stroke), stroke);
		field.getActionMap().put(stroke, new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				action.run();
			}
		});
	}

	/**
	 * A row of the list: the boss's hiscores picture, when it has one, and its name.
	 */
	private final class Row extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus)
		{
			String name = value == null ? NO_MATCH : value.toString();
			boolean none = NO_MATCH.equals(name);
			super.getListCellRendererComponent(list, none ? "No boss or monster by that name" : name, index, isSelected && !none, false);
			putClientProperty("html.disable", Boolean.TRUE);
			setFont(SMALL);
			setIcon(none ? NO_ICON : picture(name));
			setIconTextGap(8);
			setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));
			if (none)
			{
				setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
			return this;
		}
	}

	/**
	 * The magnifying glass that stands in for the boss's picture while the list is open.
	 */
	private static final class Glass implements Icon
	{
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.drawOval(x + 3, y + 3, 8, 8);
			g2.drawLine(x + 10, y + 10, x + 14, y + 14);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return NO_ICON.getIconWidth();
		}

		@Override
		public int getIconHeight()
		{
			return NO_ICON.getIconHeight();
		}
	}

	/**
	 * The small down arrow at the end of the box.
	 */
	private static final class Arrow implements Icon
	{
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.drawPolyline(new int[]{x + 1, x + 5, x + 9}, new int[]{y + 2, y + 6, y + 2}, 3);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 10;
		}

		@Override
		public int getIconHeight()
		{
			return 8;
		}
	}
}
