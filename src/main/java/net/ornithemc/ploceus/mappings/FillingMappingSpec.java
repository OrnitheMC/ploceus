package net.ornithemc.ploceus.mappings;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;

import net.ornithemc.ploceus.PloceusGradleExtension;

public record FillingMappingSpec(PloceusGradleExtension ploceus) implements MappingsSpec<FillingMappingLayer> {

	@Override
	public int hashCode() {
		return "mapping-filler".hashCode();
	}

	@Override
	public FillingMappingLayer createLayer(MappingContext context) {
		return new FillingMappingLayer(ploceus.getMappingFiller(context));
	}
}
