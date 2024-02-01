package net.ornithemc.ploceus;

import java.util.LinkedHashSet;
import java.util.Set;

import org.gradle.api.Project;

import com.vdurmont.semver4j.Semver;

public class CommonLibraries {

	private final Project project;
	private final PloceusGradleExtension ploceus;
	private final Set<Library> libraries;

	public CommonLibraries(Project project, PloceusGradleExtension ploceus) {
		this.project = project;
		this.ploceus = ploceus;
		this.libraries = new LinkedHashSet<>();

		// the slf4j binding for log4j - this version has been tested to work on 1.6, 1.11, 1.12
		// lower versions of mc not tested because they did not yet ship log4j to begin with
		this.libraries.add(Library.all("org.slf4j:slf4j-api:2.0.1"));
		this.libraries.add(Library.all("org.apache.logging.log4j:log4j-slf4j2-impl:2.19.0"));
		this.libraries.add(Library.all("org.apache.logging.log4j:log4j-api:2.19.0", "org.apache.logging.log4j:log4j-core:2.19.0"));
		this.libraries.add(Library.all("it.unimi.dsi:fastutil:8.5.9"));
		this.libraries.add(Library.all("com.google.code.gson:gson:2.10"));

		this.libraries.add(Library.upTo("1.5.2", "com.google.guava:guava:14.0"));
		this.libraries.add(Library.upTo("1.7.5", "commons-codec:commons-codec:1.9"));
		this.libraries.add(Library.upTo("1.7.10", "org.apache.commons:commons-compress:1.8.1"));
		this.libraries.add(Library.upTo("1.5.2", "commons-io:commons-io:2.4"));
		this.libraries.add(Library.upTo("1.5.2", "org.apache.commons:commons-lang3:3.1"));
		this.libraries.add(Library.upTo("1.7.10", "commons-logging:commons-logging:1.1.3"));
		this.libraries.add(Library.upTo("1.7.9", "org.apache.httpcomponents:httpcore:4.3.2"));
		this.libraries.add(Library.upTo("1.7.9", "org.apache.httpcomponents:httpclient:4.3.3"));
	}

	public void addDependencies(String configuration) {
		String normalizedMcVersion = ploceus.normalizedMinecraftVersion();
		Semver mcVersion = new Semver(normalizedMcVersion);

		for (Library library : libraries) {
			boolean minSatisfied = (library.minVersion == null || mcVersion.compareTo(library.minVersion) >= 0);
			boolean maxSatisfied = (library.maxVersion == null || mcVersion.compareTo(library.maxVersion) <= 0);

			if (minSatisfied && maxSatisfied) {
				for (String maven : library.maven) {
					project.getDependencies().add(configuration, maven);
				}
			}
		}
	}

	private static class Library {

		private final Semver minVersion;
		private final Semver maxVersion;
		private final String[] maven;

		public static Library all(String... maven) {
			return new Library(null, null, maven);
		}

		public static Library from(String minVersion, String... maven) {
			return new Library(new Semver(minVersion), null, maven);
		}

		public static Library upTo(String maxVersion, String... maven) {
			return new Library(null, new Semver(maxVersion), maven);
		}

		public static Library between(String minVersion, String maxVersion, String... maven) {
			return new Library(new Semver(minVersion), new Semver(maxVersion), maven);
		}

		private Library(Semver minVersion, Semver maxVersion, String... maven) {
			this.minVersion = minVersion;
			this.maxVersion = maxVersion;
			this.maven = maven;
		}
	}
}
