package net.ornithemc.ploceus.api;

import org.gradle.api.provider.Property;

import net.ornithemc.ploceus.mcp.McpForgeMappingsSpec;
import net.ornithemc.ploceus.mcp.McpModernMappingsSpec;

public interface PloceusGradleExtensionApi {

	McpModernMappingsSpec mcpMappings(String channel, String build);

	McpModernMappingsSpec mcpMappings(String channel, String mc, String build);

	McpForgeMappingsSpec mcpForgeMappings(String version);

	McpForgeMappingsSpec mcpForgeMappings(String mc, String version);

	void dependOsl(String version) throws Exception;

	void dependOsl(String version, String side) throws Exception;

	void dependOsl(String version, GameSide side) throws Exception;

	void dependOsl(String configuration, String version, GameSide side) throws Exception;

	void dependOslModule(String module, String version) throws Exception;

	void dependOslModule(String module, String version, String side) throws Exception;

	void dependOslModule(String module, String version, GameSide side) throws Exception;

	void dependOslModule(String configuration, String module, String version, GameSide side) throws Exception;

	String oslModule(String module, String version) throws Exception;

	String oslModule(String module, String version, String side) throws Exception;

	String oslModule(String module, String version, GameSide side) throws Exception;

	void addCommonLibraries();

	void addCommonLibraries(String configuration);

	@Deprecated
	void clientOnlyMappings();

	@Deprecated
	void serverOnlyMappings();

	Property<Integer> getGeneration();

}
