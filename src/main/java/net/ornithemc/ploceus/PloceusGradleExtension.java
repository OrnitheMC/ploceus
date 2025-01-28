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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Property;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.util.ZipUtils;

import net.ornithemc.ploceus.api.GameSide;
import net.ornithemc.ploceus.api.PloceusGradleExtensionApi;
import net.ornithemc.ploceus.manifest.VersionDetails;
import net.ornithemc.ploceus.manifest.VersionsManifest;
import net.ornithemc.ploceus.mappings.CalamusGen1Provider;
import net.ornithemc.ploceus.mappings.CalamusGen2Provider;
import net.ornithemc.ploceus.mcp.McpForgeMappingsSpec;
import net.ornithemc.ploceus.mcp.McpModernMappingsSpec;
import net.ornithemc.ploceus.nester.NesterProcessor;
import net.ornithemc.ploceus.nester.NestsMappingSpec;
import net.ornithemc.ploceus.nester.NestsProvider;
import net.ornithemc.ploceus.signatures.SignaturePatcherProcessor;
import net.ornithemc.ploceus.signatures.SigsProvider;

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
	private final Property<SigsProvider> sigsProvider;
	private final Property<GameSide> side; // gen 1
	private final Property<Integer> generation; // gen 2+

	public PloceusGradleExtension(Project project) {
		this.project = project;
		this.loom = LoomGradleExtension.get(this.project);
		this.oslVersions = new OslVersionCache(this.project, this);
		this.commonLibraries = new CommonLibraries(this.project, this);
		this.nestsProvider = project.getObjects().property(NestsProvider.class);
		this.nestsProvider.convention(project.provider(() -> {
			NestsProvider provider;
			if (loom.getMinecraftProvider().isLegacyVersion()) {
				if (getGeneration().get() == 1) {
					provider = new NestsProvider.Legacy(project, loom, this, getSide().get());
				} else {
					provider = new NestsProvider.Split(project, loom, this);
				}
			} else {
				provider = new NestsProvider.Simple(project, loom, this);
			}
			provider.provide();

			return provider;
		}));
		this.sigsProvider = project.getObjects().property(SigsProvider.class);
		this.sigsProvider.convention(project.provider(() -> {
			SigsProvider provider;
			if (loom.getMinecraftProvider().isLegacyVersion()) {
				provider = new SigsProvider.Split(project, loom, this);
			} else {
				provider = new SigsProvider.Simple(project, loom, this);
			}
			provider.provide();

			return provider;
		}));
		this.nestsProvider.finalizeValueOnRead();
		this.side = project.getObjects().property(GameSide.class);
		this.side.convention(GameSide.MERGED);
		this.generation = project.getObjects().property(int.class);
		this.generation.convention(1);

		apply();
	}

	private void apply() {
		project.getConfigurations().register(Constants.NESTS_CONFIGURATION);
		project.getConfigurations().register(Constants.CLIENT_NESTS_CONFIGURATION);
		project.getConfigurations().register(Constants.SERVER_NESTS_CONFIGURATION);
		project.getConfigurations().register(Constants.SIGNATURES_CONFIGURATION);
		project.getConfigurations().register(Constants.CLIENT_SIGNATURES_CONFIGURATION);
		project.getConfigurations().register(Constants.SERVER_SIGNATURES_CONFIGURATION);

		loom.addMinecraftJarProcessor(NesterProcessor.class, this);
		loom.addMinecraftJarProcessor(SignaturePatcherProcessor.class, this);

		project.getTasks().configureEach(task -> {
            if (task instanceof AbstractRemapJarTask remapJarTask) {
                remapJarTask.doLast(task1 -> {
                    try {
                        ZipUtils.transform(remapJarTask.getArchiveFile().get().getAsFile().toPath(), Map.of(Constants.MANIFEST_PATH, bytes -> {
                            Manifest manifest = new Manifest(new ByteArrayInputStream(bytes));

                            Attributes attributes = manifest.getMainAttributes();
                            attributes.putValue(Constants.CALAMUS_GENERATION_ATTRIBUTE, generation.get().toString());

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

		switchToGen1();
	}

	public NestsProvider getNestsProvider() {
		return nestsProvider.get();
	}

	public SigsProvider getSigsProvider() {
		return sigsProvider.get();
	}

	@Override
	public Dependency featherMappings(String build) {
		return layeredMappings(builder -> {
			builder.mappings(project.getDependencies().create(
				generation.get() == 1
					? Constants.featherGen1Mappings(minecraftVersion(), side.get(), build)
					: Constants.featherGen2Mappings(generation.get(), minecraftVersion(), build)
			));
		});
	}

	@Override
	public Dependency mcpMappings(String channel, String build) {
		return mcpMappings(channel, DependencyInfo.create(project, Constants.MINECRAFT_CONFIGURATION).getDependency().getVersion(), build);
	}

	@Override
	public Dependency mcpMappings(String channel, String mc, String build) {
		return layeredMappings(builder -> {
			builder.addLayer(new McpModernMappingsSpec(
				generation.get() == 1
					? FileSpec.create(Constants.calamusGen1Mappings(mc, side.get()))
					: FileSpec.create(Constants.calamusGen2Mappings(mc, generation.get())),
				FileSpec.create(String.format(Constants.SRG_MAPPINGS, mc)),
				FileSpec.create(String.format(Constants.MCP_MAPPINGS, channel, build, mc))
			));
		});
	}

	@Override
	public Dependency mcpForgeMappings(String version) {
		return mcpForgeMappings(DependencyInfo.create(project, Constants.MINECRAFT_CONFIGURATION).getDependency().getVersion(), version);
	}

	@Override
	public Dependency mcpForgeMappings(String mc, String version) {
		return layeredMappings(builder -> {
			builder.addLayer(new McpForgeMappingsSpec(
				generation.get() == 1
					? FileSpec.create(Constants.calamusGen1Mappings(mc, side.get()))
					: FileSpec.create(Constants.calamusGen2Mappings(mc, generation.get())),
				FileSpec.create(String.format(Constants.FORGE_SRC, mc, version))
			));
		});
	}

	private Dependency layeredMappings(Action<LayeredMappingSpecBuilder> action) {
		return loom.layered(builder -> {
			action.execute(builder);
			builder.addLayer(new NestsMappingSpec(this));
		});
	}

	@Override
	public Dependency nests(String build) {
		return nests(build, generation.get() == 1 ? side.get() : GameSide.MERGED);
	}

	@Override
	public Dependency nests(String build, String side) {
		return nests(build, GameSide.of(side));
	}

	@Override
	public Dependency nests(String build, GameSide side) {
		return project.getDependencies().create(Constants.nests(minecraftVersion(), side, build));
	}

	@Override
	public Dependency sparrow(String build) {
		return sparrow(build, generation.get() == 1 ? side.get() : GameSide.MERGED);
	}

	@Override
	public Dependency sparrow(String build, String side) {
		return sparrow(build, GameSide.of(side));
	}

	@Override
	public Dependency sparrow(String build, GameSide side) {
		return project.getDependencies().create(Constants.sparrow(minecraftVersion(), side, build));
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
	}

	@Override
	public void serverOnlyMappings() {
		side.set(GameSide.SERVER);
	}

	private void switchToGen1() {
		loom.setIntermediateMappingsProvider(CalamusGen1Provider.class, provider -> {
			provider.getSide()
				.convention(side)
				.finalizeValueOnRead();
			provider.getIntermediaryUrl()
				.convention(project.provider(() -> Constants.calamusGen1Url(provider.getSide().get())))
				.finalizeValueOnRead();
			provider.getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
		});

		project.getExtensions().getExtraProperties().set(Constants.VERSION_MANIFEST_PROPERTY, Constants.VERSIONS_MANIFEST_URL_GEN1);
	}

	private void switchToGen2() {
		loom.setIntermediateMappingsProvider(CalamusGen2Provider.class, provider -> {
			provider.getGeneration()
				.convention(generation)
				.finalizeValueOnRead();
			provider.getIntermediaryUrl()
				.convention(project.provider(() -> Constants.calamusGen2Url(provider.getGeneration().get())))
				.finalizeValueOnRead();
			provider.getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
		});

		project.getExtensions().getExtraProperties().set(Constants.VERSION_MANIFEST_PROPERTY, Constants.VERSIONS_MANIFEST_URL_GEN2);
	}

	public Property<GameSide> getSide() {
		return side;
	}

	public Property<Integer> getGeneration() {
		return generation;
	}

	@Override
	public void setGeneration(int generation) {
		this.generation.set(generation);

		if (generation == 1) {
			switchToGen1();
		} else {
			switchToGen2();
		}
	}

	public String minecraftVersion() {
		return DependencyInfo.create(project, Constants.MINECRAFT_CONFIGURATION).getDependency().getVersion();
	}

	public String normalizedMinecraftVersion() {
		// the normalized version id can be parsed from the version details file

		String versionId = minecraftVersion();

		String name = generation.get() == 1 ? "skyrising" : "ornithe";
		Path userCache = loom.getFiles().getUserCache().toPath();
		Path manifestCache = userCache.resolve(name + "_version_manifest.json");

		try {
			if (!Files.exists(manifestCache)) {
				loom.download(Constants.versionsManifestUrl(generation.get())).downloadPath(manifestCache);
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
