package net.ornithemc.ploceus.signatures;

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

import io.github.gaming32.signaturechanger.tree.SigsFile;
import io.github.gaming32.signaturechanger.visitor.SigsReader;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.mappingio.tree.MappingTree;

import net.ornithemc.mappingutils.MappingUtils;
import net.ornithemc.ploceus.Constants;
import net.ornithemc.ploceus.PloceusGradleExtension;
import net.ornithemc.ploceus.api.GameSide;

public class SignaturesProvider {

	final Project project;
	final LoomGradleExtension loom;
	final PloceusGradleExtension ploceus;
	final String configuration;
	final MappingsNamespace sourceNamespace;

	Path sigsPath;
	SigsFile sigs;
	Map<MappingsNamespace, SigsFile> mappedSigs;

	private SignaturesProvider(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, String configuration, MappingsNamespace sourceNamespace) {
		this.project = project;
		this.loom = loom;
		this.ploceus = ploceus;
		this.configuration = configuration;
		this.sourceNamespace = sourceNamespace;

		this.mappedSigs = new EnumMap<>(MappingsNamespace.class);
	}

	@Override
	public int hashCode() {
		return isPresent() ? sigsPath.hashCode() : 0;
	}

	public void provide() {
		Configuration conf = project.getConfigurations().getByName(configuration);

		if (conf.getDependencies().isEmpty()) {
			return;
		}

		DependencyInfo dependency = DependencyInfo.create(project, configuration);
		String sigsName = dependency.getDependency().getName();
		String sigsVersion = dependency.getResolvedVersion();
		Optional<File> sigsJar = dependency.resolveFile();

		if (!sigsJar.isPresent()) {
			return;
		}

		MinecraftProvider minecraft = loom.getMinecraftProvider();
		Path dir = minecraft.path("signatures");
		Path path = dir.resolve(sigsName + "-" + sigsVersion + ".sigs");

		if (Files.notExists(path) || minecraft.refreshDeps()) {
			try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(sigsJar.get().toPath())) {
				Files.createDirectories(dir);
				Files.copy(delegate.getPath("signatures/mappings.sigs"), path, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("unable to extract signatures!");
			}
		}

		sigsPath = path;
	}

	public boolean isPresent() {
		return sigsPath != null;
	}

	public SigsFile get(MappingTree mappings, MappingsNamespace ns) {
		if (isPresent()) {
			if (sigs == null) {
				try (SigsReader sr = new SigsReader(Files.newBufferedReader(sigsPath))) {
					sr.accept(sigs = new SigsFile());
				} catch (IOException e) {
					throw new UncheckedIOException("unable to read signatures", e);
				}
			}
			if (ns != sourceNamespace && !mappedSigs.containsKey(ns)) {
				mappedSigs.put(ns, new SignaturesMapper(mappings).apply(sigs, sourceNamespace, ns));
			}
		}

		return ns == sourceNamespace ? sigs : mappedSigs.get(ns);
	}

	public static class Simple extends SignaturesProvider {

		public Simple(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, Constants.SIGNATURES_CONFIGURATION, MappingsNamespace.OFFICIAL);
		}
	}

	public static class Legacy extends SignaturesProvider {

		public Legacy(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, GameSide side) {
			super(project, loom, ploceus, Constants.SIGNATURES_CONFIGURATION, side == GameSide.CLIENT ? MappingsNamespace.CLIENT_OFFICIAL : MappingsNamespace.SERVER_OFFICIAL);
		}
	}

	public static class Split extends SignaturesProvider {

		private final SignaturesProvider client;
		private final SignaturesProvider server;

		public Split(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, null, MappingsNamespace.INTERMEDIARY);

			this.client = new SignaturesProvider(project, loom, ploceus, Constants.CLIENT_SIGNATURES_CONFIGURATION, MappingsNamespace.CLIENT_OFFICIAL);
			this.server = new SignaturesProvider(project, loom, ploceus, Constants.SERVER_SIGNATURES_CONFIGURATION, MappingsNamespace.SERVER_OFFICIAL);
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
		public SigsFile get(MappingTree mappings, MappingsNamespace ns) {
			if (isPresent()) {
				if (sigs == null) {
					if (client.isPresent() && server.isPresent()) {
						SigsFile clientSigs = client.get(mappings, MappingsNamespace.INTERMEDIARY);
						SigsFile serverSigs = server.get(mappings, MappingsNamespace.INTERMEDIARY);

						sigs = MappingUtils.mergeSignatures(clientSigs, serverSigs);
					} else {
						if (client.isPresent()) {
							sigs = client.get(mappings, MappingsNamespace.INTERMEDIARY);
						}
						if (server.isPresent()) {
							sigs = server.get(mappings, MappingsNamespace.INTERMEDIARY);
						}
					}
				}
				if (ns != sourceNamespace && !mappedSigs.containsKey(ns)) {
					mappedSigs.put(ns, new SignaturesMapper(mappings).apply(sigs, sourceNamespace, ns));
				}
			}

			return ns == sourceNamespace ? sigs : mappedSigs.get(ns);
		}
	}
}
