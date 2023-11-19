package net.ornithemc.ploceus.mcp;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.FileSpec;

public class McpForgeMappingsSpec extends McpMappingsSpec<McpForgeMappingsLayer> {

	private final FileSpec zipFile;

	public McpForgeMappingsSpec(FileSpec intermediaryFile, FileSpec zipFile) {
		super(intermediaryFile);

		this.zipFile = zipFile;
	}

	@Override
	public int hashCode() {
		return 31 * super.hashCode() + zipFile.hashCode();
	}

	@Override
	public McpForgeMappingsLayer createLayer(MappingContext ctx) {
		return new McpForgeMappingsLayer(intermediaryFile.get(ctx), zipFile.get(ctx));
	}
}
