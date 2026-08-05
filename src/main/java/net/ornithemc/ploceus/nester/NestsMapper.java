package net.ornithemc.ploceus.nester;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.NestType;
import net.ornithemc.nester.nest.Nests;

public class NestsMapper {

	private final MappingTree mappings;

	private Nests nests;
	private Nests mappedNests;

	private boolean named;
	private int fromNs;
	private int toNs;

	public NestsMapper(MappingTree mappings) {
		this.mappings = mappings;
	}

	public Nests apply(Nests nests, MappingsNamespace fromNs, MappingsNamespace toNs) {
		this.nests = nests;
		this.mappedNests = Nests.empty();

		this.named = (toNs == MappingsNamespace.NAMED);
		this.fromNs = this.mappings.getNamespaceId(fromNs.toString());
		this.toNs = this.mappings.getNamespaceId(toNs.toString());

		for (Nest nest : this.nests) {
			NestType type = nest.type;
			String className = mapClassName(nest.className);
			String enclClassName = mapOuterName(nest.className, nest.enclClassName);
			String enclMethodName = (nest.enclMethodName == null) ? null : mapMethodName(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
			String enclMethodDesc = (nest.enclMethodName == null) ? null : mapMethodDesc(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
			String innerName = mapInnerName(nest.className, nest.enclClassName, nest.innerName);
			int access = nest.access;

			this.mappedNests.add(new Nest(type, className, enclClassName, enclMethodName, enclMethodDesc, innerName, access));
		}

		return this.mappedNests;
	}

	private String mapClassName(String name) {
		ClassMapping c = mappings.getClass(name, fromNs);
		return (c == null) ? name : c.getName(toNs);
	}

	private String mapMethodName(String className, String name, String desc) {
		MethodMapping m = mappings.getMethod(className, name, desc, fromNs);
		return (m == null) ? name : m.getName(toNs);
	}

	private String mapMethodDesc(String className, String name, String desc) {
		return mappings.mapDesc(desc, fromNs, toNs);
	}

	private String mapOuterName(String className, String enclClassName) {
		if (named) {
			String mappedClassName = mapClassName(className);
			int idx = mappedClassName.lastIndexOf('$');

			if (idx > 0) {
				// provided mappings already apply nesting
				return mappedClassName.substring(0, idx);
			}
		}

		return mapClassName(enclClassName);
	}

	private String mapInnerName(String className, String enclClassName, String innerName) {
		String mappedClassName = mapClassName(className);

		if (mappedClassName.equals(className)) {
			// class name maps to itself, keep given inner name
			return innerName;
		}

		if (named) {
			int idx = mappedClassName.lastIndexOf('$');

			if (idx > 0) {
				String mappedEnclClassName = mapClassName(enclClassName);

				if (idx == mappedEnclClassName.length() && mappedClassName.startsWith(mappedEnclClassName)) {
					// provided mappings already apply nesting
					return mappedClassName.substring(idx + 1);
				}
			}
		}

		int idx = 0;

		while (idx < innerName.length() && Character.isDigit(innerName.charAt(idx))) {
			idx++;
		}
		if (idx < innerName.length()) {
			// local classes have a number prefix
			String prefix = innerName.substring(0, idx);
			String simpleName = innerName.substring(idx);

			// make sure the class does not have custom inner name
			if (className.endsWith(simpleName)) {
				// inner name is full name with package stripped
				// so translate that
				innerName = prefix + mappedClassName.substring(mappedClassName.lastIndexOf('/') + 1);
			}
		} else {
			// anonymous class
			String simpleName = mappedClassName.substring(mappedClassName.lastIndexOf('/') + 1);

			if (simpleName.startsWith("C_")) {
				// mapped name is Calamus intermediary format C_<number>
				// we strip the C_ prefix and keep the number as the inner name
				return simpleName.substring(2);
			} else {
				// keep the inner name given by the nests file
				return innerName;
			}
		}

		return innerName;
	}
}
