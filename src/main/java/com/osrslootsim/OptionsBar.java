package com.osrslootsim;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.text.AbstractDocument;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The row of settings above the kills box: what the picked boss can be simulated with. Challenge Mode and a team
 * size at a raid, skulled at revenants, on a slayer task where that changes the drops. The site says which a boss has
 * (one row's worth at most), so the row is empty and hidden for a boss with none. A switch is a small button, lit
 * while it is on; a number is a short box.
 */
final class OptionsBar extends JPanel
{
	private static final String TOGGLE = "toggle";
	private static final String NUMBER = "number";
	private static final int MAX_OPTIONS = 6;
	private static final int ROW_HEIGHT = 24;
	private static final int GAP = 4;
	private static final Font SMALL = FontManager.getRunescapeSmallFont();

	// What the player set for each boss, so coming back to a boss finds it as they left it
	private final Map<String, Map<String, Integer>> remembered = new HashMap<>();
	private List<LootSimApi.Option> options = Collections.emptyList();
	private Map<String, Integer> values = new HashMap<>();
	private String boss = "";

	OptionsBar()
	{
		super(new GridBagLayout());
		setOpaque(false);
		setVisible(false);
	}

	/**
	 * Shows a boss's settings; none hides the row.
	 */
	void offer(String boss, List<LootSimApi.Option> offered)
	{
		this.boss = boss;
		options = usable(offered);
		values = remembered.computeIfAbsent(boss, b -> new HashMap<>());
		removeAll();
		GridBagConstraints at = new GridBagConstraints();
		at.gridy = 0;
		at.insets = new Insets(0, 0, 0, GAP);
		for (LootSimApi.Option option : options)
		{
			if (TOGGLE.equals(option.kind))
			{
				add(toggle(option), at);
			}
			else
			{
				JTextField box = box(option);
				JLabel name = new JLabel(option.label);
				name.putClientProperty("html.disable", Boolean.TRUE);
				name.setFont(SMALL);
				name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				name.setToolTipText(option.tip);
				name.setLabelFor(box);
				add(name, at);
				add(box, at);
			}
		}
		at.weightx = 1;   // what is left of the row stays empty, so the settings sit at its left
		add(new JLabel(), at);
		setVisible(!options.isEmpty());
		revalidate();
		repaint();
	}

	/**
	 * The settings as the simulator takes them, "cm:1,team:3", or nothing for a boss without any.
	 */
	String settings()
	{
		StringBuilder out = new StringBuilder();
		for (LootSimApi.Option option : options)
		{
			out.append(out.length() > 0 ? "," : "").append(option.id).append(':').append(value(option));
		}
		return out.toString();
	}

	/**
	 * Sets one of the shown settings. For the panel preview.
	 */
	void choose(String id, int value)
	{
		values.put(id, value);
		offer(boss, options);
	}

	private int value(LootSimApi.Option option)
	{
		Integer set = values.get(option.id);
		if (TOGGLE.equals(option.kind))
		{
			return set == null ? (option.on ? 1 : 0) : (set != 0 ? 1 : 0);
		}
		int step = Math.max(1, option.step);
		int number = set == null ? option.value : set;
		number = Math.round((float) number / step) * step;
		return Math.max(option.min, Math.min(option.max, number));
	}

	private JButton toggle(LootSimApi.Option option)
	{
		JButton button = new JButton(option.label);
		button.putClientProperty("html.disable", Boolean.TRUE);
		button.setFont(SMALL);
		button.setFocusPainted(false);
		button.setMargin(new Insets(2, 2, 2, 2));
		button.setToolTipText(option.tip);
		// Sized by its word: the look and feel's own least width would make five of them overflow the row
		button.setPreferredSize(new Dimension(getFontMetrics(SMALL).stringWidth(option.label) + 14, ROW_HEIGHT));
		light(button, value(option) == 1);
		button.addActionListener(e ->
		{
			values.put(option.id, value(option) == 1 ? 0 : 1);
			light(button, value(option) == 1);
		});
		return button;
	}

	private static void light(JButton button, boolean on)
	{
		button.setBackground(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(on ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		button.getAccessibleContext().setAccessibleDescription(on ? "on" : "off");
	}

	private JTextField box(LootSimApi.Option option)
	{
		int digits = String.valueOf(option.max).length();
		JTextField box = new JTextField(String.valueOf(value(option)));
		box.setFont(SMALL);
		box.setForeground(Color.WHITE);
		box.setHorizontalAlignment(SwingConstants.CENTER);
		box.setToolTipText(option.tip + " (" + Text.number(option.min) + " to " + Text.number(option.max) + ")");
		box.setPreferredSize(new Dimension(digits * 7 + 14, ROW_HEIGHT));
		((AbstractDocument) box.getDocument()).setDocumentFilter(new DigitsOnly(digits));
		// Typing is taken as it comes; leaving the box, or Enter, pulls the number into its range
		Runnable settle = () ->
		{
			try
			{
				values.put(option.id, Integer.parseInt(box.getText().trim()));
			}
			catch (NumberFormatException e)
			{
				values.remove(option.id);
			}
			box.setText(String.valueOf(value(option)));
		};
		box.addActionListener(e -> settle.run());
		box.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				settle.run();
			}
		});
		box.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e)
			{
				typed();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e)
			{
				typed();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e)
			{
			}

			private void typed()
			{
				try
				{
					values.put(option.id, Integer.parseInt(box.getText().trim()));
				}
				catch (NumberFormatException e)
				{
					values.remove(option.id);
				}
			}
		});
		return box;
	}

	/**
	 * The options of the site's list that this version can show, with their words made safe to draw.
	 */
	private static List<LootSimApi.Option> usable(List<LootSimApi.Option> offered)
	{
		List<LootSimApi.Option> out = new ArrayList<>();
		if (offered == null)
		{
			return out;
		}
		for (LootSimApi.Option option : offered)
		{
			if (option == null || option.id == null || !option.id.matches("[a-z0-9-]{1,20}") || out.size() >= MAX_OPTIONS)
			{
				continue;
			}
			option.label = Text.plain(option.label, 10);
			option.tip = Text.plain(option.tip, 120);
			boolean number = NUMBER.equals(option.kind) && option.min >= 0 && option.max >= option.min && option.max <= 9_999_999;
			if (option.label.isEmpty() || !(TOGGLE.equals(option.kind) || number))
			{
				continue;
			}
			out.add(option);
		}
		return out;
	}
}
