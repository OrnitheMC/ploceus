package net.ornithemc.ploceus.mcp;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;

public class McpMappingsSpec implements MappingsSpec<McpMappingsLayer> {

	private final FileSpec srgFile;
	private final FileSpec mcpFile;

	public McpMappingsSpec(FileSpec srgFile, FileSpec mcpFile) {
		this.srgFile = srgFile;
		this.mcpFile = mcpFile;
	}

	@Override
	public int hashCode() {
		return 31 * srgFile.hashCode() + mcpFile.hashCode();
	}

	@Override
	public McpMappingsLayer createLayer(MappingContext ctx) {
		return new McpMappingsLayer(srgFile.get(ctx), mcpFile.get(ctx));
	}
}
