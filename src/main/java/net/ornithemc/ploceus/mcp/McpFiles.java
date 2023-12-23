package net.ornithemc.ploceus.mcp;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class McpFiles {

	private final Path intermediaryFile;

	protected McpFiles(Path intermediaryFile) {
		this.intermediaryFile = intermediaryFile;
	}

	public InputStream readIntermediary() throws IOException {
		ZipFile zip = new ZipFile(intermediaryFile.toFile());
		ZipEntry intermediary = zip.getEntry("mappings/mappings.tiny");

		if (intermediary == null) {
			throw new FileNotFoundException("intermediary mappings are missing!");
		}

		return zip.getInputStream(intermediary);
	}

	public abstract InputStream readSrg() throws IOException;

	public abstract InputStream readFields() throws IOException;

	public abstract InputStream readMethods() throws IOException;

	public abstract InputStream readParams() throws IOException;

}
