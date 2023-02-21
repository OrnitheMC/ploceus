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

		loom.getCustomMinecraftManifest().set("https://skyrising.github.io/mc-versions/version_manifest.json");
		loom.addMinecraftJarProcessor(NesterProcessor.class, this);
	}

	public NestsProvider getNestsProvider() {
		return NestsProvider.of(project);
	}

	public void nestedMappings(LayeredMappingSpecBuilder builder) {
		builder.addLayer(new NestedMappingsSpec(this));
	}
}
