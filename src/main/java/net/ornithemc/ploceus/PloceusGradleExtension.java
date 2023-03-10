package net.ornithemc.ploceus;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder;

import net.ornithemc.ploceus.nester.NestedMappingsSpec;
import net.ornithemc.ploceus.nester.NesterProcessor;
import net.ornithemc.ploceus.nester.NestsProvider;

public class PloceusGradleExtension {

	public static PloceusGradleExtension get(Project project) {
		return (PloceusGradleExtension)project.getExtensions().getByName("ploceus");
	}

	private final Project project;

	public PloceusGradleExtension(Project project) {
		this.project = project;

		apply(LoomGradleExtension.get(this.project));
	}

	private void apply(LoomGradleExtension loom) {
		project.getConfigurations().register(Constants.NESTS_CONFIGURATION);
		project.getExtensions().getExtraProperties().set("loom_version_manifests", "https://skyrising.github.io/mc-versions/version_manifest.json");

		loom.getIntermediaryUrl().convention("https://maven.ornithemc.net/releases/net/ornithemc/calamus-intermediary/%1$s/calamus-intermediary-%1$s-v2.jar");
		loom.addMinecraftJarProcessor(NesterProcessor.class, this);
	}

	public NestsProvider getNestsProvider() {
		return NestsProvider.of(project);
	}

	public void nestedMappings(LayeredMappingSpecBuilder builder) {
		builder.addLayer(new NestedMappingsSpec(this));
	}
}
