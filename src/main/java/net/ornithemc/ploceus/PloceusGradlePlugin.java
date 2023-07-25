package net.ornithemc.ploceus;

import java.util.Objects;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginAware;

public class PloceusGradlePlugin implements Plugin<PluginAware> {

	public static final String PLOCEUS_VERSION = Objects.requireNonNullElse(PloceusGradlePlugin.class.getPackage().getImplementationVersion(), "0.0.0+unknown");

	@Override
	public void apply(PluginAware target) {
		target.getPlugins().apply(PloceusRepositoryPlugin.class);

		if (target instanceof Project project) {
			project.getLogger().lifecycle("Ploceus: " + PLOCEUS_VERSION);

			project.getExtensions().create("ploceus", PloceusGradleExtension.class);
		}
	}
}
