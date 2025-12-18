package net.ornithemc.ploceus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.gradle.api.Project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.loom.LoomGradleExtension;

import net.ornithemc.ploceus.api.GameSide;

public class OslVersionCache {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Project project;
	private final PloceusGradleExtension ploceus;
	private final Map<String, Map<String, String>> moduleBaseVersions;
	private final Map<String, String> moduleVersions;

	private Integer intermediaryGeneration;
	private String minecraftVersion;
	private Path moduleBaseVersionsCache;
	private Path moduleVersionsCache;

	public OslVersionCache(Project project, PloceusGradleExtension ploceus) {
		this.project = project;
		this.ploceus = ploceus;
		this.moduleBaseVersions = new HashMap<>();
		this.moduleVersions = new HashMap<>();
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

	private Path moduleBaseVersionsCache() {
		if (moduleBaseVersionsCache == null) {
			LoomGradleExtension loom = LoomGradleExtension.get(project);
			Path userCache = loom.getFiles().getUserCache().toPath();

			// avoid conflicts with previous Ploceus versions, which used 'osl-versions.json'
			moduleBaseVersionsCache = userCache.resolve("osl-base-versions.json");
		}

		return moduleBaseVersionsCache;
	}

	private Path moduleVersionsCache() {
		if (moduleVersionsCache == null) {
			LoomGradleExtension loom = LoomGradleExtension.get(project);
			Path userCache = loom.getFiles().getUserCache().toPath();

			// avoid conflicts with previous Ploceus versions, which used 'osl-module-versions.json'
			moduleVersionsCache = userCache.resolve(minecraftVersion()).resolve("osl-versions.json");
		}

		return moduleVersionsCache;
	}

	public Map<String, String> getOslModuleBaseVersions(String version) {
		Map<String, String> modules = moduleBaseVersions.get(version);

		if (modules == null) {
			moduleBaseVersions.put(version, modules = getModuleBaseVersions(version));
		}

		return modules;
	}

	public String getOslModuleBaseVersion(String version, String module) {
		return getOslModuleBaseVersions(version).get(module);
	}

	private Map<String, String> getModuleBaseVersions(String version) {
		Map<String, String> baseVersions = null;

		try {
			baseVersions = getModuleBaseVersionsFromMeta(version);
		} catch (Exception me) {
			try {
				baseVersions = getModuleBaseVersionsFromCache(version);
			} catch (Exception ce) {
				project.getLogger().warn("unable to read OSL module base versions from cache for gen" + intermediaryGeneration() + " " + version, ce);
			}

			if (baseVersions == null) {
				throw new IllegalStateException("unable to fetch OSL module base versions from meta for gen" + intermediaryGeneration() + " " + version + ", and it is not in the cache", me);
			}
		}

		return baseVersions;
	}

	private Map<String, String> getModuleBaseVersionsFromMeta(String version) throws Exception {
		String metaUrl = Constants.META_URL + Constants.oslVersionMetaEndpoint(intermediaryGeneration(), version);

		try (InputStreamReader ir = new InputStreamReader(new URI(metaUrl).toURL().openStream())) {
			JsonArray modulesJson = GSON.fromJson(ir, JsonArray.class);
			Map<String, String> baseVersions = new LinkedHashMap<>();

			for (JsonElement e : modulesJson) {
				if (!e.isJsonObject()) {
					continue;
				}

				JsonObject moduleJson = e.getAsJsonObject();
				String maven = moduleJson.get("maven").getAsString();
				String moduleName = maven.split("[:]")[1];
				String moduleVersion = moduleJson.get("version").getAsString();

				baseVersions.put(moduleName, moduleVersion);
			}

			try {
				saveModuleBaseVersionsToCache(version, baseVersions);
			} catch (Exception e) {
				project.getLogger().warn("unable to save OSL module base versions for gen" + intermediaryGeneration() + " " + version + " to cache", e);
			}

			return baseVersions;
		}
	}

	private Map<String, String> getModuleBaseVersionsFromCache(String version) throws Exception {
		Path baseVersionsCache = moduleBaseVersionsCache();
		JsonObject json;

		try (BufferedReader br = Files.newBufferedReader(baseVersionsCache)) {
			json = GSON.fromJson(br, JsonObject.class);
		} catch (NoSuchFileException e) {
			return null;
		}

		String generation = "gen" + intermediaryGeneration();
		JsonObject versionsJson = json.getAsJsonObject(generation);

		if (versionsJson == null) {
			return null;
		}

		JsonObject baseVersionsJson = versionsJson.getAsJsonObject(version);

		if (baseVersionsJson == null) {
			return null;
		}

		Map<String, String> baseVersions = new HashMap<>();

		for (Map.Entry<String, JsonElement> e : baseVersionsJson.entrySet()) {
			JsonElement baseVersionJson = e.getValue();

			if (!baseVersionJson.isJsonPrimitive()) {
				continue;
			}

			String moduleName = e.getKey();
			String moduleVersion = baseVersionJson.getAsString();

			baseVersions.put(moduleName, moduleVersion);
		}

		return baseVersions;
	}

	private void saveModuleBaseVersionsToCache(String version, Map<String, String> baseVersions) throws Exception {
		Path baseVersionsCache = moduleBaseVersionsCache();
		JsonObject json = null;

		try (BufferedReader br = Files.newBufferedReader(baseVersionsCache)) {
			json = GSON.fromJson(br, JsonObject.class);
		} catch (NoSuchFileException e) {
			json = new JsonObject();
		}

		String generation = "gen" + intermediaryGeneration();
		JsonObject versionsJson = json.getAsJsonObject(generation);

		if (versionsJson == null) {
			versionsJson = new JsonObject();
			json.add(generation, versionsJson);
		}

		JsonObject baseVersionsJson = new JsonObject();
		versionsJson.add(version, baseVersionsJson);

		for (Map.Entry<String, String> e : baseVersions.entrySet()) {
			String moduleName = e.getKey();
			String moduleVersion = e.getValue();

			baseVersionsJson.addProperty(moduleName, moduleVersion);
		}

		Files.createDirectories(baseVersionsCache.getParent());

		try (BufferedWriter bw = Files.newBufferedWriter(baseVersionsCache)) {
			GSON.toJson(json, bw);
		}
	}

	public String getOslModuleVersion(String module, String version, GameSide side) {
		String moduleVersion = moduleVersions.get(module + version + side.suffix());

		if (moduleVersion == null) {
			moduleVersions.put(module + version + side.suffix(), moduleVersion = getModuleVersion(module, version, side));
		}

		return moduleVersion;
	}

	private String getModuleVersion(String module, String version, GameSide side) {
		String moduleVersion = null;

		try {
			moduleVersion = getModuleVersionFromMeta(module, version, side);
		} catch (Exception me) {
			try {
				moduleVersion = getModuleVersionFromCache(module, version, side);
			} catch (Exception ce) {
				project.getLogger().warn("unable to read OSL module version from cache for gen" + intermediaryGeneration() + " " + module + " " + version, ce);
			}

			if (moduleVersion == null) {
				throw new IllegalStateException("unable to fetch OSL module version from meta for gen" + intermediaryGeneration() + " " + module + " " + version + ", and it is not in the cache", me);
			}
		}

		return moduleVersion;
	}

	private String getModuleVersionFromMeta(String module, String version, GameSide side) throws Exception {
		String metaUrl = Constants.META_URL + Constants.oslModuleVersionMetaEndpoint(intermediaryGeneration(), module, minecraftVersion(), version);

		try (InputStreamReader ir = new InputStreamReader(new URI(metaUrl).toURL().openStream())) {
			JsonArray modulesJson = GSON.fromJson(ir, JsonArray.class);
			String moduleVersion = null;

			for (JsonElement e : modulesJson) {
				if (!e.isJsonObject()) {
					continue;
				}

				JsonObject moduleJson = e.getAsJsonObject();
				moduleVersion = moduleJson.get("version").getAsString();

				if (moduleVersion.contains(side.id()) || (!moduleVersion.contains(GameSide.CLIENT.id()) && !moduleVersion.contains(GameSide.SERVER.id()))) {
					break;
				} else {
					moduleVersion = null;
				}
			}

			try {
				saveModuleVersionToCache(module, version, side, moduleVersion);
			} catch (Exception e) {
				project.getLogger().warn("unable to save OSL module versions for gen" + intermediaryGeneration() + " " + module + " " + version + " to cache", e);
			}

			return moduleVersion;
		}
	}

	private String getModuleVersionFromCache(String module, String version, GameSide side) throws Exception {
		Path versionsCache = moduleVersionsCache();
		JsonObject json;

		try (BufferedReader br = Files.newBufferedReader(versionsCache)) {
			json = GSON.fromJson(br, JsonObject.class);
		} catch (NoSuchFileException e) {
			return null;
		}

		String generation = "gen" + intermediaryGeneration();
		JsonObject versionsJson = json.getAsJsonObject(generation);

		if (versionsJson == null) {
			return null;
		}

		JsonObject moduleJson = versionsJson.getAsJsonObject(module);

		if (moduleJson == null) {
			return null;
		}

		JsonObject moduleVersionJson = moduleJson.getAsJsonObject(version);

		if (moduleVersionJson == null) {
			return null;
		}

		JsonElement moduleVersion = moduleVersionJson.get(side.id());

		if (moduleVersion == null) {
			return null;
		}

		return moduleVersion.getAsString();
	}

	private void saveModuleVersionToCache(String module, String version, GameSide side, String moduleVersion) throws Exception {
		Path versionsCache = moduleVersionsCache();
		JsonObject json = null;

		try (BufferedReader br = Files.newBufferedReader(versionsCache)) {
			json = GSON.fromJson(br, JsonObject.class);
		} catch (NoSuchFileException e) {
			json = new JsonObject();
		}

		String generation = "gen" + intermediaryGeneration();
		JsonObject modulesJson = json.getAsJsonObject(generation);

		if (modulesJson == null) {
			modulesJson = new JsonObject();
			json.add(generation, modulesJson);
		}

		JsonObject moduleJson = modulesJson.getAsJsonObject(module);

		if (moduleJson == null) {
			moduleJson = new JsonObject();
			modulesJson.add(module, moduleJson);
		}

		JsonObject moduleVersionJson = moduleJson.getAsJsonObject(version);

		if (moduleVersionJson == null) {
			moduleVersionJson = new JsonObject();
			moduleJson.add(version, moduleVersionJson);
		}

		moduleVersionJson.addProperty(side.id(), moduleVersion);

		Files.createDirectories(versionsCache.getParent());

		try (BufferedWriter bw = Files.newBufferedWriter(versionsCache)) {
			GSON.toJson(json, bw);
		}
	}
}
