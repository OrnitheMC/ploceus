package net.ornithemc.ploceus.exceptions;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.mappingio.tree.MappingTree;

import net.ornithemc.exceptor.io.ExceptionsFile;
import net.ornithemc.exceptor.io.ExceptorIo;
import net.ornithemc.mappingutils.MappingUtils;
import net.ornithemc.ploceus.Constants;
import net.ornithemc.ploceus.PloceusGradleExtension;
import net.ornithemc.ploceus.api.GameSide;

public class ExceptionsProvider {

	final Project project;
	final LoomGradleExtension loom;
	final PloceusGradleExtension ploceus;
	final String configuration;
	final MappingsNamespace sourceNamespace;

	Path excsPath;
	ExceptionsFile excs;
	Map<MappingsNamespace, ExceptionsFile> mappedExcs;

	private ExceptionsProvider(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, String configuration, MappingsNamespace sourceNamespace) {
		this.project = project;
		this.loom = loom;
		this.ploceus = ploceus;
		this.configuration = configuration;
		this.sourceNamespace = sourceNamespace;

		this.mappedExcs = new EnumMap<>(MappingsNamespace.class);
	}

	@Override
	public int hashCode() {
		return isPresent() ? excsPath.hashCode() : 0;
	}

	public void provide() {
		Configuration conf = project.getConfigurations().getByName(configuration);

		if (conf.getDependencies().isEmpty()) {
			return;
		}

		DependencyInfo dependency = DependencyInfo.create(project, configuration);
		String excsName = dependency.getDependency().getName();
		String excsVersion = dependency.getResolvedVersion();
		Optional<File> excsJar = dependency.resolveFile();

		if (!excsJar.isPresent()) {
			return;
		}

		MinecraftProvider minecraft = loom.getMinecraftProvider();
		Path path = minecraft.path(excsName + "-" + excsVersion + ".excs");

		if (Files.notExists(path) || minecraft.refreshDeps()) {
			try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(excsJar.get().toPath())) {
				Files.copy(delegate.getPath("exceptions/mappings.excs"), path, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("unable to extract exceptions!");
			}
		}

		excsPath = path;
	}

	public boolean isPresent() {
		return excsPath != null;
	}

	public ExceptionsFile get(MappingTree mappings, MappingsNamespace ns) {
		if (isPresent()) {
			if (excs == null) {
				try {
					excs = ExceptorIo.read(excsPath);
				} catch (IOException e) {
					throw new UncheckedIOException("unable to read exceptions", e);
				}
			}
			if (ns != sourceNamespace && !mappedExcs.containsKey(ns)) {
				mappedExcs.put(ns, new ExceptionsMapper(mappings).apply(excs, sourceNamespace, ns));
			}
		}

		return ns == sourceNamespace ? excs : mappedExcs.get(ns);
	}

	public static class Simple extends ExceptionsProvider {

		public Simple(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, Constants.EXCEPTIONS_CONFIGURATION, MappingsNamespace.OFFICIAL);
		}
	}

	public static class Legacy extends ExceptionsProvider {

		public Legacy(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, GameSide side) {
			super(project, loom, ploceus, Constants.EXCEPTIONS_CONFIGURATION, side == GameSide.CLIENT ? MappingsNamespace.CLIENT_OFFICIAL : MappingsNamespace.SERVER_OFFICIAL);
		}
	}

	public static class Split extends ExceptionsProvider {

		private final ExceptionsProvider client;
		private final ExceptionsProvider server;

		public Split(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, null, MappingsNamespace.INTERMEDIARY);

			this.client = new ExceptionsProvider(project, loom, ploceus, Constants.CLIENT_EXCEPTIONS_CONFIGURATION, MappingsNamespace.CLIENT_OFFICIAL);
			this.server = new ExceptionsProvider(project, loom, ploceus, Constants.SERVER_EXCEPTIONS_CONFIGURATION, MappingsNamespace.SERVER_OFFICIAL);
		}

		@Override
		public int hashCode() {
			if (client.isPresent() && server.isPresent()) {
				return Objects.hash(client, server);
			} else {
				if (client.isPresent()) {
					return client.hashCode();
				}
				if (server.isPresent()) {
					return server.hashCode();
				}

				return 0;
			}
		}

		@Override
		public void provide() {
			client.provide();
			server.provide();
		}

		@Override
		public boolean isPresent() {
			return client.isPresent() || server.isPresent();
		}

		@Override
		public ExceptionsFile get(MappingTree mappings, MappingsNamespace ns) {
			if (isPresent()) {
				if (excs == null) {
					if (client.isPresent() && server.isPresent()) {
						ExceptionsFile clientExcs = client.get(mappings, MappingsNamespace.INTERMEDIARY);
						ExceptionsFile serverExcs = server.get(mappings, MappingsNamespace.INTERMEDIARY);

						excs = MappingUtils.mergeExceptions(clientExcs, serverExcs);
					} else {
						if (client.isPresent()) {
							excs = client.get(mappings, MappingsNamespace.INTERMEDIARY);
						}
						if (server.isPresent()) {
							excs = server.get(mappings, MappingsNamespace.INTERMEDIARY);
						}
					}
				}
				if (ns != sourceNamespace && !mappedExcs.containsKey(ns)) {
					mappedExcs.put(ns, new ExceptionsMapper(mappings).apply(excs, sourceNamespace, ns));
				}
			}

			return ns == sourceNamespace ? excs : mappedExcs.get(ns);
		}
	}
}
