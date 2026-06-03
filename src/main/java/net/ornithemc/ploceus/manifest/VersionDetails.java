package net.ornithemc.ploceus.manifest;

import net.fabricmc.loom.util.Constants;

public record VersionDetails(String id, String normalizedVersion, boolean client, boolean server, boolean sharedMappings, String releaseTime) {

	public boolean isSharedVersioning() {
		return this.releaseTime().compareTo(Constants.RELEASE_TIME_BETA_1_0) >= 0;
	}

	public boolean isSplitMappings() {
		return !this.sharedMappings() && this.isSharedVersioning();
	}
}
