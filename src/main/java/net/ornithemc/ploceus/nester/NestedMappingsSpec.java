package net.ornithemc.ploceus.nester;

import net.fabricmc.loom.api.mappings.layered.MappingContext;
import net.fabricmc.loom.api.mappings.layered.spec.MappingsSpec;

import net.ornithemc.ploceus.PloceusGradleExtension;

public class NestedMappingsSpec implements MappingsSpec<NestedMappingsLayer> {

	private final PloceusGradleExtension ploceus;

	public NestedMappingsSpec(PloceusGradleExtension ploceus) {
		this.ploceus = ploceus;
	}

	@Override
	public int hashCode() {
		return "ploceus:nests".hashCode();
	}

	@Override
	public NestedMappingsLayer createLayer(MappingContext ctx) {
		return new NestedMappingsLayer(ploceus);
	}
}
