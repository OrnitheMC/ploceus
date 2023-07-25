package net.ornithemc.ploceus;

import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import net.fabricmc.loom.LoomGradleExtension;

import net.ornithemc.ploceus.mappings.SidedIntermediaryMappingsProvider;
import net.ornithemc.ploceus.nester.NestedMappingsSpec;
import net.ornithemc.ploceus.nester.NesterProcessor;
import net.ornithemc.ploceus.nester.NestsProvider;

public class PloceusGradleExtension {

	public static PloceusGradleExtension get(Project project) {
		return (PloceusGradleExtension)project.getExtensions().getByName("ploceus");
	}

	private final Project project;
	private final LoomGradleExtension loom;
	private final OslVersionCache oslVersions;

	public PloceusGradleExtension(Project project) {
		this.project = project;
		this.loom = LoomGradleExtension.get(this.project);
		this.oslVersions = new OslVersionCache(this.project);

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

	public void dependOsl(String version) throws Exception {
		for (Map.Entry<String, String> entry : oslVersions.getDependencies(version).entrySet()) {
			dependOsl(entry.getKey(), entry.getValue());
		}
	}

	public void dependOsl(String module, String version) throws Exception {
		project.getDependencies().add("modImplementation", String.format("%s:%s:%s",
			Constants.OSL_MAVEN_GROUP,
			module,
			oslVersions.getVersion(module, version)));
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
}
