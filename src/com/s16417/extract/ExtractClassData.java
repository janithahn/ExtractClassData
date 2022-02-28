package com.s16417.extract;

import com.google.common.reflect.ClassPath;
import javassist.*;
import javassist.bytecode.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

//import org.apache.lucene.util.RamUsageEstimator;

import java.lang.instrument.Instrumentation;

public class ExtractClassData {

    private static JSONArray packageList = new JSONArray();
    private static JSONArray classList = new JSONArray();

    private static JSONObject packageDetails = new JSONObject();

    private static Map map = new HashMap<Integer, Integer>(1);

    //private static final RamUsageEstimator RAM_USAGE_ESTIMATOR = new RamUsageEstimator();
    private static Instrumentation instrumentation;
    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
    }
    public static long getObjectSize(Object o) {
        return instrumentation.getObjectSize(o);
    }

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        extract("com.s16417.extract", "sample");
        //System.out.println("Map size(byte) -> " + RamUsageEstimator.sizeOf(map));

        List<Integer> intList = new ArrayList<Integer>(4);
        List<Integer> intList2 = new ArrayList<>();

        System.out.println(Arrays.stream(map.getClass().getDeclaredFields()).map(item -> item.getName()).collect(Collectors.toList()));

        final Field field = map.getClass().getDeclaredField("table");
        field.setAccessible(true);

        for(int i = 0 ; i < 13 ; i++){
            //intList.add(i);
            //System.out.println("intList size(byte) -> " + RamUsageEstimator.sizeOf(intList));
            //System.out.println(getObjectSize(i));
            map.put(i, i);
            //System.out.println(field.get(map));
            Object[] table = (Object[]) field.get(map);
            System.out.println(table == null ? 0 : table.length);
            //System.out.println("map size(byte) -> " + RamUsageEstimator.sizeOf(map));
            //System.out.println(getCapacity(map));
        }
        //System.out.println("intList size(byte) -> " + RamUsageEstimator.sizeOf(intList));
        //System.out.println("intList size(byte) -> " + RamUsageEstimator.sizeOf(intList2));
        //System.out.println("map size(byte) -> " + RamUsageEstimator.sizeOf(map));

        /*for(int i = 20 ; i < 40 ; i++){
            intList.add(i);
            map.put(i, i);
        }
        System.out.println("intList size(byte) -> " + RamUsageEstimator.sizeOf(intList));
        System.out.println("map size(byte) -> " + RamUsageEstimator.sizeOf(map));*/

        long end = System.currentTimeMillis();
        System.out.println("Runtime Millis: " + (end-start));
    }

    public static void extract(String packageName, String fileName) throws Exception {

        //String packageName = ExtractClassData.class.getPackage().getName();
        //Set<Class> classes = findAllClassesUsingGoogleGuice(packageName);
        Set<Class> classes = findAllClassesUsingGoogleGuice(packageName).isEmpty() ?
                findAllClassesUsingClassLoader(packageName): findAllClassesUsingGoogleGuice(packageName);
        Set<ClassFile> classFiles = insertClassesToClassFiles(classes);
        Set<CtClass> ctClasses = getCtClasses(classes);

        getClassData(classFiles, ctClasses);

        packageDetails.put("Package Name", packageName);
        packageDetails.put("Classes", classList);
        packageList.add(packageDetails);

        writeJson(fileName);
    }

    private static Set<Class> findAllClassesUsingGoogleGuice(String packageName) throws IOException {
        return ClassPath.from(ClassLoader.getSystemClassLoader())
                .getAllClasses()
                .stream()
                .filter(clazz -> clazz.getPackageName()
                        .equalsIgnoreCase(packageName))
                .map(clazz -> clazz.load())
                .collect(Collectors.toSet());
    }

    public static Set<Class> findAllClassesUsingClassLoader(String packageName) {
        InputStream stream = ClassLoader.getSystemClassLoader()
                .getResourceAsStream(packageName.replaceAll("[.]", "/"));
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        return reader.lines()
                .filter(line -> line.endsWith(".class"))
                .map(line -> getClass(line, packageName))
                .collect(Collectors.toSet());
    }

    private static Class getClass(String className, String packageName) {
        try {
            return Class.forName(packageName + "."
                    + className.substring(0, className.lastIndexOf('.')));
        } catch (ClassNotFoundException e) {
            // handle the exception
        }
        return null;
    }

    private static Set<ClassFile> insertClassesToClassFiles(Set<Class> classes) throws NotFoundException {
        ClassPool pool = ClassPool.getDefault();
        Set<ClassFile> classFiles = new HashSet<>();

        Iterator<Class> it = classes.iterator();
        while(it.hasNext()){
            String className = it.next().getName();
            pool.insertClassPath(className.replaceAll("[.]", "/"));
            CtClass ctClass = pool.getCtClass(className);
            ClassFile cf = ctClass.getClassFile();
            classFiles.add(cf);
        }

        return classFiles;
    }

    private static Set<CtClass> getCtClasses(Set<Class> classes) throws NotFoundException, ClassNotFoundException {
        ClassPool pool = ClassPool.getDefault();
        Set<CtClass> classFiles = new HashSet<>();

        Iterator<Class> it = classes.iterator();
        while(it.hasNext()){
            String className = it.next().getName();
            pool.insertClassPath(className.replaceAll("[.]", "/"));
            CtClass ctClass = pool.getCtClass(className);
            classFiles.add(ctClass);
        }

        return classFiles;
    }

    private static Set<CtClass> getAllSuperclasses(CtClass ctClass) throws NotFoundException {
        HashSet<CtClass> ctClasses = new HashSet<>();

        while (ctClass != null){
            ctClasses.add(ctClass);
            CtClass[] interfaces = ctClass.getInterfaces();
            Collections.addAll(ctClasses, interfaces);
            ctClass = ctClass.getSuperclass();
        }

        return ctClasses;
    }

    public static boolean isAssignable(CtClass thisCtClass, String targetClassName) {
        if ( thisCtClass == null ) {
            return false;
        }
        if ( thisCtClass.getName().equals( targetClassName ) ) {
            return true;
        }

        try {
            // check if extends
            if ( isAssignable( thisCtClass.getSuperclass(), targetClassName ) ) {
                return true;
            }
            // check if implements
            for ( CtClass interfaceCtClass : thisCtClass.getInterfaces() ) {
                if ( isAssignable( interfaceCtClass, targetClassName ) ) {
                    return true;
                }
            }
        }
        catch (NotFoundException e) {
            // keep going
        }
        return false;
    }

    public static boolean isAssignable(CtField thisCtField, String targetClassName) {
        try {
            return isAssignable( thisCtField.getType(), targetClassName );
        }
        catch (NotFoundException e) {
            // keep going
        }
        return false;
    }

    private static String inferGenericTypeName(CtClass ctClass, SignatureAttribute.Type genericSignature) {
        // infer targetEntity from generic type signature
        if ( isAssignable( ctClass, Collection.class.getName() ) ) {
            return ( (SignatureAttribute.ClassType) genericSignature ).getTypeArguments()[0].getType().jvmTypeName();
        }
        if ( isAssignable( ctClass, Map.class.getName() ) ) {
            return ( (SignatureAttribute.ClassType) genericSignature ).getTypeArguments()[1].getType().jvmTypeName();
        }
        return ctClass.getName();
    }

    private static String inferFieldTypeName(CtField field) {
        try {
            if ( field.getFieldInfo2().getAttribute( SignatureAttribute.tag ) == null ) {
                return field.getType().getName();
            }
            return inferGenericTypeName(
                    field.getType(),
                    SignatureAttribute.toTypeSignature( field.getGenericSignature() )
            );
        }
        catch (BadBytecode ignore) {
            return null;
        }
        catch (NotFoundException e) {
            return null;
        }
    }

    private static void getClassData(Set<ClassFile> classFiles, Set<CtClass> ctClasses) {

        // get everything from classFiles
        /*Iterator<ClassFile> it = classFiles.iterator();
        while(it.hasNext()) {
            JSONObject classDetails = new JSONObject();
            JSONArray fieldList = new JSONArray();
            JSONArray methodList = new JSONArray();

            ClassFile cf = it.next();

            // Fields
            for (Object fieldInfoObj : cf.getFields()) {
                JSONObject fieldDetails = new JSONObject();

                try {
                    FieldInfo fieldInfo = (FieldInfo)fieldInfoObj;
                    fieldDetails.put("Name", fieldInfo.getName());
                    fieldDetails.put("Descriptor", fieldInfo.getDescriptor());
                    System.out.println(fieldInfo.getDescriptor());
                    fieldList.add(fieldDetails);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }*/

            // Methods
            /*for (Object m : cf.getMethods()) {
                JSONObject methodDetails = new JSONObject();
                JSONArray localMethodParamList = new JSONArray();
                JSONArray localMethodVarList = new JSONArray();

                try {
                    MethodInfo minfo = (MethodInfo) m;
                    methodDetails.put("Method Name", minfo.getName());

                    CodeAttribute ca = minfo.getCodeAttribute();
                    LocalVariableAttribute table = (LocalVariableAttribute) ca.getAttribute(LocalVariableAttribute.tag);
                    int pc = 0;
                    int n = table.tableLength();
                    for (int i = 0; i < n; ++i) {
                        JSONObject localMethodParamDetails = new JSONObject();
                        JSONObject localMethodVarDetails = new JSONObject();

                        int start = table.startPc(i);
                        int len = table.codeLength(i);

                        if (start <= pc && pc < start + len) {
                            if (!table.variableName(i).equals("this")) {
                                localMethodParamDetails.put("Name", table.variableName(i));
                                localMethodParamDetails.put("Descriptor", table.descriptor(i));
                                localMethodParamList.add(localMethodParamDetails);
                            }
                        } else {
                            localMethodVarDetails.put("Name", table.variableName(i));
                            localMethodVarDetails.put("Descriptor", table.descriptor(1));
                            localMethodVarList.add(localMethodVarDetails);
                        }
                    }

                    methodDetails.put("Parameters", localMethodParamList);
                    methodDetails.put("Variables", localMethodVarList);
                    methodList.add(methodDetails);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }

            classDetails.put("Class Name", cf.getName());
            classDetails.put("Methods", methodList);
            classDetails.put("Fields", fieldList);

            classList.add(classDetails);*/
        //}

        // Data from Classes
        Iterator<CtClass> ctIte = ctClasses.iterator();

        while(ctIte.hasNext()) {
            JSONObject classDetails = new JSONObject();
            JSONArray fieldList = new JSONArray();
            JSONArray methodList = new JSONArray();

            CtClass ctClass = ctIte.next();
            System.out.println("Class Name: " + ctClass.getName() + "\n");

            // Fields from ctClasses
            System.out.println("---------- Fields ----------\n");
            for(CtField ctField: ctClass.getDeclaredFields()) {
                //CtField ctField = (CtField) fieldInfoObj;

                JSONObject fieldDetails = new JSONObject();

                try {
                    FieldInfo fieldInfo = ctField.getFieldInfo();
                    fieldDetails.put("Name", fieldInfo.getName());
                    fieldDetails.put("Descriptor", fieldInfo.getDescriptor());

                    System.out.println("Name: " + fieldInfo.getName());
                    System.out.println("Descriptor: " + fieldInfo.getDescriptor());

                    ArrayList a = new ArrayList(15);
                    Map map2 = new HashMap(18);
                    System.out.println("Size: " + getCapacity(map2));

                }catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    fieldList.add(fieldDetails);
                }

                try {
                    FieldInfo fieldInfo = ctField.getFieldInfo();
                    SignatureAttribute sig = (SignatureAttribute) fieldInfo.getAttribute(SignatureAttribute.tag);
                    fieldDetails.put("Signature Attr", SignatureAttribute.toFieldSignature(sig.getSignature()).toString());

                    System.out.println("Signature Attr: " + SignatureAttribute.toFieldSignature(sig.getSignature()));
                }catch (Exception e){
                    e.printStackTrace();
                }

                try {
                    for(Object obj: ctField.getType().getConstructors()) {
                        System.out.println(obj);
                    }
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }

                /*try {
                    System.out.println(ctField);
                }catch (Exception e) {
                    e.printStackTrace();
                }*/

                if(!Modifier.isStatic(ctField.getModifiers())) {
                    System.out.println("Non-Static Field");
                    fieldDetails.put("Static", "No");
                }else {
                    System.out.println("Static Field");
                    fieldDetails.put("Static", "Yes");
                }

                System.out.println("inferFieldTypeName: " + inferFieldTypeName(ctField));
                fieldDetails.put("Type/Generic", inferFieldTypeName(ctField));

                //System.out.println(ctField.getGenericSignature());
                //System.out.println(ctField.getType().getGenericSignature());

                CtClass type = null;
                try {
                    type = ctField.getType();
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }

                Set<String> allSupper = null;
                try {
                    allSupper = getAllSuperclasses(type)
                            .stream()
                            .map(CtClass::getName)
                            .collect(Collectors.toSet());
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }

                fieldDetails.put("Notes", "");
                if (allSupper.contains(Map.class.getCanonicalName())){
                    System.out.format("field %s is a Map\n", ctField.getName());
                    fieldDetails.put("Notes", fieldDetails.get("Notes") + "|Map");
                }

                if (allSupper.contains(Collection.class.getCanonicalName())){
                    System.out.format("field %s is a Collection\n", ctField.getName());
                    fieldDetails.put("Notes", fieldDetails.get("Notes") + "|Collection");
                }

                if (allSupper.contains(HashSet.class.getCanonicalName())){
                    System.out.format("field %s is a HashSet\n", ctField.getName());
                    fieldDetails.put("Notes", fieldDetails.get("Notes") + "|HashSet");
                }

                System.out.println("\n");
            }

            // Methods
            for(CtMethod m: ctClass.getDeclaredMethods()) {
                JSONObject methodDetails = new JSONObject();
                JSONArray localMethodParamList = new JSONArray();
                JSONArray localMethodVarList = new JSONArray();

                try {
                    MethodInfo minfo = m.getMethodInfo();
                    methodDetails.put("Method Name", minfo.getName());
                    methodDetails.put("Return Type", m.getReturnType().getName());
                    methodDetails.put("Signature", m.getSignature());

                    CodeAttribute ca = minfo.getCodeAttribute();
                    LocalVariableAttribute table = (LocalVariableAttribute) ca.getAttribute(LocalVariableAttribute.tag);
                    int pc = 0;
                    int n = table.tableLength();
                    for (int i = 0; i < n; ++i) {
                        JSONObject localMethodParamDetails = new JSONObject();
                        JSONObject localMethodVarDetails = new JSONObject();

                        int start = table.startPc(i);
                        int len = table.codeLength(i);

                        if (start <= pc && pc < start + len) {
                            if (!table.variableName(i).equals("this")) {
                                localMethodParamDetails.put("Name", table.variableName(i));
                                localMethodParamDetails.put("Descriptor", table.descriptor(i));
                                localMethodParamList.add(localMethodParamDetails);
                            }
                        } else {
                            localMethodVarDetails.put("Name", table.variableName(i));
                            localMethodVarDetails.put("Descriptor", table.descriptor(1));
                            localMethodVarList.add(localMethodVarDetails);
                        }
                    }

                    methodDetails.put("Parameters", localMethodParamList);
                    methodDetails.put("Variables", localMethodVarList);
                } catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    methodList.add(methodDetails);
                }
            }

            classDetails.put("Class Name", ctClass.getName());
            classDetails.put("Methods", methodList);
            classDetails.put("Fields", fieldList);

            classList.add(classDetails);
        }
    }

    static int getCapacity(Map<?, ?> l) throws Exception {
        Field dataField = l.getClass().getDeclaredField("size");
        dataField.setAccessible(true);
        return ((Object[]) dataField.get(l)).length;
    }

    private static void writeJson(String fileName) {
        fileName = fileName + ".json";
        try (FileWriter file = new FileWriter(fileName)) {
            file.write(packageList.toJSONString());
            file.flush();
            System.out.println("Successfully written data to " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


/*
            LocalVariableAttribute va = (LocalVariableAttribute) ca.getAttribute(LocalVariableAttribute.tag);
			System.out.println("Descriptor: " + va.descriptor(1));*/

			/*int n = va.tableLength();
			for (int i = 0; i < n; ++i) {
				int start = va.startPc(i);
				int len = va.codeLength(i);
				if (start <= pc && pc < start + len)
					gen.recordVariable(va.descriptor(i), va.variableName(i),
							va.index(i), stable);
			}*/

			/*for (CodeIterator ci = ca.iterator(); ci.hasNext();) {
				int index = ci.next();
				int op = ci.byteAt(index);
				//System.out.println(Mnemonic.OPCODE[op]);
				if (op == Opcode.GETFIELD) {
					int a1 = ci.s16bitAt(index + 1);
					String fieldName = " " + cf.getConstPool().getFieldrefName(a1);
					System.out.println("Field Name: " + fieldName + "\n");
				}
			}


    private void dumpMyDataClass() throws IOException, BadBytecode, Exception {
		ClassFile cf = new ClassFile(new DataInputStream(getClass().getResourceAsStream("Main.class")));

		// Dump fields:
		for (Object fieldInfoObj : cf.getFields()) {
			FieldInfo fieldInfo = (FieldInfo) fieldInfoObj;
			System.out.printf("Field %s; %s%n", fieldInfo.getName(), fieldInfo.getDescriptor());
		}

		MethodInfo minfo = cf.getMethod("add");
		CodeAttribute ca = minfo.getCodeAttribute();
		for (CodeIterator ci = ca.iterator(); ci.hasNext();) {
			int address = ci.next();
			int op = ci.byteAt(address);

			String params = "";
			switch (op) {
				case Opcode.INVOKEINTERFACE:
					int a1 = ci.s16bitAt(address + 1);
					params += " " + cf.getConstPool().getInterfaceMethodrefName(a1);
					System.out.println("a1 = " + a1);
					break;
			}

			System.out.printf("Line %4d. Address %7d: %s%s%n", minfo.getLineNumber(address), address, Mnemonic.OPCODE[op], params);
		}

		// Command line tool of javassist:
		//String pathToClass = System.getProperty("user.dir") + "/target/classes/jeggen/test2/Main.class";
		//Dump.main(new String[] { pathToClass });
	}
*/

