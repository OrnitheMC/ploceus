package net.ornithemc.ploceus.mappings;

import java.util.HashMap;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;

public class MappingsUtil {

	public static Remapper getAsmRemapper(MappingTree mappings, MappingsNamespace srcNs, MappingsNamespace dstNs) {
		return getAsmRemapper(mappings, srcNs.toString(), dstNs.toString());
	}

	public static Remapper getAsmRemapper(MappingTree mappings, String srcNs, String dstNs) {
		return getAsmRemapper(mappings, mappings.getNamespaceId(srcNs), mappings.getNamespaceId(dstNs));
	}

	@SuppressWarnings("serial")
	public static Remapper getAsmRemapper(MappingTree mappings, int srcNs, int dstNs) {
		return new SimpleRemapper(new HashMap<String, String>() {

			{
				for (ClassMapping c : mappings.getClasses()) {
					if (c.getName(srcNs) != null) {
						put(c.getName(srcNs), c.getName(dstNs));
						for (FieldMapping f : c.getFields()) {
							if (f.getName(srcNs) != null) {
								put(c.getName(srcNs) + "." + f.getName(srcNs), f.getName(dstNs));
							}
						}
						for (MethodMapping m : c.getMethods()) {
							if (m.getName(srcNs) != null) {
//								put(c.getName(srcNs) + "." + m.getName(srcNs) + m.getDesc(srcNs), m.getName(dstNs));
							}
						}
					}
				}
			}

			@Override
			public String get(Object key) {
				String value = super.get(key);
				return value == null ? (String)key : value;
			}
		});
	}
}
