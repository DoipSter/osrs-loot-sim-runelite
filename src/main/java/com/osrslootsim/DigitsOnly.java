package com.osrslootsim;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Lets only digits into a text box, up to a length.
 */
final class DigitsOnly extends DocumentFilter
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
