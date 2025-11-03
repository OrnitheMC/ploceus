package net.ornithemc.ploceus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Property;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.vdurmont.semver4j.Semver;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.util.Constants.Configurations;

import net.ornithemc.ploceus.api.GameSide;
import net.ornithemc.ploceus.api.PloceusGradleExtensionApi;
import net.ornithemc.ploceus.exceptions.ExceptionPatcherProcessor;
import net.ornithemc.ploceus.exceptions.ExceptionsProvider;
import net.ornithemc.ploceus.lvt.LvtProcessor;
import net.ornithemc.ploceus.manifest.VersionDetails;
import net.ornithemc.ploceus.manifest.VersionsManifest;
import net.ornithemc.ploceus.mappings.CalamusGen1Provider;
import net.ornithemc.ploceus.mappings.CalamusGen2Provider;
import net.ornithemc.ploceus.mcp.McpForgeMappingsSpec;
import net.ornithemc.ploceus.mcp.McpModernMappingsSpec;
import net.ornithemc.ploceus.nester.NesterProcessor;
import net.ornithemc.ploceus.nester.NestsMappingSpec;
import net.ornithemc.ploceus.nester.NestsProvider;
import net.ornithemc.ploceus.preen.PreenProcessor;
import net.ornithemc.ploceus.signatures.SignaturePatcherProcessor;
import net.ornithemc.ploceus.signatures.SignaturesProvider;

public class PloceusGradleExtension implements PloceusGradleExtensionApi {

	private static final Gson GSON = new GsonBuilder().create();

	public static PloceusGradleExtension get(Project project) {
		return (PloceusGradleExtension)project.getExtensions().getByName("ploceus");
	}

	private final Project project;
	private final LoomGradleExtension loom;
	private final OslVersionCache oslVersions;
	private final LibraryUpgradesCache libraryUpgrades;
	private final Property<ExceptionsProvider> exceptionsProvider;
	private final Property<SignaturesProvider> signaturesProvider;
	private final Property<NestsProvider> nestsProvider;
	private final Property<Boolean> upgradeLibraries;
	private final Property<Boolean> patchLvts;
	private final Property<GameSide> side; // gen 1
	private final Property<Integer> intermediaryGeneration; // gen 2+

	private int nextManifestPriority = -10;

	public PloceusGradleExtension(Project project) {
		this.project = project;
		this.loom = LoomGradleExtension.get(this.project);
		this.oslVersions = new OslVersionCache(this.project, this);
		this.libraryUpgrades = new LibraryUpgradesCache(this.project, this);
		this.exceptionsProvider = project.getObjects().property(ExceptionsProvider.class);
		this.exceptionsProvider.convention(project.provider(() -> {
			ExceptionsProvider provider;
			if (loom.getMinecraftProvider().isLegacySplitOfficialNamespaceVersion()) {
				if (getIntermediaryGeneration().get() == 1) {
					provider = new ExceptionsProvider.Legacy(project, loom, this, getSide().get());
				} else {
					provider = new ExceptionsProvider.Split(project, loom, this);
				}
			} else {
				provider = new ExceptionsProvider.Simple(project, loom, this);
			}
			provider.provide();

			return provider;
		}));
		this.signaturesProvider = project.getObjects().property(SignaturesProvider.class);
		this.signaturesProvider.convention(project.provider(() -> {
			SignaturesProvider provider;
			if (loom.getMinecraftProvider().isLegacySplitOfficialNamespaceVersion()) {
				if (getIntermediaryGeneration().get() == 1) {
					provider = new SignaturesProvider.Legacy(project, loom, this, getSide().get());
				} else {
					provider = new SignaturesProvider.Split(project, loom, this);
				}
			} else {
				provider = new SignaturesProvider.Simple(project, loom, this);
			}
			provider.provide();

			return provider;
		}));
		this.nestsProvider = project.getObjects().property(NestsProvider.class);
		this.nestsProvider.convention(project.provider(() -> {
			NestsProvider provider;
			if (loom.getMinecraftProvider().isLegacySplitOfficialNamespaceVersion()) {
				if (getIntermediaryGeneration().get() == 1) {
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
		this.nestsProvider.finalizeValueOnRead();
		this.upgradeLibraries = project.getObjects().property(Boolean.class);
		this.upgradeLibraries.convention(project.provider(() -> true));
		this.upgradeLibraries.finalizeValueOnRead();
		this.patchLvts = project.getObjects().property(Boolean.class);
		this.patchLvts.convention(project.provider(() -> true));
		this.patchLvts.finalizeValueOnRead();
		this.side = project.getObjects().property(GameSide.class);
		this.side.convention(project.provider(() -> {
			VersionDetails details = minecraftVersionDetails();

			if (!details.server()) {
				return GameSide.CLIENT;
			}
			if (!details.client()) {
				return GameSide.SERVER;
			}

			return GameSide.MERGED;
		}));
		this.intermediaryGeneration = project.getObjects().property(int.class);
		this.intermediaryGeneration.convention(1);

		apply();
	}

	private void apply() {
		project.getConfigurations().register(Constants.EXCEPTIONS_CONFIGURATION);
		project.getConfigurations().register(Constants.CLIENT_EXCEPTIONS_CONFIGURATION);
		project.getConfigurations().register(Constants.SERVER_EXCEPTIONS_CONFIGURATION);
		project.getConfigurations().register(Constants.SIGNATURES_CONFIGURATION);
		project.getConfigurations().register(Constants.CLIENT_SIGNATURES_CONFIGURATION);
		project.getConfigurations().register(Constants.SERVER_SIGNATURES_CONFIGURATION);
		project.getConfigurations().register(Constants.NESTS_CONFIGURATION);
		project.getConfigurations().register(Constants.CLIENT_NESTS_CONFIGURATION);
		project.getConfigurations().register(Constants.SERVER_NESTS_CONFIGURATION);

		loom.getLibraryProcessors().add((platform, context) -> new LibraryUpgrader(this, platform, context));
		loom.getLibraryProcessors().add((platform, context) -> new RealmsRemover(platform, context));
		loom.addMinecraftJarProcessor(LvtProcessor.class, this);
		loom.addMinecraftJarProcessor(ExceptionPatcherProcessor.class, this);
		loom.addMinecraftJarProcessor(SignaturePatcherProcessor.class, this);
		loom.addMinecraftJarProcessor(PreenProcessor.class);
		loom.addMinecraftJarProcessor(NesterProcessor.class, this);

		project.afterEvaluate(project -> {
			project.getTasks().configureEach(task -> {
				if (task instanceof AbstractRemapJarTask remapJarTask) {
					String dstNs = remapJarTask.getTargetNamespace().get();

					if (MappingsNamespace.INTERMEDIARY.toString().equals(dstNs)) {
						remapJarTask.manifest(manifest -> {
							manifest.attributes(Map.of(
								Constants.CALAMUS_GENERATION_ATTRIBUTE, intermediaryGeneration.get()
							));
						});
					}
				}
			});
		});

		switchToGen1();
	}

	public List<Path> getLibraries() {
		return this.project.getConfigurations().findByName(Configurations.MINECRAFT_COMPILE_LIBRARIES).getFiles().stream().map(File::toPath).toList();
	}

	public ExceptionsProvider getExceptionsProvider() {
		return exceptionsProvider.get();
	}

	public SignaturesProvider getSignaturesProvider() {
		return signaturesProvider.get();
	}

	public NestsProvider getNestsProvider() {
		return nestsProvider.get();
	}

	public boolean shouldUpgradeLibraries() {
		return upgradeLibraries.get();
	}

	public boolean shouldPatchLvts() {
		return patchLvts.get()
			&& new Semver(normalizedMinecraftVersion()).isLowerThan(new Semver("1.8.2-rc.5"))
			&& new Semver(normalizedMinecraftVersion()).isGreaterThan(new Semver("0.32.0")); // broken in infdev and below
	}

	@Override
	public Dependency featherMappings(String build) {
		return layeredMappings(builder -> {
			builder.mappings(project.getDependencies().create(
				intermediaryGeneration.get() == 1
					? Constants.featherGen1Mappings(minecraftVersion(), side.get(), build)
					: Constants.featherGen2Mappings(intermediaryGeneration.get(), minecraftVersion(), build)
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
				intermediaryGeneration.get() == 1
					? FileSpec.create(Constants.calamusGen1Mappings(mc, side.get()))
					: FileSpec.create(Constants.calamusGen2Mappings(mc, intermediaryGeneration.get())),
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
				intermediaryGeneration.get() == 1
					? FileSpec.create(Constants.calamusGen1Mappings(mc, side.get()))
					: FileSpec.create(Constants.calamusGen2Mappings(mc, intermediaryGeneration.get())),
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
	public Dependency raven(String build) {
		return raven(build, intermediaryGeneration.get() == 1 ? side.get() : GameSide.MERGED);
	}

	@Override
	public Dependency raven(String build, String side) {
		return raven(build, GameSide.of(side));
	}

	@Override
	public Dependency raven(String build, GameSide side) {
		return project.getDependencies().create(Constants.raven(minecraftVersion(), side, build));
	}

	@Override
	public Dependency sparrow(String build) {
		return sparrow(build, intermediaryGeneration.get() == 1 ? side.get() : GameSide.MERGED);
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
	public Dependency nests(String build) {
		return nests(build, intermediaryGeneration.get() == 1 ? side.get() : GameSide.MERGED);
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
	public void dependOslModule(String version, String module) throws Exception {
		dependOslModule(version, GameSide.MERGED, module);
	}

	@Override
	public void dependOslModule(String version, String side, String module) throws Exception {
		dependOslModule(version, GameSide.of(side), module);
	}

	@Override
	public void dependOslModule(String version, GameSide side, String module) throws Exception {
		dependOslModule("modImplementation", version, side, module);
	}

	@Override
	public void dependOslModule(String configuration, String version, GameSide side, String module) throws Exception {
		String baseVersion = oslVersions.getDependency(version, module);

		if (baseVersion == null) {
			throw new RuntimeException("osl " + version + " " + module + " for " + side.id() + " does not exist");
		} else {
			addOslModuleDependency(configuration, module, oslModule(module, baseVersion, side));
		}
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
	public void disableLibraryUpgrades() {
		upgradeLibraries.set(false);
	}

	public List<Library> getLibraryUpgrades() {
		return libraryUpgrades.getLibraryUpgrades();
	}

	@Override
	public void disableLvtPatch() {
		patchLvts.set(false);
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

		loom.getVersionsManifests().add(Constants.VERSIONS_MANIFEST_NAME_GEN1, Constants.VERSIONS_MANIFEST_URL_GEN1, nextManifestPriority--);
	}

	private void switchToGen2() {
		loom.setIntermediateMappingsProvider(CalamusGen2Provider.class, provider -> {
			provider.getIntermediaryGeneration()
				.convention(intermediaryGeneration)
				.finalizeValueOnRead();
			provider.getIntermediaryUrl()
				.convention(project.provider(() -> Constants.calamusGen2Url(provider.getIntermediaryGeneration().get())))
				.finalizeValueOnRead();
			provider.getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
		});

		loom.getVersionsManifests().add(Constants.VERSIONS_MANIFEST_NAME_GEN2, Constants.VERSIONS_MANIFEST_URL_GEN2, nextManifestPriority--);
	}

	public Property<GameSide> getSide() {
		return side;
	}

	public Property<Integer> getIntermediaryGeneration() {
		return intermediaryGeneration;
	}

	@Override
	public void setIntermediaryGeneration(int generation) {
		this.intermediaryGeneration.set(generation);

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
		return minecraftVersionDetails().normalizedVersion();
	}

	public VersionDetails minecraftVersionDetails() {
		String versionId = minecraftVersion();

		String manifestUrl = Constants.versionsManifestUrl(intermediaryGeneration.get());
		Path userCache = loom.getFiles().getUserCache().toPath();
		Path manifestCache = userCache.resolve(Constants.versionsManifestName(intermediaryGeneration.get()) + "_versions_manifest.json");

		try {
			if (!Files.exists(manifestCache)) {
				loom.download(manifestUrl).downloadPath(manifestCache);
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
					return GSON.fromJson(_br, VersionDetails.class);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("unable to read version details", e);
		}
	}
}
