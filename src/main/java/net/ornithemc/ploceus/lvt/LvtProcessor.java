package net.ornithemc.ploceus.lvt;

import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;

import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;

import net.ornithemc.condor.Condor;
import net.ornithemc.ploceus.PloceusGradleExtension;

public class LvtProcessor implements MinecraftJarProcessor<LvtProcessor.Spec> {

	private final PloceusGradleExtension ploceus;

	@Inject
	public LvtProcessor(PloceusGradleExtension ploceus) {
		this.ploceus = ploceus;
	}

	@Override
	public String getName() {
		return "ploceus:lvt";
	}

	@Override
	public Spec buildSpec(SpecContext context) {
		return new Spec();
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext ctx) throws IOException {
		try {
			Condor.run(jar, ploceus.getLibraries());
		} catch (IOException e) {
			throw new IOException("failed to generate local variable tables!", e);
		}
	}

	public static class Spec implements MinecraftJarProcessor.Spec {

		private Integer hashCode;

		@Override
		public int hashCode() {
			if (hashCode == null) {
				hashCode = "lvt".hashCode();
			}

			return hashCode;
		}
	}
}
