package net.ornithemc.ploceus.signatures;

import java.util.Map;
import java.util.TreeMap;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SignatureRemapper;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;

import io.github.gaming32.signaturechanger.SignatureMode;
import io.github.gaming32.signaturechanger.tree.MemberReference;
import io.github.gaming32.signaturechanger.tree.SignatureInfo;
import io.github.gaming32.signaturechanger.tree.SigsClass;
import io.github.gaming32.signaturechanger.tree.SigsFile;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.tree.MappingTree;

import net.ornithemc.ploceus.mappings.MappingsUtil;

public class SignaturesMapper {

	private final MappingTree mappings;

	private SigsFile sigs;
	private Remapper remapper;

	public SignaturesMapper(MappingTree mappings) {
		this.mappings = mappings;
	}

	public SigsFile apply(SigsFile sigs, MappingsNamespace fromNs, MappingsNamespace toNs) {
		this.sigs = sigs;
		this.remapper = MappingsUtil.getAsmRemapper(mappings, fromNs, toNs);

		return remap();
	}

	private SigsFile remap() {
		Map<String, SigsClass> mappedSigsSorted = new TreeMap<>();

		for (Map.Entry<String, SigsClass> ce : sigs.classes.entrySet()) {
			String cname = ce.getKey();
			SigsClass c = ce.getValue();
			SignatureMode cmode = c.signatureInfo.mode();
			String csignature = c.signatureInfo.signature();
			cname = remapper.map(cname);
			csignature = remapSignature(csignature);
			
			SigsClass cout = new SigsClass();
			cout.signatureInfo = new SignatureInfo(cmode, csignature);
			mappedSigsSorted.put(cname, cout);

			for (Map.Entry<MemberReference, SignatureInfo> me : c.members.entrySet()) {
				MemberReference m = me.getKey();
				String mname = m.name();
				String mdesc = m.desc().getDescriptor();
				SignatureMode mmode = me.getValue().mode();
				String msignature = me.getValue().signature();
				mname = mdesc.charAt(0) == '('
					? mdesc.charAt(0) == '<'
						? mname
						: remapper.mapMethodName(ce.getKey(), mname, mdesc)
					: remapper.mapFieldName(ce.getKey(), mname, mdesc);
				mdesc = remapper.mapDesc(mdesc);
				msignature = remapSignature(msignature);

				cout.visitMember(mname, mdesc, mmode, msignature);
			}
		}

		SigsFile mappedSigs = new SigsFile();
		mappedSigs.classes.putAll(mappedSigsSorted);

		return mappedSigs;
	}

	private String remapSignature(String signature) {
		if (signature == null || signature.isEmpty()) {
			return null;
		} else {
			SignatureReader reader = new SignatureReader(signature);
			SignatureWriter writer = new SignatureWriter();
			reader.accept(new SignatureRemapper(writer, remapper));
			return writer.toString();
		}
	}
}
