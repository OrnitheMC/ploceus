package net.ornithemc.ploceus.mcp;

import java.io.IOException;
import java.nio.file.Path;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;

import net.ornithemc.ploceus.mcp.io.McpReader;

public class McpMappingsLayer implements MappingLayer {

	private Path intermediaryFile;
	private Path srgFile;
	private Path mcpFile;

	public McpMappingsLayer(Path intermediaryFile, Path srgFile, Path mcpFile) {
		this.intermediaryFile = intermediaryFile;
		this.srgFile = srgFile;
		this.mcpFile = mcpFile;
	}

	@Override
	public MappingsNamespace getSourceNamespace() {
		return MappingsNamespace.INTERMEDIARY;
	}

	@Override
	public void visit(MappingVisitor visitor) throws IOException {
		McpReader.read(intermediaryFile, srgFile, mcpFile, visitor);
	}
}
