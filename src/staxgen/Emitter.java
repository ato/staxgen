package staxgen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

class Emitter {
	PrintWriter out = new PrintWriter(System.out);
	int depth = 0;
	boolean useGetters = true, useSetters = true;
	boolean generateConstructor = true;
	String var = "this", packageName;
	File outDir;
	public boolean verbose = false;
	
	private static String cap(String s) {
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
	
	private Emitter indent() {
		for (int i = 0; i < depth; i++) {
			out.append("    ");
		}
		return this;
	}
	
	private Emitter updent() {
		try {
			return indent();
		} finally {
			depth++;
		}
	}
	
	private Emitter getter(String var, String field) {
		if (useGetters) {
			return this.format("%s.%s", var, field);
		} else {
			return this.format("%s.get%s()", var, cap(field));				
		}
	}

	private Emitter setter(String var, String field) {
		if (useSetters) {
			return this.format("%s.%s = ", var, field);								
		} else {
			return this.format("%s.set%s(", var, cap(field));				
		}
	}
	
	private void endSetter() {
		if (useSetters) {
			out.println(";");
		} else {
			out.println(");");
		}
	}
	
	private Emitter format(String format, Object... args) {
		out.format(format, args);
		return this;
	}
	
	void parseMethod(String type) {
		if (generateConstructor) {
			indent().format("public %s() {}\n\n", type);
			updent().format("public %s(XMLStreamReader xml) throws XMLStreamException {\n", type);				
		} else {
			updent().format("%s parse%s(XMLStreamReader xml) throws XMLStreamException {\n", type, type);
		}
	}

	void declareVar(String type, String var) {
		if (!generateConstructor) {
			this.var = var;
			indent().format("%s %s = new %s();\n", type, var, type);
		}
	}
	
	void whileNextTag() {
		updent().println("while (xml.nextTag() == START_ELEMENT) {");
	}
	
	void switchOnTagName() {
		updent().println("switch(xml.getLocalName()) {");
	}
	
	void switchCase(String value) {
		updent().format("case \"%s\": {\n", value);
	}

	void switchBreak() {
		indent().println("break;");
	}

	void switchDefault() {
		updent().println("default: {");
		indent().println("throw new XMLStreamException(\"Unexpected tag: \" + xml.getLocalName(), xml.getLocation());");
		depth--;
		indent().println("}");
	}
	
	void ifTagName(String tagName) {
		updent().format("if (xml.getLocalName().equals(\"%s\")) {\n", tagName);
	}
	
	public void elseExpected(String name) {
		depth--;
		updent().println("} else {");
		indent().format("throw new XMLStreamException(\"Expected <%s> but got: \" + xml.getLocalName(), xml.getLocation());\n", name);
		endBlock();
	}

			
	void endBlock() {
		depth--;
		indent().println("}");
	}

	public void println(String s) {
		out.println(s);
	}

	private Emitter print(String s) {
		out.print(s);
		return this;
	}
	
	void setString(String field) {
		indent().setter(var, field).print("xml.getElementText()").endSetter();
	}
	
	void setBoolean(String field) {
		indent().setter(var, field).print("Boolean.parseBoolean(xml.getElementText())").endSetter();
	}

	void setComplex(String field, String type) {
		if (generateConstructor) {
			indent().setter(var, field).format("new %s(xml)", type).endSetter();
		} else {
			indent().setter(var, field).format("parse%s(xml)", type).endSetter();
		}
	}
	
	void addComplex(String listName, String itemType) {
		if (generateConstructor) {
			indent().getter(var, listName).format(".add(new %s(xml));\n", itemType);
		} else {
			indent().getter(var, listName).format(".add(parse%s(xml));\n", itemType);				
		}
	}

	void addString(String listName) {
		indent().getter(var, listName).println(".add(xml.getElementText());");
	}

	void putString(String field) {
		indent().getter(var, field).println(".put(xml.getLocalName(), xml.getElementText());");
	}
	
	void returnVar() {
		if (!generateConstructor) {
			indent().format("return %s;\n", var);
		}
	}

	public void flush() {
		out.flush();
	}
	
	void fieldString(String name) {
		indent().format("String %s;\n", name);
	}

	void fieldString(String name, String value) {
		indent().format("String %s = \"%s\";\n", name, value);
	}
	
	void fieldBoolean(String name) {
		indent().format("boolean %s;\n", name);
	}

	void fieldBoolean(String name, boolean defaultValue) {
		indent().format("boolean %s = %b;\n", name, defaultValue);
	}

	void fieldList(String type, String name) {
		indent().format("List<%s> %s = new ArrayList<>();\n", type, name);
	}
	
	void fieldComplex(String type, String name) {
		indent().format("%s %s = new %s();\n", type, name, type);
	}

	void fieldMap(String name) {
		indent().format("Map<String,String> %s = new HashMap<>();\n", name);
	}
	
	void startClass(String name) {
		openClassFile(name);
		if (packageName != null) {
			indent().format("package %s;\n", packageName);
			println("");
		}
		indent().println("import java.util.ArrayList;");
		indent().println("import java.util.HashMap;");
		indent().println("import java.util.List;");
		indent().println("import java.util.Map;");
		indent().println("import javax.xml.stream.XMLStreamException;");
        indent().println("import javax.xml.stream.XMLStreamReader;");
        indent().println("import static javax.xml.stream.XMLStreamReader.START_ELEMENT;");
        println("");
		updent().format("public class %s {\n", name);
	}

	private void openClassFile(String className) {
		if (outDir != null) {
			File pkgDir = outDir;
			if (packageName != null) {
				pkgDir = new File(outDir, packageName.replace('.', '/'));
			}
			pkgDir.mkdirs();
			try {
				if (out != null) {
					out.flush();
					out.close();
				}
				File classFile = new File(pkgDir, className + ".java");
				if (verbose) {
					System.err.println("Creating " + classFile);
				}
				out = new PrintWriter(classFile);
			} catch (FileNotFoundException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}