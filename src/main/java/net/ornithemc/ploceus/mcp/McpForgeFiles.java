package net.ornithemc.ploceus.mcp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class McpForgeFiles extends McpFiles {

	private final Path zipFile;

	public McpForgeFiles(Path intermediaryFile, Path zipFile) {
		super(intermediaryFile);

		this.zipFile = zipFile;
	}

	@Override
	public InputStream readSrg() throws IOException {
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			ZipEntry srg = zip.getEntry("conf/joined.srg");

			if (srg == null) {
				throw new FileNotFoundException("srg mappings are missing!");
			}

			return zip.getInputStream(srg);
		}
	}

	@Override
	public InputStream readFields() throws IOException {
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			ZipEntry fields = zip.getEntry("conf/fields.csv");

			if (fields == null) {
				throw new FileNotFoundException("field mappings are missing!");
			}

			return zip.getInputStream(fields);
		}
	}

	@Override
	public InputStream readMethods() throws IOException {
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			ZipEntry fields = zip.getEntry("conf/methods.csv");

			if (fields == null) {
				throw new FileNotFoundException("method mappings are missing!");
			}

			return zip.getInputStream(fields);
		}
	}

	@Override
	public InputStream readParams() throws IOException {
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			ZipEntry params = zip.getEntry("conf/params.csv");

			if (params == null) {
				throw new FileNotFoundException("parameter mappings are missing!");
			}

			return zip.getInputStream(params);
		}
	}
}
