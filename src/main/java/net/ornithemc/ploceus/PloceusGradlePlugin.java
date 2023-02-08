package net.ornithemc.ploceus;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.PluginAware;

public class PloceusGradlePlugin implements Plugin<PluginAware> {

	@Override
	public void apply(PluginAware target) {
		target.getPlugins().apply(PloceusRepositoryPlugin.class);

		if (target instanceof Project project) {
			project.getExtensions().create("ploceus", PloceusGradleExtension.class);
		}
	}
}
