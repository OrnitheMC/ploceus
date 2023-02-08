package net.ornithemc.ploceus;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;

import net.ornithemc.ploceus.nester.NesterJarProcessor;
import net.ornithemc.ploceus.nester.NestsProvider;

public class PloceusGradleExtension {

	public static PloceusGradleExtension get(Project project) {
		return (PloceusGradleExtension)project.getExtensions().getByName("ploceus");
	}

	public PloceusGradleExtension(Project project) {
		apply(project, LoomGradleExtension.get(project));
	}

	private void apply(Project project, LoomGradleExtension loom) {
		project.getConfigurations().register(Constants.NESTS_CONFIGURATION);

		JarProcessor nester = new NesterJarProcessor(project);
		loom.addJarProcessor(nester);
	}

	public NestsProvider getNestsProvider(Project project) {
		LoomGradleExtension loom = LoomGradleExtension.get(project);
		MinecraftProvider minecraft = loom.getMinecraftProvider();
		DependencyInfo dependency = DependencyInfo.create(project, Constants.NESTS_CONFIGURATION);

		return NestsProvider.of(project, dependency, minecraft);
	}
}
