package net.ornithemc.ploceus.nester;

import java.io.IOException;
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

import net.ornithemc.mappingutils.MappingUtils;
import net.ornithemc.nester.nest.Nests;
import net.ornithemc.ploceus.Constants;
import net.ornithemc.ploceus.PloceusGradleExtension;
import net.ornithemc.ploceus.api.GameSide;

public class NestsProvider {

	final Project project;
	final LoomGradleExtension loom;
	final PloceusGradleExtension ploceus;
	final Configuration configuration;
	final MappingsNamespace sourceNamespace;

	Dependency dependency;
	Path file;
	Nests nests;
	Map<MappingsNamespace, Nests> mappedNests;

	private NestsProvider(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, String configuration, MappingsNamespace sourceNamespace) {
		this(project, loom, ploceus, configuration == null ? null : project.getConfigurations().getByName(configuration), sourceNamespace);
	}

	private NestsProvider(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, Configuration configuration, MappingsNamespace sourceNamespace) {
		this.project = project;
		this.loom = loom;
		this.ploceus = ploceus;
		this.configuration = configuration;
		this.sourceNamespace = sourceNamespace;

		this.mappedNests = new EnumMap<>(MappingsNamespace.class);
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
			String fileName = dependency.getName() + "-" + dependency.getVersion() + ".nest";
			Path dir = minecraft.path("nests");
			Path path = dir.resolve(fileName);

			if (Files.notExists(path) || minecraft.refreshDeps()) {
				try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(jar)) {
					Files.createDirectories(dir);
					Files.copy(delegate.getPath("nests/mappings.nest"), path, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					throw new RuntimeException("unable to extract nests!");
				}
			}

			file = path;
		}
	}

	public boolean isPresent() {
		return dependency != null;
	}

	public Nests get(MappingTree mappings, MappingsNamespace ns) {
		provide();

		if (file != null) {
			if (nests == null) {
				nests = Nests.of(file);
			}
			if (ns != sourceNamespace && !mappedNests.containsKey(ns)) {
				mappedNests.put(ns, new NestsMapper(mappings).apply(nests, sourceNamespace, ns));
			}
		}

		return ns == sourceNamespace ? nests : mappedNests.get(ns);
	}

	public static class Simple extends NestsProvider {

		public Simple(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, Constants.NESTS_CONFIGURATION, MappingsNamespace.OFFICIAL);
		}
	}

	public static class Legacy extends NestsProvider {

		public Legacy(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, GameSide side) {
			super(project, loom, ploceus, Constants.NESTS_CONFIGURATION, side == GameSide.CLIENT ? MappingsNamespace.CLIENT_OFFICIAL : MappingsNamespace.SERVER_OFFICIAL);
		}
	}

	public static class Split extends NestsProvider {

		private final NestsProvider client;
		private final NestsProvider server;

		public Split(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, (Configuration) null, null);

			this.client = new NestsProvider(project, loom, ploceus, Constants.CLIENT_NESTS_CONFIGURATION, MappingsNamespace.CLIENT_OFFICIAL);
			this.server = new NestsProvider(project, loom, ploceus, Constants.SERVER_NESTS_CONFIGURATION, MappingsNamespace.SERVER_OFFICIAL);
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
		public Nests get(MappingTree mappings, MappingsNamespace ns) {
			provide();

			if (client.isPresent() || server.isPresent()) {
				if (ns != sourceNamespace && !mappedNests.containsKey(ns)) {
					Nests nests = null;

					if (client.isPresent() && server.isPresent()) {
						Nests clientNests = client.get(mappings, ns);
						Nests serverNests = server.get(mappings, ns);

						nests = MappingUtils.mergeNests(clientNests, serverNests);
					} else {
						if (client.isPresent()) {
							nests = client.get(mappings, ns);
						}
						if (server.isPresent()) {
							nests = server.get(mappings, ns);
						}
					}

					if (nests != null) {
						mappedNests.put(ns, nests);
					}
				}
			}

			return ns == sourceNamespace ? null : mappedNests.get(ns);
		}
	}
}
