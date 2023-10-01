package net.ornithemc.ploceus.mcp;

import java.io.IOException;
import java.nio.file.Path;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;

import net.ornithemc.ploceus.mcp.io.McpReader;

public record McpMappingsLayer(Path intermediaryFile, Path srgFile, Path mcpFile) implements MappingLayer {

	@Override
	public MappingsNamespace getSourceNamespace() {
		return MappingsNamespace.INTERMEDIARY;
	}

	@Override
	public void visit(MappingVisitor visitor) throws IOException {
		McpReader.read(intermediaryFile, srgFile, mcpFile, visitor);
	}
}
