package net.ornithemc.ploceus.mappings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

public abstract class CalamusGen2Provider extends CalamusProvider {

	public abstract Property<Integer> getIntermediaryGeneration();

	@Override
	public void provide(Path tinyMappings, Project project) throws IOException {
		/*
		 * In previous versions of Loom, mappings for all pre-1.3 Minecraft versions
		 * were expected in intermediary -> [clientOfficial, serverOfficial] format.
		 * Since 1.12 it only expects this format for versions between b1.0 and 1.3.
		 * This means Loom existing files could be in the wrong format. In that case,
		 * we simply delete the existing file and let Loom fetch the file again.
		 */
		if (!areMappingsValid(tinyMappings) || getRefreshDeps().get()) {
			Files.deleteIfExists(tinyMappings);

			// The Calamus mappings are built in the proper format for all MC versions,
			// so no further conversion is needed.
			super.provide(tinyMappings, project);
		}
	}

	@Override
	public String getName() {
		return "calamus-gen" + getIntermediaryGeneration().get() + "-" + NAME;
	}
}
