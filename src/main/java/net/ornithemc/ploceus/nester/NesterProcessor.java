package net.ornithemc.ploceus.nester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.inject.Inject;

import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;

import net.fabricmc.mappingio.tree.MappingTree;

import net.ornithemc.nester.Nester;

import net.ornithemc.ploceus.PloceusGradleExtension;

public class NesterProcessor implements MinecraftJarProcessor<NesterProcessor.Spec> {

	private final PloceusGradleExtension ploceus;

	@Inject
	public NesterProcessor(PloceusGradleExtension ploceus) {
		this.ploceus = ploceus;
	}

	@Override
	public String getName() {
		return "ploceus:nester";
	}

	@Override
	public Spec buildSpec(SpecContext context) {
		NestsProvider nests = ploceus.getNestsProvider();
		return nests.provide() ? new Spec(nests) : null;
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext ctx) throws IOException {
		try {
			MappingTree mappings = ctx.getMappings();
			NestsProvider nests = spec.nests;

			Path tmp = Files.createTempFile("tmp", ".jar");

			// nester does not allow src and dst to be same file
			Files.copy(jar, tmp, StandardCopyOption.REPLACE_EXISTING);

			Nester.Options options = new Nester.Options().
				silent(true).
				remap(false);
			Nester.nestJar(options, tmp, jar, nests.map(mappings));
		} catch (IOException e) {
			throw new RuntimeException("failed to nest jar!", e);
		}
	}

	public static class Spec implements MinecraftJarProcessor.Spec {

		private final NestsProvider nests;

		private Integer hashCode;

		public Spec(NestsProvider nests) {
			this.nests = nests;
		}

		@Override
		public int hashCode() {
			if (hashCode == null) {
				hashCode = nests.get().hashCode();
			}

			return hashCode;
		}
	}
}
