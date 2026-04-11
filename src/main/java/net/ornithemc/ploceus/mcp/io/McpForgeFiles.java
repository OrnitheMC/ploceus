package net.ornithemc.ploceus.mcp.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class McpForgeFiles extends McpFiles {

	private final Path mcpFile;

	private ZipFile mcpZip;

	public McpForgeFiles(Path intermediaryFile, Path zipFile) {
		super(intermediaryFile);

		this.mcpFile = zipFile;
	}

	private ZipFile openMcpZip() throws IOException {
		if (this.mcpZip == null) {
			this.mcpZip = new ZipFile(this.mcpFile.toFile());
		}

		return this.mcpZip;
	}

	@Override
	public InputStream readSrg() throws IOException {
		ZipFile zip = this.openMcpZip();
		ZipEntry srg = zip.getEntry("conf/joined.srg");

		if (srg == null) {
			throw new FileNotFoundException("srg mappings are missing!");
		}

		return zip.getInputStream(srg);
	}

	@Override
	public InputStream readFields() throws IOException {
		ZipFile zip = this.openMcpZip();
		ZipEntry fields = zip.getEntry("conf/fields.csv");

		if (fields == null) {
			throw new FileNotFoundException("field mappings are missing!");
		}

		return zip.getInputStream(fields);
	}

	@Override
	public InputStream readMethods() throws IOException {
		ZipFile zip = this.openMcpZip();
		ZipEntry fields = zip.getEntry("conf/methods.csv");

		if (fields == null) {
			throw new FileNotFoundException("method mappings are missing!");
		}

		return zip.getInputStream(fields);
	}

	@Override
	public InputStream readParams() throws IOException {
		ZipFile zip = this.openMcpZip();
		ZipEntry params = zip.getEntry("conf/params.csv");

		if (params == null) {
			throw new FileNotFoundException("parameter mappings are missing!");
		}

		return zip.getInputStream(params);
	}

	@Override
	public void close() throws IOException {
		super.close();

		if (this.mcpZip != null) {
			this.mcpZip.close();
		}
	}
}
