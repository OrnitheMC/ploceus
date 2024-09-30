package net.ornithemc.ploceus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
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
	private final Map<String, Map<String, String>> dependencies;
	private final Map<String, String> versions;
	private final Path oslVersionCache;
	private final Map<String, Path> oslModuleVersionCache;

	private String mcVersion;

	public OslVersionCache(Project project, PloceusGradleExtension ploceus) {
		LoomGradleExtension loom = LoomGradleExtension.get(project);
		Path userCache = loom.getFiles().getUserCache().toPath();

		this.project = project;
		this.ploceus = ploceus;
		this.dependencies = new HashMap<>();
		this.versions = new HashMap<>();
		this.oslVersionCache = userCache.resolve("osl-versions.json");
		this.oslModuleVersionCache = new HashMap<>();
	}

	private String mcVersion() {
		if (mcVersion == null) {
			mcVersion = ploceus.minecraftVersion();
		}

		return mcVersion;
	}

	public Map<String, String> getDependencies(String version) throws Exception {
		Map<String, String> modules = dependencies.get(version);

		if (modules != null) {
			return Collections.unmodifiableMap(modules);
		}

		modules = new HashMap<>();
		JsonArray modulesJson = queryOslModules(version);

		for (JsonElement moduleJson : modulesJson) {
			JsonObject moduleJsonObj = moduleJson.getAsJsonObject();
			String maven = moduleJsonObj.get("maven").getAsString();
			String moduleName = maven.split("[:]")[1];
			String moduleVersion = moduleJsonObj.get("version").getAsString();

			modules.put(moduleName, moduleVersion);
		}

		// add this entry to the map only after the meta queries
		// if they fail, we might add an empty map which is invalid
		if (modules.isEmpty()) {
			throw new RuntimeException("no OSL modules found for OSL " + version);
		} else {
			dependencies.put(version, modules);
		}

		return Collections.unmodifiableMap(modules);
	}

	/**
	 * checks if the osl version cache contains the specified version
	 * and queries the meta server for this data if needed
	 * 
	 * @return the json array with the maven data for all the modules
	 *         for this osl version
	 */
	private JsonArray queryOslModules(String version) throws Exception {
		JsonObject json = null;

		// if the file does not exist, we create it
		// and we cache the json object so we do not
		// unnecessarily read the file for that again
		if (!Files.exists(oslVersionCache)) {
			json = new JsonObject();

			// no need to write this to disk at this point
			// the data is empty so the meta will be queried
			// and once the data has been added the file will
			// be written to disk
		}
		if (json == null) {
			try (BufferedReader br = new BufferedReader(new FileReader(oslVersionCache.toFile()))) {
				json = GSON.fromJson(br, JsonObject.class);
			}
		}

		JsonArray modulesJson = json.getAsJsonArray(version);

		if (modulesJson == null) {
			String metaUrl = String.format(Constants.META_URL + Constants.OSL_META_ENDPOINT, version);

			try (InputStreamReader ir = new InputStreamReader(new URL(metaUrl).openStream())) {
				modulesJson = GSON.fromJson(ir, JsonArray.class);
				json.add(version, modulesJson);
			}
			Files.createDirectories(oslVersionCache.getParent());
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(oslVersionCache.toFile()))) {
				GSON.toJson(json, bw);
			}
		}

		return modulesJson;
	}

	public String getVersion(String module, String version, GameSide side) throws Exception {
		String key = module + version;
		String cachedVersion = versions.get(key);

		if (cachedVersion != null) {
			return cachedVersion;
		}

		JsonArray versionsJson = queryOslModuleVersions(module, version, side);

		for (JsonElement versionJson : versionsJson) {
			JsonObject versionJsonObj = versionJson.getAsJsonObject();
			String moduleVersion = versionJsonObj.get("version").getAsString();

			if (side == GameSide.MERGED || module.equals(Constants.OSL_CORE) || moduleVersion.contains(side.id())) {
				versions.put(key, cachedVersion = moduleVersion);
				break;
			}
		}

		return cachedVersion;
	}

	/**
	 * checks if the module version cache contains the specified version
	 * and queries the meta server for this data if needed
	 * 
	 * @return the json array with the maven data for all versions for
	 *         this module version and mc version
	 */
	private JsonArray queryOslModuleVersions(String module, String version, GameSide side) throws Exception {
		String mcVersion = mcVersion();
		Path moduleVersionCache = oslModuleVersionCache.get(mcVersion);

		// the file can only be initialized after the Minecraft dependency
		// has been declared so initially we set the value to null
		if (moduleVersionCache == null) {
			LoomGradleExtension loom = LoomGradleExtension.get(this.project);
			Path userCache = loom.getFiles().getUserCache().toPath();

			moduleVersionCache = userCache.resolve(mcVersion).resolve("osl-module-versions.json");
			oslModuleVersionCache.put(mcVersion, moduleVersionCache);
		}

		JsonObject json = null;

		// if the file does not exist, we create it
		// and we cache the json object so we do not
		// unnecessarily read the file for that again
		if (!Files.exists(moduleVersionCache)) {
			json = new JsonObject();

			// no need to write this to disk at this point
			// the data is empty so the meta will be queried
			// and once the data has been added the file will
			// be written to disk
		}
		if (json == null) {
			try (BufferedReader br = new BufferedReader(new FileReader(moduleVersionCache.toFile()))) {
				json = GSON.fromJson(br, JsonObject.class);
			}
		}

		JsonObject moduleJson = json.getAsJsonObject(module);

		if (moduleJson == null) {
			moduleJson = new JsonObject();
			json.add(module, moduleJson);

			// no need to write this to disk at this point
			// the data is empty so the meta will be queried
			// and once the data has been added the file will
			// be written to disk
		}

		JsonArray versionsJson = moduleJson.getAsJsonArray(version);

		if (versionsJson == null) {
			String metaUrl = String.format(Constants.META_URL + Constants.OSL_MODULE_META_ENDPOINT,
				module,
				mcVersion,
				version);

			try (InputStreamReader ir = new InputStreamReader(new URL(metaUrl).openStream())) {
				versionsJson = GSON.fromJson(ir, JsonArray.class);
				moduleJson.add(version, versionsJson);
			}
			Files.createDirectories(moduleVersionCache.getParent());
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(moduleVersionCache.toFile()))) {
				GSON.toJson(json, bw);
			}
		}

		return versionsJson;
	}
}
