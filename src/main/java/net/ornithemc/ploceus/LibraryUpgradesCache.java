package net.ornithemc.ploceus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

	private Integer intermediaryGeneration;
	private String minecraftVersion;
	private List<Library> libraries;
	private Path librariesCache;

	public LibraryUpgradesCache(Project project, PloceusGradleExtension ploceus) {
		this.project = project;
		this.ploceus = ploceus;
	}

	private int intermediaryGeneration() {
		if (intermediaryGeneration == null) {
			intermediaryGeneration = ploceus.getIntermediaryGeneration().get();
		}

		return intermediaryGeneration;
	}

	private String minecraftVersion() {
		if (minecraftVersion == null) {
			minecraftVersion = ploceus.minecraftVersion();
		}

		return minecraftVersion;
	}

	private Path librariesCache() {
		if (librariesCache == null) {
			LoomGradleExtension loom = LoomGradleExtension.get(project);
			Path userCache = loom.getFiles().getUserCache().toPath();

			librariesCache = userCache.resolve(minecraftVersion()).resolve("library-upgrades.json");
		}

		return librariesCache;
	}

	public List<Library> getLibraryUpgrades() {
		if (libraries == null) {
			libraries = getLibraries();
		}

		return libraries;
	}

	private List<Library> getLibraries() {
		List<Library> libs = null;

		try {
			libs = getLibrariesFromMeta();
		} catch (Exception me) {
			try {
				libs = getLibrariesFromCache();
			} catch (Exception ce) {
				project.getLogger().warn("unable to read library upgrades from cache for gen" + intermediaryGeneration() + " " + minecraftVersion(), ce);
			}

			if (libs == null) {
				throw new IllegalStateException("unable to fetch library upgrades from meta for gen" + intermediaryGeneration() + " " + minecraftVersion() + ", and it is not in the cache", me);
			}
		}

		return libs;
	}

	private List<Library> getLibrariesFromMeta() throws Exception {
		String metaUrl = Constants.librariesMetaUrl(minecraftVersion(), intermediaryGeneration());

		try (InputStreamReader ir = new InputStreamReader(new URI(metaUrl).toURL().openStream())) {
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

			try {
				saveLibrariesToCache(libs);
			} catch (Exception e) {
				project.getLogger().warn("unable to save library upgrades for gen" + intermediaryGeneration() + " " + minecraftVersion() + " to cache", e);
			}

			return libs;
		}
	}

	private List<Library> getLibrariesFromCache() throws Exception {
		Path libsCache = librariesCache();
		JsonObject json;

		try (BufferedReader br = Files.newBufferedReader(libsCache)) {
			json = GSON.fromJson(br, JsonObject.class);
		} catch (FileNotFoundException e) {
			return null;
		}

		String generation = "gen" + intermediaryGeneration();
		JsonArray libsJson = json.getAsJsonArray(generation);

		if (libsJson == null) {
			return null;
		}

		List<Library> libs = new ArrayList<>();

		for (JsonElement libJson : libsJson) {
			String name = libJson.getAsString();
			Library lib = Library.fromMaven(name, Target.COMPILE);

			libs.add(lib);
		}

		return libs;
	}

	private void saveLibrariesToCache(List<Library> libs) throws Exception {
		Path libsCache = librariesCache();
		JsonObject json;

		try (BufferedReader br = Files.newBufferedReader(libsCache)) {
			json = GSON.fromJson(br, JsonObject.class);
		} catch (FileNotFoundException e) {
			json = new JsonObject();
		}

		String generation = "gen" + intermediaryGeneration();
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
