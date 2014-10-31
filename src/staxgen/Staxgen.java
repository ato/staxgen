package staxgen;

import java.io.File;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;

import staxgen.schema.ComplexType;
import staxgen.schema.ExplicitGroup;
import staxgen.schema.LocalElement;
import staxgen.schema.OpenAttrs;
import staxgen.schema.Schema;
import staxgen.schema.TopLevelComplexType;


public class Staxgen {

	private static final String XS = "http://www.w3.org/2001/XMLSchema";
	private static final QName XS_BOOLEAN = new QName(XS, "boolean");
	private static final QName XS_STRING = new QName(XS, "string");
		
	private static String uncap(String s) {
		return Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}
	
	private static void compile(ComplexType type, Emitter emit) {
		emit.startClass(type.getName());
		compileFields(type, emit);
		emit.println("");
		compileParser(type, emit);
		emit.endBlock();
		emit.println("");
		emit.flush();
	}
	
	private static void compileParser(ComplexType type, Emitter emit) {
		String typeName = type.getName();
		String varName = uncap(typeName); 
		emit.parseMethod(typeName);
		emit.declareVar(typeName, varName);
		emit.whileNextTag();
		emit.switchOnTagName();
		for (Object x : type.getAll().getParticle()) {
			JAXBElement<?> something = (JAXBElement<?>)x;
			LocalElement local = (LocalElement)something.getValue();
			emit.switchCase(local.getName());
			if (local.getType() == null) {
				if (local.getComplexType() != null && local.getComplexType().getSequence() != null) {
					ExplicitGroup sequence = local.getComplexType().getSequence();
					if (sequence.getParticle().get(0) instanceof JAXBElement) {
						LocalElement seqEl = (LocalElement) ((JAXBElement<?>) sequence.getParticle().get(0)).getValue();
						emit.whileNextTag();
						emit.ifTagName(seqEl.getName());
						if (seqEl.getType().equals(XS_STRING)) {
							emit.addString(local.getName());
						} else {
							emit.addComplex(local.getName(), seqEl.getType().getLocalPart());
						}
						emit.elseExpected(seqEl.getName());
						emit.endBlock();
					} else {
						emit.putString(local.getName());
					}
				} else {
					throw new UnsupportedOperationException("not implemented");
				}
			} else if (local.getType().equals(XS_STRING)) {
				emit.setString(local.getName());
			} else if (local.getType().equals(XS_BOOLEAN)) {
				emit.setBoolean(local.getName());
			} else {
				emit.setComplex(local.getName(), local.getType().getLocalPart());
			}
			emit.switchBreak();
			emit.endBlock();
		}
		emit.switchDefault();
		emit.endBlock();
		emit.endBlock();
		emit.returnVar();
		emit.endBlock();
	}
	
	private static void compileFields(ComplexType type, Emitter emit) {
		for (Object x : type.getAll().getParticle()) {
			JAXBElement<?> something = (JAXBElement<?>)x;
			LocalElement local = (LocalElement)something.getValue();
			if (local.getType() == null) { 
				if (local.getComplexType() != null && local.getComplexType().getSequence() != null) {
					ExplicitGroup sequence = local.getComplexType().getSequence();
					if (sequence.getParticle().get(0) instanceof JAXBElement) {
						LocalElement seqEl = (LocalElement) ((JAXBElement<?>) sequence.getParticle().get(0)).getValue();
						if (seqEl.getType().equals(XS_STRING)) {
							emit.fieldList("String", local.getName());
						} else {
							emit.fieldList(seqEl.getType().getLocalPart(), local.getName());
						}
					} else {
						emit.fieldMap(local.getName());
					}
				} else {
					throw new UnsupportedOperationException("not implemented");
				}
			} else if (local.getType().equals(XS_STRING)) {
				if (local.getDefault() != null) {
					emit.fieldString(local.getName(), local.getDefault());
				} else {
					emit.fieldString(local.getName());
				}
			} else if (local.getType().equals(XS_BOOLEAN)) {
				if (local.getDefault() != null) {
					emit.fieldBoolean(local.getName(), Boolean.parseBoolean(local.getDefault()));
				} else {
					emit.fieldBoolean(local.getName());
				}
			} else {
				emit.fieldComplex(local.getType().getLocalPart(), local.getName());
			}
		}
	}
	
	public static void usage() {
		System.err.println("Usage: staxgen [-v] [-o dir] [-p package] schema.xsd");
		System.exit(1);
	}
	
	public static void main(String[] args) throws Exception {
		Emitter emitter = new Emitter();
		File schemaFile = null;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "-v":
				emitter.verbose = true;
				break;
			case "-o":
				emitter.outDir = new File(args[++i]);
				break;
			case "-p":
				emitter.packageName = args[++i];
				break;
			default:
				if (args[i].startsWith("-")) {
					usage();
				} else {
					schemaFile = new File(args[i]);
				}
			}
		}
		
		if (schemaFile == null) {
			usage();
		}
		
		JAXBContext jc = JAXBContext.newInstance("staxgen.schema");
		Unmarshaller um = jc.createUnmarshaller();
		JAXBElement<Schema> je = um.unmarshal(new StreamSource(schemaFile), Schema.class);
		Schema schema = je.getValue();
		
		for (OpenAttrs el : schema.getSimpleTypeOrComplexTypeOrGroup()) {
			if (el instanceof TopLevelComplexType) {
				TopLevelComplexType type = (TopLevelComplexType)el;
				compile(type, emitter);
			}
		}
	}

}
