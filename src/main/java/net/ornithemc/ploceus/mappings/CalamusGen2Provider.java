package net.ornithemc.ploceus.mappings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingNsCompleter;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class CalamusGen2Provider extends IntermediaryMappingsProvider {

	public abstract Property<Integer> getGeneration();

	@Override
	public void provide(Path tinyMappings, Project project) throws IOException {
		if (Files.exists(tinyMappings) && !getRefreshDeps().get()) {
			return;
		}

		super.provide(tinyMappings, project);

		if (getIsLegacyMinecraft().get()) {
			/*
			 * Loom expects intermediary files for all pre-1.3 versions to be in
			 * intermediary -> [clientOfficial, serverOfficial] format, but gen2
			 * Calamus only does this for >=b1.0 <1.3, so for Alpha and older we
			 * need to rewrite the mapping file and switch out the namespaces.
			 * All these versions are either client-only or server-only, which
			 * means filling both the clientOfficial and serverOfficial namespaces
			 * will make it work for either.
			 */

			MemoryMappingTree mappings = new MemoryMappingTree();
			MappingReader.read(tinyMappings, mappings);

			if (mappings.getSrcNamespace().equals(MappingsNamespace.OFFICIAL.toString())) {
				try (MappingWriter writer = MappingWriter.create(tinyMappings, MappingFormat.TINY_2_FILE)) {
					mappings.accept(
						new MappingSourceNsSwitch(
							new MappingNsRenamer(
								new MappingNsCompleter(
									writer,
									Map.of(
										MappingsNamespace.SERVER_OFFICIAL.toString(),
										MappingsNamespace.CLIENT_OFFICIAL.toString()
									),
									true
								),
								Map.of(
									MappingsNamespace.OFFICIAL.toString(),
									MappingsNamespace.CLIENT_OFFICIAL.toString()
								)
							),
							MappingsNamespace.INTERMEDIARY.toString()
						)
					);
				}
			}
		}
	}

	@Override
	public String getName() {
		return "calamus-gen" + getGeneration().get() + "-" + NAME;
	}
}
