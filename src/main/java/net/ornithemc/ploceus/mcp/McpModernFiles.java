package net.ornithemc.ploceus.mcp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class McpModernFiles extends McpFiles {

	private final Path srgFile;
	private final Path mcpFile;

	public McpModernFiles(Path intermediaryFile, Path srgFile, Path mcpFile) {
		super(intermediaryFile);

		this.srgFile = srgFile;
		this.mcpFile = mcpFile;
	}

	@Override
	public InputStream readSrg() throws IOException {
		ZipFile zip = new ZipFile(srgFile.toFile());
		ZipEntry srg = zip.getEntry("joined.srg");

		if (srg == null) {
			throw new FileNotFoundException("srg mappings are missing!");
		}

		return zip.getInputStream(srg);
	}

	@Override
	public InputStream readFields() throws IOException {
		ZipFile zip = new ZipFile(mcpFile.toFile());
		ZipEntry fields = zip.getEntry("fields.csv");

		if (fields == null) {
			throw new FileNotFoundException("field mappings are missing!");
		}

		return zip.getInputStream(fields);
	}

	@Override
	public InputStream readMethods() throws IOException {
		ZipFile zip = new ZipFile(mcpFile.toFile());
		ZipEntry fields = zip.getEntry("methods.csv");

		if (fields == null) {
			throw new FileNotFoundException("method mappings are missing!");
		}

		return zip.getInputStream(fields);
	}

	@Override
	public InputStream readParams() throws IOException {
		ZipFile zip = new ZipFile(mcpFile.toFile());
		ZipEntry params = zip.getEntry("params.csv");

		if (params == null) {
			throw new FileNotFoundException("parameter mappings are missing!");
		}

		return zip.getInputStream(params);
	}
}
