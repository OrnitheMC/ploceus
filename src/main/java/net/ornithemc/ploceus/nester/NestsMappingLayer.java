package net.ornithemc.ploceus.nester;

import java.io.IOException;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MappingTree;

public record NestsMappingLayer(NestsProvider nests) implements MappingLayer {

	@Override
	public MappingsNamespace getSourceNamespace() {
		return MappingsNamespace.INTERMEDIARY;
	}

	@Override
	public void visit(MappingVisitor visitor) throws IOException {
		if (visitor instanceof MappingTree mappings && nests().isPresent()) {
			new MappingsNester(mappings, nests().get(mappings, getSourceNamespace())).apply(visitor);
		}
	}
}
