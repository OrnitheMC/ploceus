package net.ornithemc.ploceus.mcp;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;

public abstract class McpMappingsSpec<T extends MappingLayer> implements MappingsSpec<T> {

	protected final FileSpec intermediaryFile;

	public McpMappingsSpec(FileSpec intermediaryFile) {
		this.intermediaryFile = intermediaryFile;
	}

	@Override
	public int hashCode() {
		return intermediaryFile.hashCode();
	}
}
