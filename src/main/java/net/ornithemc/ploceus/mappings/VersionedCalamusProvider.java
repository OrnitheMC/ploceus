package net.ornithemc.ploceus.mappings;

import org.gradle.api.provider.Property;

import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;

public abstract class VersionedCalamusProvider extends IntermediaryMappingsProvider {

	public abstract Property<Integer> getGeneration();

	@Override
	public String getName() {
		return "calamus-gen-" + getGeneration().get() + "-" + super.getName();
	}
}
