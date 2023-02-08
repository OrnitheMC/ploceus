package net.ornithemc.ploceus.nester;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.processors.JarProcessor;

import net.ornithemc.nester.Nester;
import net.ornithemc.nester.nest.Nests;

import net.ornithemc.ploceus.PloceusGradleExtension;

public class NesterJarProcessor implements JarProcessor {

	private final Project project;

	private Nests nests;

	public NesterJarProcessor(Project project) {
		this.project = project;
	}

	@Override
	public String getId() {
		return "ploceus:nester";
	}

	@Override
	public void setup() {
		this.nests = null;

		PloceusGradleExtension ploceus = PloceusGradleExtension.get(project);
		NestsProvider nests = ploceus.getNestsProvider(project);

		if (nests.provide()) {
			this.nests = nests.get();
		}
	}

	@Override
	public void process(File file) {
		if (nests != null) {
			try {
				Path path = file.toPath();
				Path tmp = Files.createTempFile("tmp", ".jar");

				// nester does not allow src and dst to be same file
				Files.copy(path, tmp);

				Nester.Options options = new Nester.Options().
					silent(true).
					remap(false);
				Nester.nestJar(options, tmp, path, nests);
			} catch (IOException e) {
				throw new RuntimeException("failed to nest jar!", e);
			}
		}
	}
}
