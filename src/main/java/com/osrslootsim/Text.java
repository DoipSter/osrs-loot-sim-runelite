package com.osrslootsim;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Wording and number formats the panel shares.
 */
final class Text
{
	private static final DecimalFormatSymbols ENGLISH = DecimalFormatSymbols.getInstance(Locale.ENGLISH);

	private Text()
	{
	}

	/**
	 * Text from the simulator, made safe to show: Swing draws a label or tooltip that starts with an HTML tag as
	 * HTML, so angle brackets and control characters are dropped and the length is capped.
	 */
	static String plain(String text, int max)
	{
		if (text == null)
		{
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length() && out.length() < max; i++)
		{
			char c = text.charAt(i);
			if (c != '<' && c != '>' && c >= ' ' && c != 127)
			{
				out.append(c);
			}
		}
		return out.toString().trim();
	}

	/**
	 * 1234567 as "1,234,567".
	 */
	static String number(long n)
	{
		return new DecimalFormat("#,##0", ENGLISH).format(n);
	}

	/**
	 * A coin total short enough for a narrow header: "14,269 gp", "681K gp", "15.14M gp", "2.56B gp".
	 */
	static String coins(long gp)
	{
		if (gp < 100_000)
		{
			return number(gp) + " gp";
		}
		if (gp < 10_000_000)
		{
			return number(gp / 1000) + "K gp";
		}
		DecimalFormat two = new DecimalFormat("#,##0.00", ENGLISH);
		return gp < 1_000_000_000 ? two.format(gp / 1e6) + "M gp" : two.format(gp / 1e9) + "B gp";
	}

	/**
	 * A drop rate as the site writes it: "1/5,000", "1/64", "1/12.5", "1/2.25".
	 */
	static String rate(double oneIn)
	{
		if (!(oneIn >= 1))
		{
			return "1/1";
		}
		if (oneIn >= 100)
		{
			return "1/" + number(Math.round(oneIn));
		}
		String digits = new DecimalFormat(oneIn < 10 ? "0.##" : "0.#", ENGLISH).format(oneIn);
		return "1/" + digits;
	}

	/**
	 * A chance from 0 to 1 as a percentage that never rounds to a flat 0% or 100% unless it is one.
	 */
	static String percent(double chance)
	{
		double pct = 100 * Math.max(0, Math.min(1, chance));
		if (pct > 0 && pct < 0.1)
		{
			return "<0.1%";
		}
		if (pct > 99.9 && pct < 100)
		{
			return ">99.9%";
		}
		return new DecimalFormat("0.0", ENGLISH).format(pct) + "%";
	}

	/**
	 * "kill" as "kills", "search" as "searches": what one run of a source is counted in, for more than one.
	 */
	static String plural(String unit)
	{
		String word = plain(unit, 20).toLowerCase(Locale.ENGLISH);
		if (word.isEmpty())
		{
			word = "kill";
		}
		return word.matches(".*(ch|sh|s|x)$") ? word + "es" : word + "s";
	}
}
