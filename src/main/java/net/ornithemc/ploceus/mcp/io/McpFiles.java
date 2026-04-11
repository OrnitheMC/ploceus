package net.ornithemc.ploceus.mcp.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class McpFiles implements AutoCloseable {

	private final Path intermediaryFile;

	private ZipFile intermediaryZip;

	protected McpFiles(Path intermediaryFile) {
		this.intermediaryFile = intermediaryFile;
	}

	private ZipFile openIntermediaryZip() throws IOException {
		if (this.intermediaryZip == null) {
			this.intermediaryZip = new ZipFile(this.intermediaryFile.toFile());
		}

		return this.intermediaryZip;
	}

	public InputStream readIntermediary() throws IOException {
		ZipFile zip = this.openIntermediaryZip();
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

	@Override
	public void close() throws IOException {
		if (this.intermediaryZip != null) {
			this.intermediaryZip.close();
		}
	}
}
