package net.ornithemc.ploceus.mappings;

import java.util.Objects;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;

import net.ornithemc.ploceus.GameSide;

public abstract class SidedIntermediaryMappingsProvider extends IntermediaryMappingsProvider {

	private GameSide side = GameSide.MERGED;
	private String defaultUrl = "";

	@Override
	public String getName() {
		String name = this.side.prefix() + "calamus-" + super.getName();

		String defaultUrl = this.defaultUrl;
		String url = getIntermediaryUrl().get();

		if (!defaultUrl.equals(url)) {
			// make sure the name is changed when the user defines a
			// custom intermediary url, to ensure the default cache
			// file does not get corrupted with other intermediaries
			name += "-" + Integer.toHexString(url.hashCode());
		}

		return name;
	}

	public void configure(Project project, LoomGradleExtension loom, GameSide side) {
		this.side = Objects.requireNonNull(side);
		this.defaultUrl = loom.getIntermediaryUrl().get();

		getIntermediaryUrl()
			.convention(loom.getIntermediaryUrl())
			.finalizeValueOnRead();
		getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
	}
}
