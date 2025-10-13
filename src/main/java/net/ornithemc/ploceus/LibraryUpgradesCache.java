package net.ornithemc.ploceus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.Project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.Library.Target;

public class LibraryUpgradesCache {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Project project;
	private final PloceusGradleExtension ploceus;
	private final Map<String, List<Library>> libraries;
	private final Map<String, Path> librariesCache;

	private Integer generation;
	private String mcVersion;

	public LibraryUpgradesCache(Project project, PloceusGradleExtension ploceus) {
		this.project = project;
		this.ploceus = ploceus;
		this.libraries = new HashMap<>();
		this.librariesCache = new HashMap<>();
	}

	private int generation() {
		if (generation == null) {
			generation = ploceus.getGeneration().get();
		}

		return generation;
	}

	private String mcVersion() {
		if (mcVersion == null) {
			mcVersion = ploceus.minecraftVersion();
		}

		return mcVersion;
	}

	public List<Library> get() {
		List<Library> libs = null;

		try {
			libs = fromMeta();
		} catch (Exception me) {
			try {
				libs = fromCache();
			} catch (Exception ce) {
				throw new IllegalStateException("unable to read library upgrades from cache for gen" + generation() + " " + mcVersion(), ce);
			}

			if (libs == null) {
				throw new IllegalStateException("unable to fetch library upgrades from meta for gen" + generation() + " " + mcVersion() + ", and it is not in the cache", me);
			}
		}

		return libs;
	}

	private List<Library> fromMeta() throws Exception {
		String metaUrl = Constants.librariesMetaUrl(mcVersion(), generation());

		try (InputStreamReader ir = new InputStreamReader(new URL(metaUrl).openStream())) {
			JsonArray libsJson = GSON.fromJson(ir, JsonArray.class);
			List<Library> libs = new ArrayList<>();

			for (JsonElement e : libsJson) {
				if (!e.isJsonObject()) {
					continue;
				}

				JsonObject libJson = e.getAsJsonObject();
				String name = libJson.get("name").getAsString();

				libs.add(Library.fromMaven(name, Target.COMPILE));
			}

			libraries.put(mcVersion(), libs);

			try {
				saveToCache(libs);
			} catch (Exception e) {
				project.getLogger().warn("unable to save library upgrades for gen" + generation() + " " + mcVersion() + " to cache", e);
			}

			return libs;
		}
	}

	private List<Library> fromCache() throws Exception {
		Path libsCache = librariesCache.get(mcVersion());

		if (libsCache == null) {
			LoomGradleExtension loom = LoomGradleExtension.get(this.project);
			Path userCache = loom.getFiles().getUserCache().toPath();

			libsCache = userCache.resolve(mcVersion()).resolve("library-upgrades.json");
			librariesCache.put(mcVersion(), libsCache);
		}

		if (!Files.exists(libsCache)) {
			return null;
		}

		JsonObject json;

		try (BufferedReader br = Files.newBufferedReader(libsCache)) {
			json = GSON.fromJson(br, JsonObject.class);
		}

		List<Library> libs = new ArrayList<>();

		String generation = "gen" + generation();
		JsonArray libsJson = json.getAsJsonArray(generation);

		if (libsJson == null) {
			return null;
		}

		for (JsonElement libJson : libsJson) {
			String name = libJson.getAsString();
			Library lib = Library.fromMaven(name, Target.COMPILE);

			libs.add(lib);
		}

		return libs;
	}

	private void saveToCache(List<Library> libs) throws Exception {
		Path libsCache = librariesCache.get(mcVersion());

		if (libsCache == null) {
			LoomGradleExtension loom = LoomGradleExtension.get(this.project);
			Path userCache = loom.getFiles().getUserCache().toPath();

			libsCache = userCache.resolve(mcVersion()).resolve("library-upgrades.json");
			librariesCache.put(mcVersion(), libsCache);
		}

		JsonObject json = null;

		if (!Files.exists(libsCache)) {
			json = new JsonObject();
		}
		if (json == null) {
			try (BufferedReader br = Files.newBufferedReader(libsCache)) {
				json = GSON.fromJson(br, JsonObject.class);
			}
		}

		String generation = "gen" + generation();
		JsonArray libsJson = new JsonArray();

		json.add(generation, libsJson);

		for (Library lib : libs) {
			libsJson.add(lib.mavenNotation());
		}

		Files.createDirectories(libsCache.getParent());
		
		try (BufferedWriter bw = Files.newBufferedWriter(libsCache)) {
			GSON.toJson(json, bw);
		}
	}
}
