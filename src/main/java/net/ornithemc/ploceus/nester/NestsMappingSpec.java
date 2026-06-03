package net.ornithemc.ploceus.nester;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;

public record NestsMappingSpec(NestsProvider nests) implements MappingsSpec<NestsMappingLayer> {

	@Override
	public NestsMappingLayer createLayer(MappingContext context) {
		return new NestsMappingLayer(nests());
	}
}
