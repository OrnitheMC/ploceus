package net.ornithemc.ploceus.mappings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import net.ornithemc.ploceus.api.GameSide;

public abstract class CalamusGen1Provider extends CalamusProvider {

	public abstract Property<GameSide> getSide();

	@Override
	public void provide(Path tinyMappings, Project project) throws IOException {
		/*
		 * In previous versions of Loom, mappings for all pre-1.3 Minecraft versions
		 * were expected in intermediary -> [clientOfficial, serverOfficial] format.
		 * Since 1.12 it only expects this format for versions between b1.0 and 1.3.
		 * This means Loom existing files could be in the wrong format. In that case,
		 * we simply delete the existing file and let Loom fetch the file again.
		 * The Calamus mappings are not built in the proper format for b1.0-1.3, in
		 * which case extra conversion is needed.
		 */
		if (!areMappingsValid(tinyMappings) || getRefreshDeps().get()) {
			String extractedMappingsPath = getName() + "-extracted.tiny";
			Path extractedMappings = tinyMappings.resolveSibling(extractedMappingsPath);

			Files.deleteIfExists(extractedMappings);
			Files.deleteIfExists(tinyMappings);

			super.provide(extractedMappings, project);

			if (getUseSplitOfficialNamespaces().get()) {
				MemoryMappingTree mappings = new MemoryMappingTree();
				MappingReader.read(extractedMappings, mappings);

				try (MappingWriter writer = MappingWriter.create(tinyMappings, MappingFormat.TINY_2_FILE)) {
					mappings.accept(
						new MappingSourceNsSwitch(
							new MappingNsRenamer(
								writer,
								Map.of(
									MappingsNamespace.OFFICIAL.toString(),
									getSide().get() == GameSide.CLIENT
										? MappingsNamespace.CLIENT_OFFICIAL.toString()
										: MappingsNamespace.SERVER_OFFICIAL.toString()
								)
							),
							MappingsNamespace.INTERMEDIARY.toString()
						)
					);
				}	
			} else {
				Files.copy(extractedMappings, tinyMappings, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	@Override
	public String getName() {
		return getSide().get().prefix() + "calamus-gen1-" + NAME;
	}
}
