package net.ornithemc.ploceus.api;

import org.gradle.api.artifacts.Dependency;

public interface PloceusGradleExtensionApi {

	Dependency featherMappings(String build);

	Dependency mcpMappings(String channel, String build);

	Dependency mcpMappings(String channel, String mc, String build);

	Dependency mcpForgeMappings(String version);

	Dependency mcpForgeMappings(String mc, String version);

	Dependency raven(String build);

	Dependency raven(String build, String side);

	Dependency raven(String build, GameSide side);

	Dependency sparrow(String build);

	Dependency sparrow(String build, String side);

	Dependency sparrow(String build, GameSide side);

	Dependency nests(String build);

	Dependency nests(String build, String side);

	Dependency nests(String build, GameSide side);

	void dependOsl(String version) throws Exception;

	void dependOsl(String version, String side) throws Exception;

	void dependOsl(String version, GameSide side) throws Exception;

	void dependOsl(String configuration, String version, GameSide side) throws Exception;

	void dependOslModule(String version, String module) throws Exception;

	void dependOslModule(String version, String side, String module) throws Exception;

	void dependOslModule(String version, GameSide side, String module) throws Exception;

	void dependOslModule(String configuration, String version, GameSide side, String module) throws Exception;

	String oslModule(String module, String version) throws Exception;

	String oslModule(String module, String version, String side) throws Exception;

	String oslModule(String module, String version, GameSide side) throws Exception;

	void disableLibraryUpgrades();

	void disableLvtPatch();

	@Deprecated
	void clientOnlyMappings();

	@Deprecated
	void serverOnlyMappings();

	void setGeneration(int generation); // TODO: change to a property once gen 2 is the default

}
