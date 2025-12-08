package net.ornithemc.ploceus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import net.ornithemc.ploceus.api.GameSide;

public class Constants {

	public static final String MAVEN_NAME = "Ornithe";
	public static final String MAVEN_URL = "https://maven.ornithemc.net/releases";
	public static final String META_URL = "https://meta.ornithemc.net";
	public static final String MAVEN_GROUP = "net.ornithemc";

	public static final String QUILT_MAVEN_NAME = "Quilt";
	public static final String QUILT_MAVEN_URL = "https://maven.quiltmc.org/repository/release";

	public static final String LEGACY_FABRIC_MAVEN_NAME = "Legacy Fabric";
	public static final String LEGACY_FABRIC_MAVEN_URL = "https://maven.legacyfabric.net";

	public static final String FORGE_MAVEN_NAME = "Forge";
	public static final String FORGE_MAVEN_URL = "https://maven.minecraftforge.net/";

	public static final String MINECRAFT_CONFIGURATION = "minecraft";
	public static final String EXCEPTIONS_CONFIGURATION = "exceptions";
	public static final String CLIENT_EXCEPTIONS_CONFIGURATION = "clientExceptions";
	public static final String SERVER_EXCEPTIONS_CONFIGURATION = "serverExceptions";
	public static final String SIGNATURES_CONFIGURATION = "signatures";
	public static final String CLIENT_SIGNATURES_CONFIGURATION = "clientSignatures";
	public static final String SERVER_SIGNATURES_CONFIGURATION = "serverSignatures";
	public static final String NESTS_CONFIGURATION = "nests";
	public static final String CLIENT_NESTS_CONFIGURATION = "clientNests";
	public static final String SERVER_NESTS_CONFIGURATION = "serverNests";

	public static final String VERSIONS_MANIFEST_NAME = "ornithe-gen%d";
	public static final String VERSIONS_MANIFEST_URL = "https://ornithemc.net/mc-versions/gen%d/version_manifest.json";

	public static String versionsManifestName(int generation) {
		return String.format(VERSIONS_MANIFEST_NAME, generation);
	}

	public static String versionsManifestUrl(int generation) {
		return String.format(VERSIONS_MANIFEST_URL, generation);
	}

	public static final String LIBRARIES_META_URL = META_URL + "/v3/versions/gen%d/libraries/%s";
	public static String librariesMetaUrl(String mc, int generation) {
		return LIBRARIES_META_URL.formatted(generation, URLEncoder.encode(mc, StandardCharsets.UTF_8));
	}

	public static final String CALAMUS_INTERMEDIARY_MAVEN_GROUP = MAVEN_GROUP;
	public static String calamusGen1Mappings(GameSide side) {
		return CALAMUS_INTERMEDIARY_MAVEN_GROUP + ":calamus-intermediary:%1$s" + side.suffix() + ":v2";
	}
	public static String calamusGen1Mappings(String mc, GameSide side) {
		return calamusGen1Mappings(side).formatted(URLEncoder.encode(mc, StandardCharsets.UTF_8));
	}
	public static String calamusGen1Url(GameSide side) {
		return MAVEN_URL + "/net/ornithemc/calamus-intermediary/%1$s" + side.suffix() + "/calamus-intermediary-%1$s" + side.suffix() + "-v2.jar";
	}
	public static String calamusGen2Mappings(int generation) {
		return CALAMUS_INTERMEDIARY_MAVEN_GROUP + ":calamus-intermediary-gen" + generation + ":%1$s:v2";
	}
	public static String calamusGen2Mappings(String mc, int generation) {
		return calamusGen2Mappings(generation).formatted(URLEncoder.encode(mc, StandardCharsets.UTF_8));
	}
	public static String calamusGen2Url(int generation) {
		return MAVEN_URL + "/net/ornithemc/calamus-intermediary-gen" + generation + "/%1$s/calamus-intermediary-gen" + generation + "-%1$s-v2.jar";
	}

	public static final String FEATHER_MAVEN_GROUP = MAVEN_GROUP;
	public static final String FEATHER_GEN1_MAPPINGS = FEATHER_MAVEN_GROUP + ":feather:%s%s+build.%s:v2";
	public static String featherGen1Mappings(String mc, GameSide side, String build) {
		return String.format(FEATHER_GEN1_MAPPINGS, URLEncoder.encode(mc, StandardCharsets.UTF_8), side.suffix(), build);
	}
	public static final String FEATHER_GEN2_MAPPINGS = FEATHER_MAVEN_GROUP + ":feather-gen%s:%s+build.%s:v2";
	public static String featherGen2Mappings(int generation, String mc, String build) {
		return String.format(FEATHER_GEN2_MAPPINGS, generation, URLEncoder.encode(mc, StandardCharsets.UTF_8), build);
	}

	public static final String RAVEN_MAVEN_GROUP = MAVEN_GROUP;
	public static final String RAVEN = RAVEN_MAVEN_GROUP + ":raven:%s%s+build.%s";
	public static String raven(String mc, GameSide side, String build) {
		return String.format(RAVEN, URLEncoder.encode(mc, StandardCharsets.UTF_8), side.suffix(), build);
	}

	public static final String SPARROW_MAVEN_GROUP = MAVEN_GROUP;
	public static final String SPARROW = SPARROW_MAVEN_GROUP + ":sparrow:%s%s+build.%s";
	public static String sparrow(String mc, GameSide side, String build) {
		return String.format(SPARROW, URLEncoder.encode(mc, StandardCharsets.UTF_8), side.suffix(), build);
	}

	public static final String NESTS_MAVEN_GROUP = MAVEN_GROUP;
	public static final String NESTS = NESTS_MAVEN_GROUP + ":nests:%s%s+build.%s";
	public static String nests(String mc, GameSide side, String build) {
		return String.format(NESTS, URLEncoder.encode(mc, StandardCharsets.UTF_8), side.suffix(), build);
	}

	public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
	public static final String CALAMUS_GENERATION_ATTRIBUTE = "Calamus-Generation";

	public static final String OSL_MAVEN_GROUP_GEN1 = MAVEN_GROUP + ".osl";
	public static final String OSL_MAVEN_GROUP_GEN2 = MAVEN_GROUP + ".osl-gen%d";
	public static String oslMavenGroup(int generation) {
		return generation == 1 ? OSL_MAVEN_GROUP_GEN1 : String.format(OSL_MAVEN_GROUP_GEN2, generation);
	}
	public static final String OSL_CORE = "core";

	public static final String OSL_VERSION_META_ENDPOINT = "/v3/versions/gen%d/osl/%s";
	public static String oslVersionMetaEndpoint(int generation, String version) {
		return String.format(OSL_VERSION_META_ENDPOINT, generation, version);
	}
	public static final String OSL_MODULE_VERSION_META_ENDPOINT = "/v3/versions/gen%d/osl/%s/%s/%s";
	public static String oslModuleVersionMetaEndpoint(int generation, String module, String mc, String version) {
		return String.format(OSL_MODULE_VERSION_META_ENDPOINT, generation, module, URLEncoder.encode(mc, StandardCharsets.UTF_8), version);
	}

	public static final String MCP_MAVEN_GROUP = "de.oceanlabs.mcp";
	public static final String SRG_MAPPINGS = MCP_MAVEN_GROUP + ":mcp:%s:srg@zip";
	public static String srgMappings(String mc) {
		return String.format(SRG_MAPPINGS, URLEncoder.encode(mc, StandardCharsets.UTF_8));
	}
	public static final String MCP_MAPPINGS = MCP_MAVEN_GROUP + ":mcp_%s:%s-%s@zip";
	public static String mcpMappings(String channel, String build, String mc) {
		return String.format(MCP_MAPPINGS, channel, build, URLEncoder.encode(mc, StandardCharsets.UTF_8));
	}

	public static final String FORGE_MAVEN_GROUP = "net.minecraftforge.mcp";
	public static final String FORGE_SRC = FORGE_MAVEN_GROUP + ":forge:%s-%s:src@zip";
	public static String forgeSrc(String mc, String version) {
		return String.format(FORGE_SRC, URLEncoder.encode(mc, StandardCharsets.UTF_8), version);
	}
}
