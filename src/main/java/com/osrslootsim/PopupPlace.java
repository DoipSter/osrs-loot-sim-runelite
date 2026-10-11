package com.osrslootsim;

/**
 * Where the pop-up for an insane drop shows.
 */
public enum PopupPlace
{
	OFF("Off"),
	CHAT("In chat"),
	CHARACTER("In front of my character");

	private final String name;

	PopupPlace(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
