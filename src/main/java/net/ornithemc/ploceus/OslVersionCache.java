package net.ornithemc.ploceus;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.internal.impldep.com.google.gson.JsonArray;
import org.gradle.internal.impldep.com.google.gson.JsonElement;
import org.gradle.internal.impldep.com.google.gson.JsonObject;
import org.gradle.internal.impldep.com.google.gson.JsonParser;

import net.fabricmc.loom.configuration.DependencyInfo;

public class OslVersionCache {

	private final Project project;
	private final Map<String, String> cache;

	private String mcVersion;

	public OslVersionCache(Project project) {
		this.project = project;
		this.cache = new HashMap<>();
	}

	private String mcVersion() {
		if (mcVersion == null) {
			mcVersion = DependencyInfo.create(project, Constants.MINECRAFT_CONFIGURATION).getDependency().getVersion();
		}

		return mcVersion;
	}

	public String get(String module, String version) throws Exception {
		String key = module + version;
		String cachedVersion = cache.get(key);

		if (cachedVersion != null) {
			return cachedVersion;
		}

		String metaUrl = String.format(Constants.META_URL + Constants.OSL_META_ENDPOINT,
			module,
			mcVersion(),
			version);

		try (InputStreamReader input = new InputStreamReader(new URL(metaUrl).openStream())) {
			JsonElement json = JsonParser.parseReader(input);
			JsonArray array = json.getAsJsonArray();

			if (!array.isEmpty()) {
				JsonElement versionJson = array.get(0);
				JsonObject versionJsonObj = versionJson.getAsJsonObject();

				cache.put(key, cachedVersion = versionJsonObj.get("version").getAsString());
			}
		}

		return cachedVersion;
	}
}
