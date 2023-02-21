package net.ornithemc.ploceus.nester;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;

import net.ornithemc.ploceus.PloceusGradleExtension;

public record NestedMappingsSpec(PloceusGradleExtension ploceus) implements MappingsSpec<NestedMappingsLayer> {

	@Override
	public NestedMappingsLayer createLayer(MappingContext ctx) {
		return new NestedMappingsLayer(ploceus());
	}
}
