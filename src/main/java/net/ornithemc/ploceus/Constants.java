package net.ornithemc.ploceus;

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

	public static final String VERSION_MANIFEST_PROPERTY = "loom_version_manifests";
	public static final String VERSION_MANIFEST_URL = "https://skyrising.github.io/mc-versions/version_manifest.json";

	public static final String CALAMUS_INTERMEDIARY_MAVEN_GROUP = "net.ornithemc";
	public static final String CALAMUS_INTERMEDIARY_MAPPINGS = "calamus-intermediary:%s";
	public static final String CALAMUS_INTERMEDIARY_URL = MAVEN_URL + "/net/ornithemc/calamus-intermediary/%1$s/calamus-intermediary-%1$s-v2.jar";

	public static final String OSL_MAVEN_GROUP = "net.ornithemc.osl";
	public static final String OSL_CORE = "core";

	public static final String OSL_META_ENDPOINT = "/v3/versions/osl/%s";
	public static final String OSL_MODULE_META_ENDPOINT = "/v3/versions/osl/%s/%s/%s";

	public static final String MCP_MAVEN_GROUP = "de.oceanlabs.mcp";
	public static final String SRG_MAPPINGS = "mcp:%s:srg@zip";
	public static final String MCP_MAPPINGS = "mcp_%s:%s-%s@zip";

	public static final String FORGE_MAVEN_GROUP = "net.minecraftforge.mcp";
	public static final String FORGE_SRC = "forge:%s-%s:src@zip";

}
