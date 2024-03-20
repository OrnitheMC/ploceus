package net.ornithemc.ploceus.nester;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.Nests;
import net.ornithemc.ploceus.Constants;
import net.ornithemc.ploceus.PloceusGradleExtension;

public class NestsProvider {

	final Project project;
	final LoomGradleExtension loom;
	final PloceusGradleExtension ploceus;
	final String configuration;
	final MappingsNamespace sourceNamespace;
	final MappingsNamespace targetNamespace;

	Path nestsPath;
	Nests nests;
	Nests mappedNests;

	private NestsProvider(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, String configuration, MappingsNamespace sourceNamespace, MappingsNamespace targetNamespace) {
		this.project = project;
		this.loom = loom;
		this.ploceus = ploceus;
		this.configuration = configuration;
		this.sourceNamespace = sourceNamespace;
		this.targetNamespace = targetNamespace;
	}

	@Override
	public int hashCode() {
		return nestsPath.hashCode();
	}

	public void provide() {
		Configuration conf = project.getConfigurations().getByName(configuration);

		if (conf.getDependencies().isEmpty()) {
			return;
		}

		DependencyInfo dependency = DependencyInfo.create(project, configuration);
		String nestsVersion = dependency.getResolvedVersion();
		Optional<File> nestsJar = dependency.resolveFile();

		if (!nestsJar.isPresent()) {
			return;
		}

		MinecraftProvider minecraft = loom.getMinecraftProvider();
		Path path = minecraft.path(nestsVersion + ".nest");

		if (Files.notExists(path) || minecraft.refreshDeps()) {
			try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(nestsJar.get().toPath())) {
				Files.copy(delegate.getPath("nests/mappings.nest"), path, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("unable to extract nests!");
			}
		}

		nestsPath = path;
	}

	public boolean isPresent() {
		return nestsPath != null;
	}

	public Nests get(MappingTree mappings, boolean mapped) {
		if (isPresent()) {
			if (nests == null) {
				nests = Nests.of(nestsPath);
			}
			if (mapped && mappedNests == null) {
				mappedNests = new NestsMapper(mappings).apply(nests, sourceNamespace, targetNamespace);
			}
		}

		return mapped ? mappedNests : nests;
	}

	public static class Simple extends NestsProvider {

		public Simple(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, Constants.NESTS_CONFIGURATION, MappingsNamespace.OFFICIAL, MappingsNamespace.NAMED);
		}
	}

	public static class Split extends NestsProvider {

		private final NestsProvider client;
		private final NestsProvider server;

		public Split(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, null, MappingsNamespace.INTERMEDIARY, MappingsNamespace.NAMED);

			this.client = new NestsProvider(project, loom, ploceus, Constants.CLIENT_NESTS_CONFIGURATION, MappingsNamespace.CLIENT_OFFICIAL, MappingsNamespace.INTERMEDIARY);
			this.server = new NestsProvider(project, loom, ploceus, Constants.SERVER_NESTS_CONFIGURATION, MappingsNamespace.SERVER_OFFICIAL, MappingsNamespace.INTERMEDIARY);
		}

		@Override
		public int hashCode() {
			return Objects.hash(client, server);
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

		public Nests get(MappingTree mappings, boolean mapped) {
			if (isPresent()) {
				if (nests == null) {
					Nests clientNests = client.get(mappings, true);
					Nests serverNests = server.get(mappings, true);

					nests = mergeNests(clientNests, serverNests);
				}
				if (mapped && mappedNests == null) {
					mappedNests = new NestsMapper(mappings).apply(nests, sourceNamespace, targetNamespace);
				}
			}

			return mapped ? mappedNests : nests;
		}

		private Nests mergeNests(Nests client, Nests server) {
			Nests merged = Nests.empty();

			for (Nest c : client) {
				Nest s = server.get(c.className);

				if (s == null) {
					// client only nest - we can add it to merged as is
					merged.add(c);
				} else {
					// nest is present on both sides - check that they match
					if (nestsMatch(c, s)) {
						merged.add(c);
					}
				}
			}
			for (Nest s : server) {
				Nest c = client.get(s.className);

				if (c == null) {
					// server only nest - we can add it to merged as is
					merged.add(s);
				} else {
					// nest is present on both sides - already added to merged
				}
			}

			return merged;
		}

		private boolean nestsMatch(Nest c, Nest s) {
			if (c.type != s.type) {
				throw cannotMerge(c, s, "type does not match");
			}
			if (!Objects.equals(c.enclClassName, s.enclClassName)) {
				throw cannotMerge(c, s, "enclosing class name does not match");
			}
			if (!Objects.equals(c.enclMethodName, s.enclMethodName)) {
				throw cannotMerge(c, s, "enclosing method name does not match");
			}
			if (!Objects.equals(c.enclMethodDesc, s.enclMethodDesc)) {
				throw cannotMerge(c, s, "enclosing method descriptor does not match");
			}
			if (!Objects.equals(c.innerName, s.innerName)) {
				throw cannotMerge(c, s, "inner name does not match");
			}
			if (c.access != s.access) {
				throw cannotMerge(c, s, "access flags does not match");
			}

			return true;
		}

		private RuntimeException cannotMerge(Nest  c, Nest s, String reason) {
			return new IllegalStateException("cannot merge client nest " + c.className + " with server nest " + s.className + ": " + reason);
		}
	}
}
