package net.ornithemc.ploceus.mappings;

import java.io.IOException;

import net.fabricmc.loom.api.mappings.layered.MappingLayer;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MappingTree;

public record FillingMappingLayer(MappingFiller filler) implements MappingLayer {

	@Override
	public void visit(MappingVisitor visitor) throws IOException {
		if (visitor instanceof MappingTree mappings) {
			filler.fillMappings(mappings);
		}
	}
}
