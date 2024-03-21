package net.ornithemc.ploceus.manifest;

import java.util.List;

public record VersionsManifest(List<Version> versions) {

	public static record Version(String id, String details) {
	}

	public Version getVersion(String id) {
		return versions.stream().filter(version -> version.id.equalsIgnoreCase(id)).findFirst().orElse(null);
	}
}
