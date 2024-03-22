package net.ornithemc.ploceus.signatures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.inject.Inject;

import org.objectweb.asm.ClassWriter;

import io.github.gaming32.signaturechanger.SignatureMode;
import io.github.gaming32.signaturechanger.apply.SignatureApplier;
import io.github.gaming32.signaturechanger.cli.SignatureChangerCli;
import io.github.gaming32.signaturechanger.tree.SigsClass;
import io.github.gaming32.signaturechanger.tree.SigsFile;

import net.fabricmc.loom.api.processor.MinecraftJarProcessor;
import net.fabricmc.loom.api.processor.ProcessorContext;
import net.fabricmc.loom.api.processor.SpecContext;
import net.fabricmc.mappingio.tree.MappingTree;

import net.ornithemc.ploceus.PloceusGradleExtension;

public class SignaturePatcherProcessor implements MinecraftJarProcessor<SignaturePatcherProcessor.Spec> {

	private final PloceusGradleExtension ploceus;

	@Inject
	public SignaturePatcherProcessor(PloceusGradleExtension ploceus) {
		this.ploceus = ploceus;
	}

	@Override
	public String getName() {
		return "ploceus:signature_patcher";
	}

	@Override
	public Spec buildSpec(SpecContext context) {
		SigsProvider sigs = ploceus.getSigsProvider();
		return sigs.isPresent() ? new Spec(sigs) : null;
	}

	@Override
	public void processJar(Path jar, Spec spec, ProcessorContext ctx) throws IOException {
		try {
			MappingTree mappings = ctx.getMappings();
			SigsFile sigs = ploceus.getSigsProvider().get(mappings, true);
			SignatureApplier applier = new SignatureApplier(sigs);

			SignatureChangerCli.iterateClasses(
				List.of(jar),
				origin -> { },
				origin -> { },
				(path, reader) -> {
					SigsClass c = sigs.classes.get(reader.getClassName());

					if (c == null || (c.signatureInfo.mode() == SignatureMode.KEEP && c.members.isEmpty())) {
						return;
					}

					ClassWriter w = new ClassWriter(reader, 0);
					applier.setDelegate(w);

					try {
						reader.accept(applier, 0);
					} finally {
						applier.setDelegate(null);
					}

					Files.write(path, w.toByteArray());
				}
			);
		} catch (IOException e) {
			throw new RuntimeException("failed to patch signatures!", e);
		}
	}

	public static class Spec implements MinecraftJarProcessor.Spec {

		private final SigsProvider sigs;

		private Integer hashCode;

		public Spec(SigsProvider nests) {
			this.sigs = nests;
		}

		@Override
		public int hashCode() {
			if (hashCode == null) {
				hashCode = sigs.hashCode();
			}

			return hashCode;
		}
	}
}
