package net.ornithemc.ploceus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Map;

import org.gradle.api.Project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.configuration.DependencyInfo;

import net.ornithemc.ploceus.manifest.Manifest;
import net.ornithemc.ploceus.manifest.VersionDetails;
import net.ornithemc.ploceus.mappings.SidedIntermediaryMappingsProvider;
import net.ornithemc.ploceus.mcp.McpMappingsSpec;
import net.ornithemc.ploceus.nester.NestedMappingsSpec;
import net.ornithemc.ploceus.nester.NesterProcessor;
import net.ornithemc.ploceus.nester.NestsProvider;

public class PloceusGradleExtension {

	private static final Gson GSON = new GsonBuilder().create();

	public static PloceusGradleExtension get(Project project) {
		return (PloceusGradleExtension)project.getExtensions().getByName("ploceus");
	}

	private final Project project;
	private final LoomGradleExtension loom;
	private final OslVersionCache oslVersions;
	private final CommonLibraries commonLibraries;

	public PloceusGradleExtension(Project project) {
		this.project = project;
		this.loom = LoomGradleExtension.get(this.project);
		this.oslVersions = new OslVersionCache(this.project);
		this.commonLibraries = new CommonLibraries(this.project, this);

		apply();
	}

	private void apply() {
		project.getConfigurations().register(Constants.NESTS_CONFIGURATION);
		project.getExtensions().getExtraProperties().set(Constants.VERSION_MANIFEST_PROPERTY, Constants.VERSION_MANIFEST_URL);

		loom.addMinecraftJarProcessor(NesterProcessor.class, this);
		setIntermediaryProvider(GameSide.MERGED);
	}

	public NestsProvider getNestsProvider() {
		return NestsProvider.of(project);
	}

	public NestedMappingsSpec nestedMappings() {
		return new NestedMappingsSpec(this);
	}

	public McpMappingsSpec mcpMappings(String channel, String build) {
		return mcpMappings(channel, DependencyInfo.create(project, Constants.MINECRAFT_CONFIGURATION).getDependency().getVersion(), build);
	}

	public McpMappingsSpec mcpMappings(String channel, String mc, String build) {
		return new McpMappingsSpec(
			FileSpec.create(String.format(Constants.CALAMUS_INTERMEDIARY_MAVEN_GROUP + ":" + Constants.CALAMUS_INTERMEDIARY_MAPPINGS, mc)),
			FileSpec.create(String.format(Constants.MCP_MAVEN_GROUP + ":" + Constants.SRG_MAPPINGS, mc)),
			FileSpec.create(String.format(Constants.MCP_MAVEN_GROUP + ":" + Constants.MCP_MAPPINGS, channel, build, mc))
		);
	}

	public void dependOsl(String version) throws Exception {
		dependOsl(version, GameSide.MERGED);
	}

	public void dependOsl(String version, String side) throws Exception {
		dependOsl(version, GameSide.of(side));
	}

	public void dependOsl(String version, GameSide side) throws Exception {
		for (Map.Entry<String, String> entry : oslVersions.getDependencies(version).entrySet()) {
			String module = entry.getKey();
			String baseVersion = entry.getValue();
			String moduleVersion = oslVersions.getVersion(module, baseVersion, side);

			// not all modules cover all Minecraft versions
			// so check if a valid module version exists for
			// this Minecraft version before adding the dependency
			if (moduleVersion != null) {
				addOslModuleDependency(module, moduleVersion);
			}
		}
	}

	public void dependOslModule(String module, String version) throws Exception {
		dependOslModule(module, version, GameSide.MERGED);
	}

	public void dependOslModule(String module, String version, String side) throws Exception {
		dependOslModule(module, version, GameSide.of(side));
	}

	public void dependOslModule(String module, String version, GameSide side) throws Exception {
		addOslModuleDependency(module, oslModule(module, version, side));
	}

	public String oslModule(String module, String version) throws Exception {
		return oslModule(module, version, GameSide.MERGED);
	}

	public String oslModule(String module, String version, String side) throws Exception {
		return oslModule(module, version, GameSide.of(side));
	}

	public String oslModule(String module, String version, GameSide side) throws Exception {
		String moduleVersion = oslVersions.getVersion(module, version, side);

		if (moduleVersion == null) {
			throw new RuntimeException("osl " + module + " version " + version + " for " + side.id() + " does not exist");
		}

		return moduleVersion;
	}

	private void addOslModuleDependency(String module, String version) {
		project.getDependencies().add("modImplementation", String.format("%s:%s:%s",
			Constants.OSL_MAVEN_GROUP,
			module,
			version));
	}

	public void addCommonLibraries(String configuration) {
		commonLibraries.addDependencies(configuration);
	}

	public void clientOnlyMappings() {
		setIntermediaryProvider(GameSide.CLIENT);
	}

	public void serverOnlyMappings() {
		setIntermediaryProvider(GameSide.SERVER);
	}

	private void setIntermediaryProvider(GameSide side) {
		loom.getIntermediaryUrl().convention(Constants.CALAMUS_INTERMEDIARY_URL.replace("%1$s", "%1$s" + side.suffix()));

		loom.setIntermediateMappingsProvider(SidedIntermediaryMappingsProvider.class, provider -> {
			provider.configure(project, loom, side);
		});
	}

	public String normalizedMinecraftVersion() {
		// the normalized version id can be parsed from the version details file

		String versionId = loom.getMinecraftProvider().minecraftVersion();

		Path userCache = loom.getFiles().getUserCache().toPath();
		Path manifestCache = userCache.resolve("version_manifest.json");

		try (BufferedReader br = new BufferedReader(new FileReader(manifestCache.toFile()))) {
			Manifest manifest = GSON.fromJson(br, Manifest.class);
			Manifest.Version version = manifest.getVersion(versionId);

			String detailsUrl = version.details();
			File detailsCache = loom.getMinecraftProvider().file("minecraft-details.json");

			loom.download(detailsUrl).downloadPath(detailsCache.toPath());

			try (BufferedReader _br = new BufferedReader(new FileReader(manifestCache.toFile()))) {
				VersionDetails details = GSON.fromJson(_br, VersionDetails.class);
				return details.normalizedVersion();
			}
		} catch (Exception e) {
			project.getLogger().warn("unable to read version details, cannot normalize minecraft version id");
			return versionId;
		}
	}
}
