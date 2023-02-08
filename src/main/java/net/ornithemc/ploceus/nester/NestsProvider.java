package net.ornithemc.ploceus.nester;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.gradle.api.Project;

import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.service.SharedService;
import net.fabricmc.loom.util.service.SharedServiceManager;

import net.ornithemc.nester.nest.NesterIo;
import net.ornithemc.nester.nest.Nests;

public class NestsProvider implements SharedService {

	public static NestsProvider of(Project project, DependencyInfo dependency, MinecraftProvider minecraft) {
		return SharedServiceManager.get(project)
			.getOrCreateService("NestsProvider:%s:%s".formatted(dependency.getDepString(), minecraft.minecraftVersion()), () -> {
				return new NestsProvider(project, dependency, minecraft);
			});
	}

	private final Project project;
	private final DependencyInfo dependency;
	private final MinecraftProvider minecraft;

	private Nests nests;

	private NestsProvider(Project project, DependencyInfo dependency, MinecraftProvider minecraft) {
		this.project = project;
		this.dependency = dependency;
		this.minecraft = minecraft;
	}

	public boolean provide() {
		nests = null;

		String version = dependency.getResolvedVersion();
		Optional<File> jar = dependency.resolveFile();

		if (!jar.isPresent()) {
			return false;
		}

		Path jarPath = jar.get().toPath();
		File nestsFile = minecraft.file(minecraft.minecraftVersion() + "-" + version + ".nest");
		Path nestsPath = nestsFile.toPath();

		if (Files.notExists(nestsPath) || minecraft.refreshDeps()) {
			try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(jarPath)) {
				Files.copy(delegate.getPath("nests/mappings.nest"), nestsPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("unable to extract nests!");
			}
		}

		nests = Nests.empty();
		NesterIo.read(nests, nestsPath);

		return true;
	}

	public Nests get() {
		return nests;
	}
}
