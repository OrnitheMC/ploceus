package net.ornithemc.ploceus.nester;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;

import net.ornithemc.nester.nest.Nests;

public record NestedMappingsSpec(Nests nests) implements MappingsSpec<NestedMappingsLayer> {

	@Override
	public NestedMappingsLayer createLayer(MappingContext ctx) {
		return new NestedMappingsLayer(nests());
	}
}
