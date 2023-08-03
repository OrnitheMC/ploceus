package net.ornithemc.ploceus.mcp;

import java.io.IOException;
import java.nio.file.Path;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;

import net.ornithemc.ploceus.mcp.io.McpReader;

public class McpMappingsLayer implements MappingLayer {

	private Path srgFile;
	private Path mcpFile;

	public McpMappingsLayer(Path srgFile, Path mcpFile) {
		this.srgFile = srgFile;
		this.mcpFile = mcpFile;
	}

	@Override
	public MappingsNamespace getSourceNamespace() {
		return MappingsNamespace.OFFICIAL;
	}

	@Override
	public void visit(MappingVisitor visitor) throws IOException {
		McpReader.read(srgFile, mcpFile, visitor);
	}
}
