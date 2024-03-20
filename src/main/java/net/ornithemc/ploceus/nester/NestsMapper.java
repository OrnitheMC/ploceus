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
			String innerName = mapInnerName(nest.className, nest.innerName);
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
		MethodMapping m = mappings.getMethod(className, name, desc, fromNs);
		return (m == null) ? name : m.getDesc(toNs);
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

	private String mapInnerName(String className, String innerName) {
		if (named) {
			String mappedClassName = mapClassName(className);
			int idx = mappedClassName.lastIndexOf('$');

			if (idx > 0) {
				// provided mappings already apply nesting
				return mappedClassName.substring(idx + 1);
			}
		}

		int idx = 0;

		while (idx < innerName.length() && Character.isDigit(innerName.charAt(idx))) {
			idx++;
		}

		String name = innerName.substring(0, idx);
		return idx < innerName.length() ? name + mapClassName(className) : name;
	}
}
