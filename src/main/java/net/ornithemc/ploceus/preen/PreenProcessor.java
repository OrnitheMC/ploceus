package net.ornithemc.ploceus.preen;

import java.io.IOException;
import java.nio.file.Path;

import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;

import net.ornithemc.preen.Preen;

public class PreenProcessor implements MinecraftJarProcessor<PreenProcessor.Spec> {

	@Override
	public String getName() {
		return "ploceus:preen";
	}

	@Override
	public Spec buildSpec(SpecContext context) {
		return new Spec();
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext ctx) throws IOException {
		try {
			Preen.modifyMergedBridgeMethodsAccess(jar);
		} catch (IOException e) {
			throw new IOException("failed preen jar!", e);
		}
	}

	public static class Spec implements MinecraftJarProcessor.Spec {

		private Integer hashCode;

		@Override
		public int hashCode() {
			if (hashCode == null) {
				hashCode = "preen".hashCode();
			}

			return hashCode;
		}
	}
}
