package net.ornithemc.ploceus.mcp;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;

public class McpModernMappingsSpec extends McpMappingsSpec<McpModernMappingsLayer> {

	private final FileSpec srgFile;
	private final FileSpec mcpFile;

	public McpModernMappingsSpec(FileSpec intermediaryFile, FileSpec srgFile, FileSpec mcpFile) {
		super(intermediaryFile);

		this.srgFile = srgFile;
		this.mcpFile = mcpFile;
	}

	@Override
	public int hashCode() {
		return 31 * super.hashCode() + 31 * srgFile.hashCode() + mcpFile.hashCode();
	}

	@Override
	public McpModernMappingsLayer createLayer(MappingContext ctx) {
		return new McpModernMappingsLayer(intermediaryFile.get(ctx), srgFile.get(ctx), mcpFile.get(ctx));
	}
}
