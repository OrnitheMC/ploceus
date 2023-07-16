package net.ornithemc.ploceus.mappings;

import java.util.Objects;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;

import net.ornithemc.ploceus.GameSide;

public abstract class SidedIntermediaryMappingsProvider extends IntermediaryMappingsProvider {

	private GameSide side = GameSide.MERGED;

	@Override
	public String getName() {
		return side.prefix() + super.getName();
	}

	public void configure(Project project, LoomGradleExtension loom, GameSide side) {
		this.side = Objects.requireNonNull(side);

		getIntermediaryUrl()
			.convention(loom.getIntermediaryUrl())
			.finalizeValueOnRead();
		getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
	}
}
