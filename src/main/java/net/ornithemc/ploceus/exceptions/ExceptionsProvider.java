package net.ornithemc.ploceus.exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
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
	final Configuration configuration;
	final MappingsNamespace sourceNamespace;

	Dependency dependency;
	Path file;
	ExceptionsFile excs;
	Map<MappingsNamespace, ExceptionsFile> mappedExcs;

	private ExceptionsProvider(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, String configuration, MappingsNamespace sourceNamespace) {
		this(project, loom, ploceus, configuration == null ? null : project.getConfigurations().getByName(configuration), sourceNamespace);
	}

	private ExceptionsProvider(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, Configuration configuration, MappingsNamespace sourceNamespace) {
		this.project = project;
		this.loom = loom;
		this.ploceus = ploceus;
		this.configuration = configuration;
		this.sourceNamespace = sourceNamespace;

		this.mappedExcs = new EnumMap<>(MappingsNamespace.class);
	}

	@Override
	public int hashCode() {
		return isPresent() ? dependency.hashCode() : 0;
	}

	public void resolve() {
		if (configuration == null) {
			return;
		}

		DependencySet deps = configuration.getDependencies();

		if (deps.isEmpty()) {
			return;
		}
		if (deps.size() != 1) {
			throw new IllegalStateException(String.format("Configuration '%s' must only have 1 dependency", configuration.getName()));
		}

		dependency = deps.iterator().next();
	}

	public void provide() {
		if (dependency != null && file == null) {
			Path jar = configuration.getSingleFile().toPath();

			MinecraftProvider minecraft = loom.getMinecraftProvider();
			String fileName = dependency.getName() + "-" + dependency.getVersion() + ".excs";
			Path dir = minecraft.path("exceptions");
			Path path = dir.resolve(fileName);

			if (Files.notExists(path) || minecraft.refreshDeps()) {
				try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(jar)) {
					Files.createDirectories(dir);
					Files.copy(delegate.getPath("exceptions/mappings.excs"), path, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					throw new RuntimeException("unable to extract exceptions!");
				}
			}

			file = path;
		}
	}

	public boolean isPresent() {
		return dependency != null;
	}

	public ExceptionsFile get(MappingTree mappings, MappingsNamespace ns) {
		provide();

		if (file != null) {
			if (excs == null) {
				try {
					excs = ExceptorIo.read(file);
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
			super(project, loom, ploceus, (Configuration) null, null);

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
		public void resolve() {
			client.resolve();
			server.resolve();
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
			provide();

			if (client.isPresent() || server.isPresent()) {
				if (ns != sourceNamespace && !mappedExcs.containsKey(ns)) {
					ExceptionsFile excs = null;

					if (client.isPresent() && server.isPresent()) {
						ExceptionsFile clientExcs = client.get(mappings, ns);
						ExceptionsFile serverExcs = server.get(mappings, ns);

						excs = MappingUtils.mergeExceptions(clientExcs, serverExcs);
					} else {
						if (client.isPresent()) {
							excs = client.get(mappings, ns);
						}
						if (server.isPresent()) {
							excs = server.get(mappings, ns);
						}
					}

					if (excs != null) {
						mappedExcs.put(ns, excs);
					}
				}
			}

			return ns == sourceNamespace ? null : mappedExcs.get(ns);
		}
	}
}
