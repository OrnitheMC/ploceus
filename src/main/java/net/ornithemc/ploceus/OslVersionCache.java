package net.ornithemc.ploceus;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.gradle.api.Project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loom.configuration.DependencyInfo;

public class OslVersionCache {

	private final Project project;
	private final PloceusGradleExtension ploceus;
	private final Map<String, Map<String, String>> dependencies;
	private final Map<String, String> versions;

	private String mcVersion;

	public OslVersionCache(Project project, PloceusGradleExtension ploceus) {
		this.project = project;
		this.ploceus = ploceus;
		this.dependencies = new HashMap<>();
		this.versions = new HashMap<>();
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
		dependencies.put(version, modules);

		String metaUrl = String.format(Constants.META_URL + Constants.OSL_META_ENDPOINT, version);

		try (InputStreamReader input = new InputStreamReader(new URL(metaUrl).openStream())) {
			JsonElement json = JsonParser.parseReader(input);
			JsonArray array = json.getAsJsonArray();

			for (JsonElement moduleJson : array) {
				JsonObject moduleJsonObj = moduleJson.getAsJsonObject();
				String maven = moduleJsonObj.get("maven").getAsString();
				String moduleName = maven.split("[:]")[1];
				String moduleVersion = moduleJsonObj.get("version").getAsString();

				modules.put(moduleName, moduleVersion);
			}
		}

		return Collections.unmodifiableMap(modules);
	}

	public String getVersion(String module, String version, GameSide side) throws Exception {
		String key = module + version;
		String cachedVersion = versions.get(key);

		if (cachedVersion != null) {
			return cachedVersion;
		}

		String metaUrl = String.format(Constants.META_URL + Constants.OSL_MODULE_META_ENDPOINT,
			module,
			mcVersion(),
			version);

		try (InputStreamReader input = new InputStreamReader(new URL(metaUrl).openStream())) {
			JsonElement json = JsonParser.parseReader(input);
			JsonArray array = json.getAsJsonArray();

			for (JsonElement versionJson : array) {
				JsonObject versionJsonObj = versionJson.getAsJsonObject();
				String moduleVersion = versionJsonObj.get("version").getAsString();

				if (side == GameSide.MERGED || module.equals(Constants.OSL_CORE) || moduleVersion.contains(side.id())) {
					versions.put(key, cachedVersion = moduleVersion);
					break;
				}
			}
		}

		return cachedVersion;
	}
}
