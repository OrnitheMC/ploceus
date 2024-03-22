package net.ornithemc.ploceus.signatures;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SignatureRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;

import io.github.gaming32.signaturechanger.SignatureMode;
import io.github.gaming32.signaturechanger.tree.MemberReference;
import io.github.gaming32.signaturechanger.tree.SignatureInfo;
import io.github.gaming32.signaturechanger.tree.SigsClass;
import io.github.gaming32.signaturechanger.tree.SigsFile;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;

public class SigsMapper {

	private final MappingTree mappings;

	private SigsFile sigs;
	private SigsFile mappedSigs;

	private int fromNs;
	private int toNs;

	private Remapper remapper;

	public SigsMapper(MappingTree mappings) {
		this.mappings = mappings;
	}

	public SigsFile apply(SigsFile sigs, MappingsNamespace fromNs, MappingsNamespace toNs) {
		this.sigs = sigs;
		this.mappedSigs = new SigsFile();

		this.fromNs = this.mappings.getNamespaceId(fromNs.toString());
		this.toNs = this.mappings.getNamespaceId(toNs.toString());

		remap();

		return mappedSigs;
	}

	private void remap() {
		this.remapper = new SimpleRemapper(new HashMap<String, String>() {

			{
				for (ClassMapping c : mappings.getClasses()) {
					put(c.getName(fromNs), c.getName(toNs));
					for (FieldMapping f : c.getFields()) {
						put(c.getName(fromNs) + f.getName(fromNs), f.getName(toNs));
					}
					for (MethodMapping m : c.getMethods()) {
						put(c.getName(fromNs) + m.getName(fromNs) + m.getDesc(fromNs), m.getName(toNs));
					}
				}
			}

			@Override
			public String get(Object key) {
				String value = super.get(key);
				return value == null ? (String)key : value;
			}
		});

		for (Map.Entry<String, SigsClass> ce : sigs.classes.entrySet()) {
			String cname = ce.getKey();
			SigsClass c = ce.getValue();

			String csignature = c.signatureInfo.signature();
			SignatureMode cmode = c.signatureInfo.mode();

			cname = remapper.map(cname);
			csignature = remapSignature(csignature);

			SigsClass cout = sigs.visitClass(cname, cmode, csignature);

			for (Map.Entry<MemberReference, SignatureInfo> me : c.members.entrySet()) {
				MemberReference mkey = me.getKey();
				SignatureInfo m = me.getValue();

				String mname = mkey.name();
				String mdesc = mkey.desc().getDescriptor();
				SignatureMode mmode = m.mode();
				String msignature = m.signature();

				mname = (mdesc.charAt(0) == '(') ? remapper.mapMethodName(ce.getKey(), mname, mdesc) : remapper.mapFieldName(ce.getKey(), mname, mdesc);
				mdesc = remapper.mapDesc(mdesc);
				msignature = remapSignature(msignature);

				cout.visitMember(mname, mdesc, mmode, msignature);
			}
		}
	}

	private String remapSignature(String signature) {
		if (signature.isBlank()) {
			return null;
		} else {
			SignatureReader reader = new SignatureReader(signature);
			SignatureWriter writer = new SignatureWriter();
			reader.accept(new SignatureRemapper(writer, remapper));
			return writer.toString();
		}
	}
}
