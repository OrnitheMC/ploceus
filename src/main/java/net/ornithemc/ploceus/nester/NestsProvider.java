package net.ornithemc.ploceus.nester;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.FileSystemUtil;

import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.NestType;
import net.ornithemc.nester.nest.NesterIo;
import net.ornithemc.nester.nest.Nests;

import net.ornithemc.ploceus.Constants;

public class NestsProvider {

	public static NestsProvider of(Project project) {
		return new NestsProvider(project);
	}

	private final Project project;

	private Path path;
	private Nests nests;

	private NestsProvider(Project project) {
		this.project = project;
	}

	public boolean provide() {
		path = null;
		nests = null;

		Configuration conf = project.getConfigurations().getByName(Constants.NESTS_CONFIGURATION);

		if (conf.getDependencies().isEmpty()) {
			return false;
		}

		LoomGradleExtension loom = LoomGradleExtension.get(project);
		MinecraftProvider minecraft = loom.getMinecraftProvider();
		DependencyInfo dependency = DependencyInfo.create(project, Constants.NESTS_CONFIGURATION);

		String version = dependency.getResolvedVersion();
		Optional<File> jar = dependency.resolveFile();

		if (!jar.isPresent()) {
			return false;
		}

		Path jarPath = jar.get().toPath();
		File nestsFile = minecraft.file(version + ".nest");
		Path nestsPath = nestsFile.toPath();

		if (Files.notExists(nestsPath) || minecraft.refreshDeps()) {
			try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(jarPath)) {
				Files.copy(delegate.getPath("nests/mappings.nest"), nestsPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("unable to extract nests!");
			}
		}

		path = nestsPath;

		return true;
	}

	public Path path() {
		return path;
	}

	public Nests get() {
		if (path != null && nests == null) {
			NesterIo.read(nests = Nests.empty(), path);
		}

		return nests;
	}

	public Nests map(MappingTree mappings) {
		return path == null ? null : NestsMapper.apply(mappings, get());
	}

	private static class NestsMapper {

		public static Nests apply(MappingTree mappings, Nests nests) {
			return new NestsMapper(mappings, nests).apply();
		}

		private final MappingTree mappings;
		private final Nests nests;
		private final Nests mappedNests;
		private final int nsid;

		public NestsMapper(MappingTree mappings, Nests nests) {
			this.mappings = mappings;
			this.nests = nests;
			this.mappedNests = Nests.empty();
			this.nsid = this.mappings.getNamespaceId(MappingsNamespace.NAMED.toString());
		}

		public Nests apply() {
			for (Nest nest : nests) {
				NestType type = nest.type;
				String className = mapClassName(nest.className);
				String enclClassName = mapOuterName(nest.className, nest.enclClassName);
				String enclMethodName = (nest.enclMethodName == null) ? null : mapMethodName(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
				String enclMethodDesc = (nest.enclMethodName == null) ? null : mapMethodDesc(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
				String innerName = mapInnerName(nest.className, nest.innerName);
				int access = nest.access;

				mappedNests.add(new Nest(type, className, enclClassName, enclMethodName, enclMethodDesc, innerName, access));
			}

			return mappedNests;
		}

		private String mapClassName(String name) {
			ClassMapping c = mappings.getClass(name);
			return (c == null) ? name : c.getName(nsid);
		}

		private String mapMethodName(String className, String name, String desc) {
			MethodMapping m = mappings.getMethod(className, name, desc);
			return (m == null) ? name : m.getName(nsid);
		}

		private String mapMethodDesc(String className, String name, String desc) {
			MethodMapping m = mappings.getMethod(className, name, desc);
			return (m == null) ? name : m.getDesc(nsid);
		}

		private String mapOuterName(String className, String enclClassName) {
			String mappedClassName = mapClassName(className);
			int idx = mappedClassName.lastIndexOf('$');

			// provided mappings already apply nesting
			return mappedClassName.substring(0, idx);
		}

		private String mapInnerName(String className, String innerName) {
			String mappedClassName = mapClassName(className);
			int idx = mappedClassName.lastIndexOf('$');

			// provided mappings already apply nesting
			return mappedClassName.substring(idx + 1);
		}
	}
}
