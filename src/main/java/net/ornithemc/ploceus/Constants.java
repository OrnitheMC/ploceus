package net.ornithemc.ploceus;

import net.ornithemc.ploceus.api.GameSide;

public class Constants {

	public static final String MAVEN_NAME = "Ornithe";
	public static final String MAVEN_URL = "https://maven.ornithemc.net/releases";
	public static final String META_URL = "https://meta.ornithemc.net";

	public static final String QUILT_MAVEN_NAME = "Quilt";
	public static final String QUILT_MAVEN_URL = "https://maven.quiltmc.org/repository/release";

	public static final String FORGE_MAVEN_NAME = "Forge";
	public static final String FORGE_MAVEN_URL = "https://maven.minecraftforge.net/";

	public static final String MINECRAFT_CONFIGURATION = "minecraft";
	public static final String NESTS_CONFIGURATION = "nests";
	public static final String CLIENT_NESTS_CONFIGURATION = "clientNests";
	public static final String SERVER_NESTS_CONFIGURATION = "serverNests";
	public static final String SIGNATURES_CONFIGURATION = "signatures";
	public static final String CLIENT_SIGNATURES_CONFIGURATION = "clientSignatures";
	public static final String SERVER_SIGNATURES_CONFIGURATION = "serverSignatures";

	public static final String VERSION_MANIFEST_PROPERTY = "loom_version_manifests";
	public static final String VERSION_MANIFEST_URL = "https://ornithemc.net/mc-versions/version_manifest.json";

	public static final String CALAMUS_INTERMEDIARY_MAVEN_GROUP = "net.ornithemc";
	public static String calamusGen1Mappings(GameSide side) {
		return CALAMUS_INTERMEDIARY_MAVEN_GROUP + ":calamus-intermediary:%1$s" + side.suffix() + ":v2";
	}
	public static String calamusGen1Mappings(String mc, GameSide side) {
		return calamusGen1Mappings(side).formatted(mc);
	}
	public static String calamusGen1Url(GameSide side) {
		return MAVEN_URL + "/net/ornithemc/calamus-intermediary/%1$s" + side.suffix() + "/calamus-intermediary-%1$s" + side.suffix() + "-v2.jar";
	}
	public static String calamusGen2Mappings(int generation) {
		return CALAMUS_INTERMEDIARY_MAVEN_GROUP + ":calamus-intermediary-gen" + generation + ":%1$s:v2";
	}
	public static String calamusGen2Mappings(String mc, int generation) {
		return calamusGen2Mappings(generation).formatted(mc);
	}
	public static String calamusGen2Url(int generation) {
		return MAVEN_URL + "/net/ornithemc/calamus-intermediary-gen" + generation + "/%1$s/calamus-intermediary-gen" + generation + "-%1$s-v2.jar";
	}

	public static final String FEATHER_MAVEN_GROUP = "net.ornithemc";
	public static final String FEATHER_GEN1_MAPPINGS = FEATHER_MAVEN_GROUP + ":feather:%s%s+build.%s:v2";
	public static String featherGen1Mappings(String mc, GameSide side, String build) {
		return String.format(FEATHER_GEN1_MAPPINGS, mc, side.suffix(), build);
	}
	public static final String FEATHER_GEN2_MAPPINGS = FEATHER_MAVEN_GROUP + ":feather-gen%s:%s+build.%s:v2";
	public static String featherGen2Mappings(int generation, String mc, String build) {
		return String.format(FEATHER_GEN2_MAPPINGS, generation, mc, build);
	}

	public static final String NESTS_MAVEN_GROUP = "net.ornithemc";
	public static final String NESTS = NESTS_MAVEN_GROUP + ":nests:%s%s+build.%s";
	public static String nests(String mc, GameSide side, String build) {
		return String.format(NESTS, mc, side.suffix(), build);
	}

	public static final String SPARROW_MAVEN_GROUP = "net.ornithemc";
	public static final String SPARROW = SPARROW_MAVEN_GROUP + ":sparrow:%s%s+build.%s";
	public static String sparrow(String mc, GameSide side, String build) {
		return String.format(SPARROW, mc, side.suffix(), build);
	}

	public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
	public static final String CALAMUS_GENERATION_ATTRIBUTE = "Calamus-Generation";

	public static final String OSL_MAVEN_GROUP = "net.ornithemc.osl";
	public static final String OSL_CORE = "core";

	public static final String OSL_META_ENDPOINT = "/v3/versions/osl/%s";
	public static final String OSL_MODULE_META_ENDPOINT = "/v3/versions/osl/%s/%s/%s";

	public static final String MCP_MAVEN_GROUP = "de.oceanlabs.mcp";
	public static final String SRG_MAPPINGS = MCP_MAVEN_GROUP + ":mcp:%s:srg@zip";
	public static final String MCP_MAPPINGS = MCP_MAVEN_GROUP + ":mcp_%s:%s-%s@zip";

	public static final String FORGE_MAVEN_GROUP = "net.minecraftforge.mcp";
	public static final String FORGE_SRC = FORGE_MAVEN_GROUP + ":forge:%s-%s:src@zip";

}
