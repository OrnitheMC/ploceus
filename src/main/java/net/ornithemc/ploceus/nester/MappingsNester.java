package net.ornithemc.ploceus.nester;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.Nests;

public class MappingsNester {

	private final MappingTree mappings;
	private final Nests nests;
	private final Map<String, String> translations;
	private final int nsid;

	public MappingsNester(MappingTree mappings, Nests nests) {
		this.mappings = mappings;
		this.nests = nests;
		this.translations = new HashMap<>();
		this.nsid = this.mappings.getNamespaceId(MappingsNamespace.NAMED.toString());
	}

	public void apply(MappingVisitor visitor) throws IOException {
		for (Nest nest : nests) {
			String className = nest.className;

			if (visitor.visitClass(className)) {
				visitor.visitDstName(MappedElementKind.CLASS, nsid, translate(className));
			}
		}
	}

	private String translate(String className) {
		String translation = translations.get(className);

		if (translation == null) {
			translations.put(className, translation = findTranslation(className));
		}

		return translation;
	}

	private String findTranslation(String className) {
		Nest nest = nests.get(className);
		String mappedName = mappings.mapClassName(className, nsid);

		return (nest == null) ? mappedName : applyNest(mappedName, nest);
	}

	private String applyNest(String mappedName, Nest nest) {
		// this allows for mappings for nested classes to be
		// saved mostly intact, which makes translation here 
		// easier
		String enclTranslation = translate(nest.enclClassName);
		String translation = mappedName.replace("__", "$");
		int idx = translation.lastIndexOf('$');

		if (idx > 0) {
			ClassMapping ec = mappings.getClass(nest.enclClassName);

			if (ec == null) {
				// no mapping exists, this is slightly jank
				// but we assume that is because the class
				// does not exist (nester supports generating
				// outer classes)
				// return the mapping as-is
				return translation;
			} else {
				// validation to make sure the mapping is
				// actually encoding nesting properly
				if (translation.startsWith(enclTranslation)) {
					return translation;
				}
			}
		}

		// in this case we need to 'generate' the translation
		String innerName;

		if (nest.isAnonymous()) {
			innerName = nest.innerName;
		} else {
			String prefix = "";

			if (nest.isLocal()) {
				prefix = stripInnerName(nest.innerName);
			}

			innerName = prefix + stripPackage(translation);

		}

		return translation = enclTranslation + "$" + innerName;
	}

	private static String stripPackage(String className) {
		return className.substring(className.lastIndexOf('/') + 1);
	}

	private static String stripInnerName(String simpleName) {
		int i = 0;

		while (i < simpleName.length() && Character.isDigit(simpleName.charAt(i))) {
			i++;
		}

		return simpleName.substring(0, i);
	}
}
