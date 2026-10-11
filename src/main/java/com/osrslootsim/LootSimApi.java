package com.osrslootsim;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Asks osrslootsim.com to run the kills. The site holds the drop tables and the loot rules, so they follow game
 * updates without a new version of this plugin. A request carries the boss's name and the number of kills, nothing
 * about the player or the account. Answers arrive on the Swing thread.
 */
final class LootSimApi
{
	static final String SITE = "https://osrslootsim.com";

	/**
	 * Where the simulator answers. Only the panel preview and a local test client point it anywhere else.
	 */
	static String base = SITE;

	private static final String UNREACHABLE = "Could not reach osrslootsim.com.";
	private static final String UNEXPECTED = "The simulator sent an answer this version does not understand.";

	static final class Boss
	{
		String name;
		String slug;
	}

	static final class BossList
	{
		// What the boss box lists before anything is typed
		List<Boss> bosses;
		// Everything a search looks through, and the short names that lead to one of them ("cox"). Both may be missing.
		List<String> all;
		Map<String, String> aliases;
		int maxKills;
	}

	static final class Item
	{
		String name;
		int id;
		long qty;
		long value;
		boolean rare;
	}

	static final class Dry
	{
		String name;
		int id;
		double rate;
		double chance;
	}

	static final class Run
	{
		String name;
		String url;
		String unit;
		int kills;
		long total;
		List<Item> items;
		int more;
		List<Dry> dry;
		long xp;
	}

	private static final class Failure
	{
		String error;
	}

	private final OkHttpClient http;
	private final Gson gson;

	LootSimApi(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/**
	 * The bosses, raids, chests and clues the picker lists.
	 */
	void bosses(Consumer<BossList> ok, Consumer<String> fail)
	{
		HttpUrl url = url("api/sim/bosses");
		if (url == null)
		{
			fail.accept(UNREACHABLE);
			return;
		}
		get(url, BossList.class, list -> list.bosses != null && !list.bosses.isEmpty()
			&& list.bosses.stream().allMatch(b -> b != null && b.name != null && !b.name.isEmpty()), ok, fail);
	}

	void simulate(String boss, int kills, Consumer<Run> ok, Consumer<String> fail)
	{
		HttpUrl url = url("api/sim");
		if (url == null)
		{
			fail.accept(UNREACHABLE);
			return;
		}
		url = url.newBuilder().addQueryParameter("boss", boss).addQueryParameter("kills", String.valueOf(kills)).build();
		get(url, Run.class, run -> run.name != null && run.items != null && run.dry != null && run.kills > 0
			&& run.items.stream().allMatch(i -> i != null && i.name != null)
			&& run.dry.stream().allMatch(d -> d != null && d.name != null), ok, fail);
	}

	private static HttpUrl url(String path)
	{
		HttpUrl site = HttpUrl.parse(base);
		return site == null ? null : site.newBuilder().addPathSegments(path).build();
	}

	private <T> void get(HttpUrl url, Class<T> type, Predicate<T> valid, Consumer<T> ok, Consumer<String> fail)
	{
		Request request = new Request.Builder().url(url).header("Accept", "application/json").build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				SwingUtilities.invokeLater(() -> fail.accept(UNREACHABLE));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					String body = r.body() == null ? "" : r.body().string();
					if (!r.isSuccessful())
					{
						String message = failure(r.code(), body);
						SwingUtilities.invokeLater(() -> fail.accept(message));
						return;
					}
					T value = gson.fromJson(body, type);
					if (value == null || !valid.test(value))
					{
						SwingUtilities.invokeLater(() -> fail.accept(UNEXPECTED));
						return;
					}
					SwingUtilities.invokeLater(() -> ok.accept(value));
				}
				catch (IOException e)
				{
					SwingUtilities.invokeLater(() -> fail.accept(UNREACHABLE));
				}
				catch (JsonParseException | NumberFormatException e)
				{
					SwingUtilities.invokeLater(() -> fail.accept(UNEXPECTED));
				}
			}
		});
	}

	/**
	 * The line to show for an answer that is not a result: the site's own one-line reason when it gave one.
	 */
	private String failure(int code, String body)
	{
		if (code == 429)
		{
			return "Too many runs in a row. Try again in a minute.";
		}
		try
		{
			Failure failure = gson.fromJson(body, Failure.class);
			if (failure != null && failure.error != null && !failure.error.isEmpty())
			{
				return Text.plain(failure.error, 120);
			}
		}
		catch (JsonParseException e)
		{
			// not JSON: the plain line below
		}
		return "The simulator is not answering right now (" + code + ").";
	}
}
