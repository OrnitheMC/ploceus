package net.ornithemc.ploceus.mcp.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree.ElementMapping;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class McpReader {

	public static void read(McpFiles files, MappingVisitor visitor) throws IOException {
		read(files).accept(new MappingSourceNsSwitch(new MappingDstNsReorder(visitor, NAMED_NAMESPACE), INTERMEDIARY_NAMESPACE, true));
	}

	public static MappingTreeView read(McpFiles files) throws IOException {
		return new McpReader(files).read();
	}

	private static final String OFFICIAL_NAMESPACE     = MappingsNamespace.OFFICIAL.name().toLowerCase();
	private static final String INTERMEDIARY_NAMESPACE = MappingsNamespace.INTERMEDIARY.name().toLowerCase();
	private static final String SRG_NAMESPACE          = "srg";
	private static final String NAMED_NAMESPACE        = MappingsNamespace.NAMED.name().toLowerCase();

	private McpFiles files;
	// The CSV files for field, method, and parameter mappings
	// do not contain information about the enclosing classes
	// of the mappings, so we cache those in these maps when
	// reading the SRG file.
	private final Map<String, Collection<String>> fieldClasses;
	private final Map<String, Collection<String>> methodClasses;
	private final Map<String, String> methods;

	private MemoryMappingTree mappings;

	private McpReader(McpFiles files) {
		this.files = files;
		this.fieldClasses = new HashMap<>();
		this.methodClasses = new HashMap<>();
		this.methods = new HashMap<>();
	}

	private MappingTreeView read() throws IOException {
		mappings = new MemoryMappingTree();

		try (InputStreamReader input = new InputStreamReader(files.readIntermediary())) {
			MappingReader.read(input, mappings);
		}
		try (InputStreamReader input = new InputStreamReader(files.readSrg())) {
			readSrg(input);
		}
		try (InputStreamReader input = new InputStreamReader(files.readFields())) {
			readFields(input);
		}
		try (InputStreamReader input = new InputStreamReader(files.readMethods())) {
			readMethods(input);
		}
		try (InputStreamReader input = new InputStreamReader(files.readParams())) {
			readParams(input);
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
					String srg = args[2];

					if (src == null || src.isEmpty()) {
						throw new IOException("invalid src name for class mapping on line " + lineNumber);
					}
					if (srg == null || srg.isEmpty()) {
						throw new IOException("invalid srg name for class mapping on line " + lineNumber);
					}

					if (!src.equals(cls)) {
						cls = src;
						visitCls = mappings.visitClass(src);

						if (visitCls) {
							// for classes, srg == named
							mappings.visitDstName(MappedElementKind.CLASS, 0, srg);
							mappings.visitDstName(MappedElementKind.CLASS, 1, srg);
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
					String srgCls = null;
					String src = null;
					String srg = null;
					String srcDesc = null;

					if (field) {
						src = args[1];
						srg = args[2];
					} else {
						src = args[1];
						srg = args[3];
						srcDesc = args[2];

						if (srcDesc == null || srcDesc.isEmpty()) {
							throw new IOException("invalid src descriptor for method mapping on line " + lineNumber);
						}
					}

					int srcSep = src.lastIndexOf('/');
					int dstSep = srg.lastIndexOf('/');

					if (src == null || src.isEmpty() || srcSep <= 0) {
						throw new IOException("invalid src name for " + (field ? "field" : "method") + " mapping on line " + lineNumber);
					}
					if (srg == null || srg.isEmpty() || dstSep <= 0) {
						throw new IOException("invalid srg name for " + (field ? "field" : "method") + " mapping on line " + lineNumber);
					}

					srcCls = src.substring(0, srcSep);
					srgCls = srg.substring(0, dstSep);
					src = src.substring(srcSep + 1);
					srg = srg.substring(dstSep + 1);

					if (!srcCls.equals(cls)) {
						cls = srcCls;
						visitCls = mappings.visitClass(srcCls);

						if (visitCls) {
							// for classes, srg == named
							mappings.visitDstName(MappedElementKind.CLASS, 0, srgCls);
							mappings.visitDstName(MappedElementKind.CLASS, 1, srgCls);
							visitCls = mappings.visitElementContent(MappedElementKind.CLASS);
						}
					}

					if (visitCls) {
						ElementMapping mapping = field
							? mappings.getField(srcCls, src, null)
							: mappings.getMethod(srcCls, src, srcDesc);

						if (mapping != null && field ? mappings.visitField(src, null) : mappings.visitMethod(src, srcDesc)) {
							MappedElementKind kind = field ? MappedElementKind.FIELD : MappedElementKind.METHOD;
							boolean obf = field ? srg.startsWith("field_") : srg.startsWith("func_");

							mappings.visitDstName(kind, 0, srg);
							if (!obf) {
								mappings.visitDstName(kind, 1, srg);
							}
							mappings.visitElementContent(kind);

							if (field) {
								fieldClasses.computeIfAbsent(srg, key -> new HashSet<>()).add(srgCls);
							} else {
								methodClasses.computeIfAbsent(srg, key -> new HashSet<>()).add(srgCls);

								if (srg.indexOf('_') > 0) {
									methods.put(srg.split("[_]")[1], srg);
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

				Collection<String> srgClss = fieldClasses.get(srg);

				if (srgClss.isEmpty()) {
					throw new IOException("unknown field mapping on line " + lineNumber);
				}

				for (String srgCls : srgClss) {
					if (mappings.visitClass(srgCls) && mappings.visitElementContent(MappedElementKind.CLASS)) {
						mappings.visitField(srg, null);
						mappings.visitDstName(MappedElementKind.FIELD, 0, dst);
						if (jav != null && !jav.isEmpty()) {
							mappings.visitComment(MappedElementKind.FIELD, jav);
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

				Collection<String> srgClss = methodClasses.get(srg);

				if (srgClss.isEmpty()) {
					throw new IOException("unknown method mapping on line " + lineNumber);
				}

				for (String srgCls : srgClss) {
					if (mappings.visitClass(srgCls) && mappings.visitElementContent(MappedElementKind.CLASS)) {
						mappings.visitMethod(srg, null);
						mappings.visitDstName(MappedElementKind.METHOD, 0, dst);
						if (jav != null && !jav.isEmpty()) {
							mappings.visitComment(MappedElementKind.METHOD, jav);
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

				String srgMtd = methods.get(methodId);
				Collection<String> srgClss = methodClasses.get(srgMtd);

				if (srgClss.isEmpty()) {
					throw new IOException("unknown parameter mapping on line " + lineNumber);
				}

				for (String srgCls : srgClss) {
					if (mappings.visitClass(srgCls) && mappings.visitElementContent(MappedElementKind.CLASS)) {
						if (mappings.visitMethod(srgMtd, null) && mappings.visitElementContent(MappedElementKind.METHOD)) {
							mappings.visitMethodArg(-1, idx, null);
							mappings.visitDstName(MappedElementKind.METHOD_ARG, 0, dst);
						}
					}
				}
			}
		}
		if (mappings.visitEnd()) {
		}
	}
}
