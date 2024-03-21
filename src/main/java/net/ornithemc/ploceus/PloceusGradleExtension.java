package net.ornithemc.ploceus;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.util.ZipUtils;

import net.ornithemc.ploceus.api.GameSide;
import net.ornithemc.ploceus.api.PloceusGradleExtensionApi;
import net.ornithemc.ploceus.manifest.VersionDetails;
import net.ornithemc.ploceus.manifest.VersionsManifest;
import net.ornithemc.ploceus.mappings.LegacyCalamusProvider;
import net.ornithemc.ploceus.mappings.VersionedCalamusProvider;
import net.ornithemc.ploceus.mcp.McpForgeMappingsSpec;
import net.ornithemc.ploceus.mcp.McpModernMappingsSpec;
import net.ornithemc.ploceus.nester.NesterProcessor;
import net.ornithemc.ploceus.nester.NestsProvider;

public class PloceusGradleExtension implements PloceusGradleExtensionApi {

	private static final Gson GSON = new GsonBuilder().create();

	public static PloceusGradleExtension get(Project project) {
		return (PloceusGradleExtension)project.getExtensions().getByName("ploceus");
	}

	private final Project project;
	private final LoomGradleExtension loom;
	private final OslVersionCache oslVersions;
	private final CommonLibraries commonLibraries;
	private final Property<NestsProvider> nestsProvider;
	private final Property<GameSide> side; // gen 1
	private final Property<Integer> generation; // gen 2+

	public PloceusGradleExtension(Project project) {
		this.project = project;
		this.loom = LoomGradleExtension.get(this.project);
		this.oslVersions = new OslVersionCache(this.project, this);
		this.commonLibraries = new CommonLibraries(this.project, this);
		this.nestsProvider = project.getObjects().property(NestsProvider.class);
		this.nestsProvider.convention(project.provider(() -> {
			int generation = getGeneration().get();

			NestsProvider provider;
			if (generation > 1 && loom.getMinecraftProvider().isLegacyVersion()) {
				provider = new NestsProvider.Split(project, loom, this);
			} else {
				provider = new NestsProvider.Simple(project, loom, this);
			}
			provider.provide();

			return provider;
		}));
		this.nestsProvider.finalizeValueOnRead();
		this.side = project.getObjects().property(GameSide.class);
		this.side.convention(GameSide.MERGED);
		this.generation = project.getObjects().property(int.class);
		this.generation.convention(2);

		apply();
	}

	private void apply() {
		project.getConfigurations().register(Constants.NESTS_CONFIGURATION);
		project.getConfigurations().register(Constants.CLIENT_NESTS_CONFIGURATION);
		project.getConfigurations().register(Constants.SERVER_NESTS_CONFIGURATION);
		project.getExtensions().getExtraProperties().set(Constants.VERSION_MANIFEST_PROPERTY, Constants.VERSION_MANIFEST_URL);

		loom.setIntermediateMappingsProvider(VersionedCalamusProvider.class, provider -> {
			provider.getGeneration()
				.convention(getGeneration())
				.finalizeValueOnRead();
			provider.getIntermediaryUrl()
				.convention(project.provider(() -> String.format(Constants.VERSIONED_CALAMUS_URL, provider.getGeneration().get(), provider.getGeneration().get())))
				.finalizeValueOnRead();
			provider.getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
		});
		loom.addMinecraftJarProcessor(NesterProcessor.class, this);

		project.getTasks().configureEach(task -> {
            if (task instanceof AbstractRemapJarTask remapJarTask) {
                remapJarTask.doLast(task1 -> {
                    try {
                        ZipUtils.transform(remapJarTask.getArchiveFile().get().getAsFile().toPath(), Map.of(Constants.MANIFEST_PATH, bytes -> {
                            Manifest manifest = new Manifest(new ByteArrayInputStream(bytes));

                            Attributes attributes = manifest.getMainAttributes();
                            attributes.putValue(Constants.CALAMUS_GENERATION_ATTRIBUTE, getGeneration().get().toString());

                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            manifest.write(out);
                            return out.toByteArray();
                        }));
                    } catch (IOException e) {
                        throw new UncheckedIOException("unable to transform remapped jar manifest!", e);
                    }
                });
            }
        });
	}

	public NestsProvider getNestsProvider() {
		return nestsProvider.get();
	}

	@Override
	public McpModernMappingsSpec mcpMappings(String channel, String build) {
		return mcpMappings(channel, DependencyInfo.create(project, Constants.MINECRAFT_CONFIGURATION).getDependency().getVersion(), build);
	}

	@Override
	public McpModernMappingsSpec mcpMappings(String channel, String mc, String build) {
		return new McpModernMappingsSpec(
			FileSpec.create(String.format(Constants.CALAMUS_INTERMEDIARY_MAVEN_GROUP + ":" + Constants.LEGACY_CALAMUS_MAPPINGS, mc)),
			FileSpec.create(String.format(Constants.MCP_MAVEN_GROUP + ":" + Constants.SRG_MAPPINGS, mc)),
			FileSpec.create(String.format(Constants.MCP_MAVEN_GROUP + ":" + Constants.MCP_MAPPINGS, channel, build, mc))
		);
	}

	@Override
	public McpForgeMappingsSpec mcpForgeMappings(String version) {
		return mcpForgeMappings(DependencyInfo.create(project, Constants.MINECRAFT_CONFIGURATION).getDependency().getVersion(), version);
	}

	@Override
	public McpForgeMappingsSpec mcpForgeMappings(String mc, String version) {
		return new McpForgeMappingsSpec(
			FileSpec.create(String.format(Constants.CALAMUS_INTERMEDIARY_MAVEN_GROUP + ":" + Constants.LEGACY_CALAMUS_MAPPINGS, mc)),
			FileSpec.create(String.format(Constants.FORGE_MAVEN_GROUP+ ":" + Constants.FORGE_SRC, mc, version))
		);
	}

	@Override
	public void dependOsl(String version) throws Exception {
		dependOsl(version, GameSide.MERGED);
	}

	@Override
	public void dependOsl(String version, String side) throws Exception {
		dependOsl(version, GameSide.of(side));
	}

	@Override
	public void dependOsl(String version, GameSide side) throws Exception {
		dependOsl("modImplementation", version, side);
	}

	@Override
	public void dependOsl(String configuration, String version, GameSide side) throws Exception {
		for (Map.Entry<String, String> entry : oslVersions.getDependencies(version).entrySet()) {
			String module = entry.getKey();
			String baseVersion = entry.getValue();
			String moduleVersion = oslVersions.getVersion(module, baseVersion, side);

			// not all modules cover all Minecraft versions
			// so check if a valid module version exists for
			// this Minecraft version before adding the dependency
			if (moduleVersion != null) {
				addOslModuleDependency(configuration, module, moduleVersion);
			}
		}
	}

	@Override
	public void dependOslModule(String module, String version) throws Exception {
		dependOslModule(module, version, GameSide.MERGED);
	}

	@Override
	public void dependOslModule(String module, String version, String side) throws Exception {
		dependOslModule(module, version, GameSide.of(side));
	}

	@Override
	public void dependOslModule(String module, String version, GameSide side) throws Exception {
		dependOslModule("modImplementation", module, version, side);
	}

	@Override
	public void dependOslModule(String configuration, String module, String version, GameSide side) throws Exception {
		addOslModuleDependency(configuration, module, oslModule(module, version, side));
	}

	@Override
	public String oslModule(String module, String version) throws Exception {
		return oslModule(module, version, GameSide.MERGED);
	}

	@Override
	public String oslModule(String module, String version, String side) throws Exception {
		return oslModule(module, version, GameSide.of(side));
	}

	@Override
	public String oslModule(String module, String version, GameSide side) throws Exception {
		String moduleVersion = oslVersions.getVersion(module, version, side);

		if (moduleVersion == null) {
			throw new RuntimeException("osl " + module + " version " + version + " for " + side.id() + " does not exist");
		}

		return moduleVersion;
	}

	private void addOslModuleDependency(String configuration, String module, String version) {
		project.getDependencies().add(configuration, String.format("%s:%s:%s",
			Constants.OSL_MAVEN_GROUP,
			module,
			version));
	}

	@Override
	public void addCommonLibraries() {
		addCommonLibraries("implementation");
	}

	@Override
	public void addCommonLibraries(String configuration) {
		commonLibraries.addDependencies(configuration);
	}

	@Override
	public void clientOnlyMappings() {
		side.set(GameSide.CLIENT);
		legacyCalamusProvider();
	}

	@Override
	public void serverOnlyMappings() {
		side.set(GameSide.SERVER);
		legacyCalamusProvider();
	}

	private void legacyCalamusProvider() {
		generation.set(1);
		loom.setIntermediateMappingsProvider(LegacyCalamusProvider.class, provider -> {
			provider.getSide()
				.convention(side)
				.finalizeValueOnRead();
			provider.getIntermediaryUrl()
				.convention(project.provider(() -> String.format(Constants.LEGACY_CALAMUS_URL, provider.getSide().get().suffix(), provider.getSide().get().suffix())))
				.finalizeValueOnRead();
			provider.getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
		});
	}

	@Override
	public Property<Integer> getGeneration() {
		return generation;
	}

	public String minecraftVersion() {
		return DependencyInfo.create(project, Constants.MINECRAFT_CONFIGURATION).getDependency().getVersion();
	}

	public String normalizedMinecraftVersion() {
		// the normalized version id can be parsed from the version details file

		String versionId = minecraftVersion();

		Path userCache = loom.getFiles().getUserCache().toPath();
		Path manifestCache = userCache.resolve("skyrising_version_manifest.json");

		try {
			if (!Files.exists(manifestCache)) {
				loom.download(Constants.VERSION_MANIFEST_URL).downloadPath(manifestCache);
			}

			try (BufferedReader br = new BufferedReader(new FileReader(manifestCache.toFile()))) {
				VersionsManifest manifest = GSON.fromJson(br, VersionsManifest.class);
				VersionsManifest.Version version = manifest.getVersion(versionId);

				String detailsUrl = version.details();
				Path detailsCache = userCache.resolve(versionId).resolve("minecraft-details.json");

				if (!Files.exists(detailsCache)) {
					loom.download(detailsUrl).downloadPath(detailsCache);
				}

				try (BufferedReader _br = new BufferedReader(new FileReader(detailsCache.toFile()))) {
					VersionDetails details = GSON.fromJson(_br, VersionDetails.class);
					return details.normalizedVersion();
				}
			}
		} catch (Exception e) {
			project.getLogger().warn("unable to read version details, cannot normalize minecraft version id", e);
			return versionId;
		}
	}
}
