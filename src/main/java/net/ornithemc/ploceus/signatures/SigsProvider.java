package net.ornithemc.ploceus.signatures;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import io.github.gaming32.signaturechanger.tree.MemberReference;
import io.github.gaming32.signaturechanger.tree.SignatureInfo;
import io.github.gaming32.signaturechanger.tree.SigsClass;
import io.github.gaming32.signaturechanger.tree.SigsFile;
import io.github.gaming32.signaturechanger.visitor.SigsReader;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftProvider;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.mappingio.tree.MappingTree;

import net.ornithemc.ploceus.Constants;
import net.ornithemc.ploceus.PloceusGradleExtension;

public class SigsProvider {

	final Project project;
	final LoomGradleExtension loom;
	final PloceusGradleExtension ploceus;
	final String configuration;
	final MappingsNamespace sourceNamespace;
	final MappingsNamespace targetNamespace;

	Path sigsPath;
	SigsFile sigs;
	SigsFile mappedSigs;

	private SigsProvider(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus, String configuration, MappingsNamespace sourceNamespace, MappingsNamespace targetNamespace) {
		this.project = project;
		this.loom = loom;
		this.ploceus = ploceus;
		this.configuration = configuration;
		this.sourceNamespace = sourceNamespace;
		this.targetNamespace = targetNamespace;
	}

	@Override
	public int hashCode() {
		return sigsPath.hashCode();
	}

	public void provide() {
		Configuration conf = project.getConfigurations().getByName(configuration);

		if (conf.getDependencies().isEmpty()) {
			return;
		}

		DependencyInfo dependency = DependencyInfo.create(project, configuration);
		String sigsVersion = dependency.getResolvedVersion();
		Optional<File> sigsJar = dependency.resolveFile();

		if (!sigsJar.isPresent()) {
			return;
		}

		MinecraftProvider minecraft = loom.getMinecraftProvider();
		Path path = minecraft.path(sigsVersion + ".sigs");

		if (Files.notExists(path) || minecraft.refreshDeps()) {
			try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(sigsJar.get().toPath())) {
				Files.copy(delegate.getPath("signatures/signatures.sigs"), path, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException("unable to extract signatures!");
			}
		}

		sigsPath = path;
	}

	public boolean isPresent() {
		return sigsPath != null;
	}

	public SigsFile get(MappingTree mappings, boolean mapped) {
		if (isPresent()) {
			if (sigs == null) {
				try (BufferedReader br = Files.newBufferedReader(sigsPath)) {
					new SigsReader(br).accept(sigs = new SigsFile());
				} catch (IOException e) {
					throw new UncheckedIOException("unable to read sigs", e);
				}
			}
			if (mapped && mappedSigs == null) {
				mappedSigs = new SigsMapper(mappings).apply(sigs, sourceNamespace, targetNamespace);
			}
		}

		return mapped ? mappedSigs : sigs;
	}

	public static class Simple extends SigsProvider {

		public Simple(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, Constants.SIGNATURES_CONFIGURATION, MappingsNamespace.OFFICIAL, MappingsNamespace.NAMED);
		}
	}

	public static class Split extends SigsProvider {

		private final SigsProvider client;
		private final SigsProvider server;

		public Split(Project project, LoomGradleExtension loom, PloceusGradleExtension ploceus) {
			super(project, loom, ploceus, null, MappingsNamespace.INTERMEDIARY, MappingsNamespace.NAMED);

			this.client = new SigsProvider(project, loom, ploceus, Constants.CLIENT_SIGNATURES_CONFIGURATION, MappingsNamespace.CLIENT_OFFICIAL, MappingsNamespace.INTERMEDIARY);
			this.server = new SigsProvider(project, loom, ploceus, Constants.SERVER_SIGNATURES_CONFIGURATION, MappingsNamespace.SERVER_OFFICIAL, MappingsNamespace.INTERMEDIARY);
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

		public SigsFile get(MappingTree mappings, boolean mapped) {
			if (isPresent()) {
				if (sigs == null) {
					SigsFile clientSigs = client.get(mappings, true);
					SigsFile serverSigs = server.get(mappings, true);

					mergeSigs(clientSigs, serverSigs);
				}
				if (mapped && mappedSigs == null) {
					mappedSigs = new SigsMapper(mappings).apply(sigs, sourceNamespace, targetNamespace);
				}
			}

			return mapped ? mappedSigs : sigs;
		}

		private void mergeSigs(SigsFile client, SigsFile server) {
			sigs = new SigsFile();

			for (Map.Entry<String, SigsClass> e : client.classes.entrySet()) {
				String name = e.getKey();
				SigsClass c = e.getValue();
				SigsClass s = server.classes.get(name);

				if (s == null) {
					// client only mapping - we can add it to merged as is
					addClass(name, c);
				} else {
					// mapping is present on both sides - check that they match
					if (mappingsMatch(name, c.signatureInfo, s.signatureInfo)) {
						mergeClasses(name, c, s);
					}
				}
			}
			for (Map.Entry<String, SigsClass> e : server.classes.entrySet()) {
				String name = e.getKey();
				SigsClass s = e.getValue();
				SigsClass c = client.classes.get(name);

				if (c == null) {
					// server only mapping - we can add it to merged as is
					addClass(name, s);
				} else {
					// mapping is present on both sides - already added to merged
				}
			}
		}

		private void addClass(String name, SigsClass c) {
			SigsClass mc = sigs.visitClass(name, c.signatureInfo.mode(), c.signatureInfo.signature());

			for (Map.Entry<MemberReference, SignatureInfo> m : c.members.entrySet()) {
				mc.visitMember(m.getKey().name(), m.getKey().desc().getDescriptor(), m.getValue().mode(), m.getValue().signature());
			}
		}

		private void mergeClasses(String name, SigsClass c, SigsClass s) {
			SigsClass mc = sigs.visitClass(name, c.signatureInfo.mode(), s.signatureInfo.signature());

			for (Map.Entry<MemberReference, SignatureInfo> e : c.members.entrySet()) {
				MemberReference key = e.getKey();
				SignatureInfo cm = e.getValue();
				SignatureInfo sm = s.members.get(key);

				if (sm == null) {
					// client only mapping - we can add it to merged as is
					mc.visitMember(key.name(), key.desc().getDescriptor(), cm.mode(), cm.signature());
				} else {
					// mapping is present on both sides - check that they match
					if (mappingsMatch(key.toString(), cm, sm)) {
						mc.visitMember(key.name(), key.desc().getDescriptor(), cm.mode(), cm.signature());
					}
				}
			}
			for (Map.Entry<MemberReference, SignatureInfo> e : s.members.entrySet()) {
				MemberReference key = e.getKey();
				SignatureInfo sm = e.getValue();
				SignatureInfo cm = c.members.get(key);

				if (cm == null) {
					// server only mapping - we can add it to merged as is
					mc.visitMember(key.name(), key.desc().getDescriptor(), sm.mode(), sm.signature());
				} else {
					// nest is present on both sides - already added to merged
				}
			}
		}

		private boolean mappingsMatch(String key, SignatureInfo c, SignatureInfo s) {
			if (c.mode() != s.mode()) {
				throw cannotMerge(key, c, s, "mode does not match");
			}
			if (!Objects.equals(c.signature(), s.signature())) {
				throw cannotMerge(key, c, s, "signature does not match");
			}

			return true;
		}

		private RuntimeException cannotMerge(String key, SignatureInfo  c, SignatureInfo s, String reason) {
			return new IllegalStateException("cannot merge client and server signature mappings for " + key + ": " + reason);
		}
	}
}
