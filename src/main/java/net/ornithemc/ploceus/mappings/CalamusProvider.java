package net.ornithemc.ploceus.mappings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class CalamusProvider extends IntermediaryMappingsProvider {

	boolean areMappingsValid(Path tinyFile) {
		if (!Files.exists(tinyFile)) {
			return false;
		}

		MemoryMappingTree mappings = new MemoryMappingTree();

		try {
			MappingReader.read(tinyFile, mappings);
		} catch (IOException e) {
			return false; // corrupt file?
		}

		String expectedSrcNs = getUseSplitOfficialNamespaces().get()
			? MappingsNamespace.INTERMEDIARY.toString()
			: MappingsNamespace.OFFICIAL.toString();
		String sourceNs = mappings.getSrcNamespace();

		return sourceNs.equals(expectedSrcNs);
	}
}
