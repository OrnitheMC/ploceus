package net.ornithemc.ploceus.mcp.io;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class McpReader {

	public static void read(Path intermediary, Path srg, Path mcp, MappingVisitor visitor) throws IOException {
		read(intermediary, srg, mcp).accept(new MappingSourceNsSwitch(new MappingDstNsReorder(visitor, NAMED_NAMESPACE), INTERMEDIARY_NAMESPACE));
	}

	public static MappingTreeView read(Path intermediary, Path srg, Path mcp) throws IOException {
		return new McpReader(intermediary, srg, mcp).read();
	}

	private static final String OFFICIAL_NAMESPACE     = MappingsNamespace.OFFICIAL.name().toLowerCase();
	private static final String INTERMEDIARY_NAMESPACE = MappingsNamespace.INTERMEDIARY.name().toLowerCase();
	private static final String SRG_NAMESPACE          = "srg";
	private static final String NAMED_NAMESPACE        = MappingsNamespace.NAMED.name().toLowerCase();

	private static final String INTERMEDIARY_TINY = "mappings/mappings.tiny";
	private static final String JOINED_SRG  = "joined.srg";
	private static final String FIELDS_CSV  = "fields.csv";
	private static final String METHODS_CSV = "methods.csv";
	private static final String PARAMS_CSV  = "params.csv";

	private Path intermediaryFile;
	private Path srgFile;
	private Path mcpFile;
	// The CSV files for field, method, and parameter mappings
	// do not contain information about the enclosing classes
	// of the mappings, so we cache those in these maps when
	// reading the SRG file.
	private final Map<String, Collection<String>> fieldClasses;
	private final Map<String, Collection<String>> methodClasses;
	private final Map<String, String> methods;

	private MemoryMappingTree mappings;

	private McpReader(Path intermediaryFile, Path srgFile, Path mcpFile) {
		this.intermediaryFile = intermediaryFile;
		this.srgFile = srgFile;
		this.mcpFile = mcpFile;
		this.fieldClasses = new HashMap<>();
		this.methodClasses = new HashMap<>();
		this.methods = new HashMap<>();
	}

	private MappingTreeView read() throws IOException {
		mappings = new MemoryMappingTree();

		try (ZipFile zip = new ZipFile(srgFile.toFile())) {
			ZipEntry srg = zip.getEntry(JOINED_SRG);

			if (srg == null) {
				throw new FileNotFoundException("srg mappings are missing!");
			}

			try (InputStreamReader input = new InputStreamReader(zip.getInputStream(srg))) {
				readSrg(input);
			}
		}
		try (ZipFile zip = new ZipFile(mcpFile.toFile())) {
			ZipEntry fields = zip.getEntry(FIELDS_CSV);
			ZipEntry methods = zip.getEntry(METHODS_CSV);
			ZipEntry params = zip.getEntry(PARAMS_CSV);

			if (fields == null) {
				throw new FileNotFoundException("mcp field mappings are missing!");
			}
			if (methods == null) {
				throw new FileNotFoundException("mcp method mappings are missing!");
			}
			if (params == null) {
				throw new FileNotFoundException("mcp parameter mappings are missing!");
			}

			try (InputStreamReader input = new InputStreamReader(zip.getInputStream(fields))) {
				readFields(input);
			}
			try (InputStreamReader input = new InputStreamReader(zip.getInputStream(methods))) {
				readMethods(input);
			}
			try (InputStreamReader input = new InputStreamReader(zip.getInputStream(params))) {
				readParams(input);
			}
		}
		try (ZipFile zip = new ZipFile(intermediaryFile.toFile())) {
			ZipEntry intermediary = zip.getEntry(INTERMEDIARY_TINY);

			if (intermediary == null) {
				throw new FileNotFoundException("intermediary mappings are missing!");
			}

			try (InputStreamReader input = new InputStreamReader(zip.getInputStream(intermediary))) {
				MappingReader.read(input, mappings);
			}
		}

		return mappings;
	}

	private void readSrg(Reader reader) throws IOException {
		try (BufferedReader br = new BufferedReader(reader)) {
			readSrg(br);
		}
	}

	private void readSrg(BufferedReader r) throws IOException {
		if (mappings.visitHeader()) {
			mappings.visitNamespaces(OFFICIAL_NAMESPACE, List.of(SRG_NAMESPACE, NAMED_NAMESPACE));
		}
		if (mappings.visitContent()) {
			String line;
			int lineNumber = 0;

			String cls = null;
			boolean visitCls = false;

			while ((line = r.readLine()) != null) {
				lineNumber++;
				String[] args = line.split("\\s");

				String type = args[0];

				if ("CL:".equals(type)) {
					if (args.length != 3) {
						throw new IOException("invalid class mapping on line " + lineNumber);
					}

					String src = args[1];
					String dst = args[2];

					if (src == null || src.isEmpty()) {
						throw new IOException("invalid src name for class mapping on line " + lineNumber);
					}
					if (dst == null || dst.isEmpty()) {
						throw new IOException("invalid dst name for class mapping on line " + lineNumber);
					}

					if (!src.equals(cls)) {
						cls = src;
						visitCls = mappings.visitClass(src);

						if (visitCls) {
							mappings.visitDstName(MappedElementKind.CLASS, 0, dst);
							mappings.visitDstName(MappedElementKind.CLASS, 1, dst);
							visitCls = mappings.visitElementContent(MappedElementKind.CLASS);
						}
					}
				} else if ("FD:".equals(type) || "MD:".equals(type)) {
					boolean field = "FD:".equals(type);

					if (field) {
						if (args.length != 3) {
							throw new IOException("invalid field mapping on line " + lineNumber);
						}
					} else {
						if (args.length != 5) {
							throw new IOException("invalid method mapping on line " + lineNumber);
						}
					}

					String srcCls = null;
					String dstCls = null;
					String src = null;
					String dst = null;
					String srcDesc = null;

					if (field) {
						src = args[1];
						dst = args[2];
					} else {
						src = args[1];
						dst = args[3];
						srcDesc = args[2];

						if (srcDesc == null || srcDesc.isEmpty()) {
							throw new IOException("invalid src descriptor for method mapping on line " + lineNumber);
						}
					}

					int srcSep = src.lastIndexOf('/');
					int dstSep = dst.lastIndexOf('/');

					if (src == null || src.isEmpty() || srcSep <= 0) {
						throw new IOException("invalid src name for " + (field ? "field" : "method") + " mapping on line " + lineNumber);
					}
					if (dst == null || dst.isEmpty() || dstSep <= 0) {
						throw new IOException("invalid dst name for " + (field ? "field" : "method") + " mapping on line " + lineNumber);
					}

					srcCls = src.substring(0, srcSep);
					dstCls = dst.substring(0, dstSep);
					src = src.substring(srcSep + 1);
					dst = dst.substring(dstSep + 1);

					if (!srcCls.equals(cls)) {
						cls = srcCls;
						visitCls = mappings.visitClass(srcCls);

						if (visitCls) {
							mappings.visitDstName(MappedElementKind.CLASS, 0, dstCls);
							mappings.visitDstName(MappedElementKind.CLASS, 1, dstCls);
							visitCls = mappings.visitElementContent(MappedElementKind.CLASS);
						}
					}

					if (visitCls) {
						if (field ? mappings.visitField(src, null) : mappings.visitMethod(src, srcDesc)) {
							MappedElementKind kind = field ? MappedElementKind.FIELD : MappedElementKind.METHOD;
							boolean obf = field ? dst.startsWith("field_") : dst.startsWith("func_");

							mappings.visitDstName(kind, 0, dst);
							if (!obf) {
								mappings.visitDstName(kind, 1, dst);
							}
							mappings.visitElementContent(kind);

							if (field) {
								fieldClasses.computeIfAbsent(dst, key -> new HashSet<>()).add(cls);
							} else {
								methodClasses.computeIfAbsent(dst, key -> new HashSet<>()).add(cls);

								if (dst.indexOf('_') > 0) {
									methods.put(dst.split("[_]")[1], dst);
								} else {
									// not obfuscated probably
								}
							}
						}
					}
				} else {
					// ignore line
				}
			}
		}
		if (mappings.visitEnd()) {
		}
	}

	private void readFields(Reader reader) throws IOException {
		try (BufferedReader br = new BufferedReader(reader)) {
			readFields(br);
		}
	}

	private void readFields(BufferedReader r) throws IOException {
		if (mappings.visitHeader()) {
			mappings.visitNamespaces(SRG_NAMESPACE, List.of(NAMED_NAMESPACE));
		}
		if (mappings.visitContent()) {
			String line;
			int lineNumber = 0;

			while ((line = r.readLine()) != null) {
				if (lineNumber++ == 0) {
					continue; // header
				}

				String[] args = line.split("[,]", 4);

				if (args.length != 4) {
					throw new IOException("invalid field mapping on line " + lineNumber);
				}

				String srg = args[0];
				String dst = args[1];
//				String side = args[2];
				String jav = args[3];

//				if (!"2".equals(side)) {
//					continue;
//				}

				if (srg == null || srg.isEmpty()) {
					throw new IOException("invalid srg name for field mapping on line " + lineNumber);
				}
				if (dst == null || dst.isEmpty()) {
					throw new IOException("invalid dst name for field mapping on line " + lineNumber);
				}

				Collection<String> clss = fieldClasses.get(srg);

				if (clss.isEmpty()) {
					throw new IOException("unknown field mapping on line " + lineNumber);
				}

				for (String clsName : clss) {
					ClassMapping cm = mappings.getClass(clsName);
					String clsSrg = cm.getName(SRG_NAMESPACE);

					if (mappings.visitClass(clsSrg) && mappings.visitElementContent(MappedElementKind.CLASS)) {
						FieldMapping fm = cm.getField(srg, null, 0);

						if (fm == null) {
							throw new IOException("field " + srg + " went missing!");
						} else {
							mappings.visitField(srg, null);
							mappings.visitDstName(MappedElementKind.FIELD, 0, dst);
							if (jav != null && !jav.isEmpty()) {
								mappings.visitComment(MappedElementKind.FIELD, jav);
							}
						}
					}
				}
			}
		}
		if (mappings.visitEnd()) {
		}
	}

	private void readMethods(Reader reader) throws IOException {
		try (BufferedReader br = new BufferedReader(reader)) {
			readMethods(br);
		}
	}

	private void readMethods(BufferedReader r) throws IOException {
		if (mappings.visitHeader()) {
			mappings.visitNamespaces(SRG_NAMESPACE, List.of(NAMED_NAMESPACE));
		}
		if (mappings.visitContent()) {
			String line;
			int lineNumber = 0;

			while ((line = r.readLine()) != null) {
				if (lineNumber++ == 0) {
					continue; // header
				}

				String[] args = line.split("[,]", 4);

				if (args.length != 4) {
					throw new IOException("invalid method mapping on line " + lineNumber);
				}

				String srg = args[0];
				String dst = args[1];
//				String side = args[2];
				String jav = args[3];

//				if (!"2".equals(side)) {
//					continue;
//				}

				if (srg == null || srg.isEmpty()) {
					throw new IOException("invalid srg name for method mapping on line " + lineNumber);
				}
				if (dst == null || dst.isEmpty()) {
					throw new IOException("invalid dst name for method mapping on line " + lineNumber);
				}

				Collection<String> clss = methodClasses.get(srg);

				if (clss.isEmpty()) {
					throw new IOException("unknown method mapping on line " + lineNumber);
				}

				for (String clsName : clss) {
					ClassMapping cm = mappings.getClass(clsName);
					String clsSrg = cm.getName(SRG_NAMESPACE);

					if (mappings.visitClass(clsSrg) && mappings.visitElementContent(MappedElementKind.CLASS)) {
						MethodMapping mm = cm.getMethod(srg, null, 0);

						if (mm == null) {
							throw new IOException("method " + srg + " went missing!");
						} else {
							mappings.visitMethod(srg, null);
							mappings.visitDstName(MappedElementKind.METHOD, 0, dst);
							if (jav != null && !jav.isEmpty()) {
								mappings.visitComment(MappedElementKind.METHOD, jav);
							}
						}
					}
				}
			}
		}
		if (mappings.visitEnd()) {
		}
	}

	private void readParams(Reader reader) throws IOException {
		try (BufferedReader br = new BufferedReader(reader)) {
			readParams(br);
		}
	}

	private void readParams(BufferedReader r) throws IOException {
		if (mappings.visitHeader()) {
			mappings.visitNamespaces(SRG_NAMESPACE, List.of(NAMED_NAMESPACE));
		}
		if (mappings.visitContent()) {
			String line;
			int lineNumber = 0;

			while ((line = r.readLine()) != null) {
				if (lineNumber++ == 0) {
					continue; // header
				}

				String[] args = line.split("[,]", 3);

				if (args.length != 3) {
					throw new IOException("invalid parameter mapping on line " + lineNumber);
				}

				String srg = args[0];
				String dst = args[1];
//				String side = args[2];

//				if (!"2".equals(side)) {
//					continue;
//				}

				if (srg == null || srg.isEmpty()) {
					throw new IOException("invalid srg name for parameter mapping on line " + lineNumber);
				}
				if (dst == null || dst.isEmpty()) {
					throw new IOException("invalid dst name for parameter mapping on line " + lineNumber);
				}

				String[] parts = srg.split("[_]", 4);

				if (parts.length < 3) {
					throw new IOException("invalid srg name for parameter mapping on line " + lineNumber);
				}

				String methodId = parts[1];
				int idx = Integer.parseInt(parts[2]);

				if (methodId.startsWith("i")) {
					// TODO: read params from joined.exc?
					// I think those are synthetic methods so might not be necessary...
					continue;
				}

				String mtdName = methods.get(methodId);
				Collection<String> clss = methodClasses.get(mtdName);

				if (clss.isEmpty()) {
					throw new IOException("unknown parameter mapping on line " + lineNumber);
				}

				for (String clsName : clss) {
					ClassMapping cm = mappings.getClass(clsName);
					String clsSrg = cm.getName(SRG_NAMESPACE);

					if (mappings.visitClass(clsSrg) && mappings.visitElementContent(MappedElementKind.CLASS)) {
						MethodMapping mm = cm.getMethod(mtdName, null, 0);

						if (mm == null) {
							throw new IOException("method " + srg + " went missing!");
						} else {
							if (mappings.visitMethod(mtdName, null) && mappings.visitElementContent(MappedElementKind.METHOD)) {
								mappings.visitMethodArg(-1, idx, null);
								mappings.visitDstName(MappedElementKind.METHOD_ARG, 0, dst);
							}
						}
					}
				}
			}
		}
		if (mappings.visitEnd()) {
		}
	}
}
