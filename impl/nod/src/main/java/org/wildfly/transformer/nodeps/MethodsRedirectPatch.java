/*
 * Copyright 2020 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.transformer.nodeps;

import static org.wildfly.transformer.nodeps.ClassFileUtils.*;
import static org.wildfly.transformer.nodeps.ConstantPoolTags.*;
import static org.wildfly.transformer.nodeps.MethodRedirection.MAPPING;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class MethodsRedirectPatch {

    final int diffInBytes;
    final int currentPoolSize;
    final byte[] poolEndPatch;
    final MethodsPatch methodsPatch;
    final int[][] methodRefRedirects;
    final UtilityClasses utilClasses;

    MethodsRedirectPatch(final int currentPoolSize, final byte[] poolEndPatch, final int[][] methodRefRedirects,
                         final MethodsPatch methodsPatch, final UtilityClasses utilClasses) {
        this.currentPoolSize = currentPoolSize;
        this.poolEndPatch = poolEndPatch;
        this.methodRefRedirects = methodRefRedirects;
        this.methodsPatch = methodsPatch;
        diffInBytes = poolEndPatch.length + (methodsPatch != null ? methodsPatch.diffInBytes : 0);
        this.utilClasses = utilClasses;
    }

    static class UtilityClasses {
        final Utf8InfoMapping utilClassesRefactoring;
        final int[] generatedClassPoolIndices;

        private UtilityClasses(final Utf8InfoMapping utilClassesRefactoring, final int[] generatedClassPoolIndices) {
            this.utilClassesRefactoring = utilClassesRefactoring;
            this.generatedClassPoolIndices = generatedClassPoolIndices;
        }
    }

    private static UtilityClasses generateUtilityClassNames(final byte[] clazz, final ClassFileRefs cfRefs, final int[] matches, final int matchesCount) {
        // Temporary mapping tables (will contain white spaces) - they will be used later for creation of final mapping tables
        final byte[][] tempFrom = new byte[matches.length][];
        final byte[][] tempTo = new byte[matches.length][];
        // For every 'method call match' defined in MethodRedirection we will generate indices of
        // generated utility class info structures in constant pool to forward calls to
        final int[] generatedClassPoolIndices = new int[matches.length];
        byte[] oldUtilityClassName, newUtilityClassName;
        int countOfGeneratedClasses = 0;
        int classPoolIndex = cfRefs.getConstantPool().getSize() + matchesCount * 4;
        boolean utilClassExists;

        for (int i = 0; i < matches.length; i++) {
            if (matches[i] != 0) {
                // found method call we are interested in
                oldUtilityClassName = MAPPING[i][1].className;
                newUtilityClassName = renameUtilityClassName(clazz, cfRefs, oldUtilityClassName);

                for (int j = 0; j < tempTo.length; j++) {
                    // searching whether generated class name already exists in registry of all generated classes
                    if (tempTo[j] != null) {
                        if (tempTo[j].length == newUtilityClassName.length) {
                            utilClassExists = true;
                            for (int k = 0; k < tempTo[j].length; k++) {
                                if (tempTo[j][k] != newUtilityClassName[k]) {
                                    // utility class names differ, we didn't find the match
                                    utilClassExists = false;
                                    break;
                                }
                            }
                            if (utilClassExists) {
                                generatedClassPoolIndices[i] = generatedClassPoolIndices[j];
                                break;
                            }
                        }
                    } else {
                        // all previous utility class names didn't match - adding
                        tempFrom[countOfGeneratedClasses] = oldUtilityClassName;
                        tempTo[countOfGeneratedClasses] = newUtilityClassName;
                        // add class info redirection mapping
                        generatedClassPoolIndices[i] = countOfGeneratedClasses * 2 + classPoolIndex;
                        countOfGeneratedClasses++;
                        break;
                    }
                }
            }
        }
        // defines mapping of 'template utility classes' defined in MethodRedirection to 'real package private renamed utility classes'
        final Utf8InfoMapping utilClassesRefactoring = getMappingForUtilityClassesRefactoring(countOfGeneratedClasses + 1, tempFrom, tempTo);
        return new UtilityClasses(utilClassesRefactoring, generatedClassPoolIndices);
    }

    private static int getLastPathSeparatorIndex(final byte[] buffer) {
        return getLastPathSeparatorIndex(buffer, 0, buffer.length);
    }

    private static int getLastPathSeparatorIndex(final byte[] buffer, final int off, final int len) {
        for (int i = off + len - 1; i >= off; i--) {
            if (buffer[i] == '/') return i;
        }
        return -1;
    }

    static MethodsRedirectPatch of(final byte[] clazz, final ClassFileRefs cfRefs) {
        final int[] matches = getMatches(cfRefs.getConstantPool());
        if (matches == null) return null; // no method matches
        // count method matches
        int matchesCount = 0;
        for (int i = 0; i < matches.length; i++) if (matches[i] != 0) matchesCount++;
        // generate transformation utility full class names
        final UtilityClasses utilClasses = generateUtilityClassNames(clazz, cfRefs, matches, matchesCount);
        // count patch size
        final int patchSize = getPoolEndPatchSize(matches, matchesCount, utilClasses);
        final byte[] poolEndPatch = new byte[patchSize];
        final int[][] methodRefRedirects = new int[MAPPING.length][2];
        int currentPoolSize = cfRefs.getConstantPool().getSize();
        int position = 0;
        // generating new MethodRef constant pool items redirecting to transform utility classes
        for (int i = 0; i < matches.length; i++) {
            if (matches[i] == 0) continue; // method was not found
            // methodRef redirect bijection
            methodRefRedirects[i][0] = matches[i]; // previous methodRef
            methodRefRedirects[i][1] = currentPoolSize; // new methodRef
            // add new methodRef into constant pool
            currentPoolSize++;
            poolEndPatch[position++] = METHOD_REF;
            writeUnsignedShort(poolEndPatch, position, utilClasses.generatedClassPoolIndices[i]);
            position += 2;
            writeUnsignedShort(poolEndPatch, position, currentPoolSize);
            position += 2;
            // add new method name and type into constant pool
            currentPoolSize++;
            poolEndPatch[position++] = NAME_AND_TYPE;
            writeUnsignedShort(poolEndPatch, position, currentPoolSize);
            position += 2;
            writeUnsignedShort(poolEndPatch, position, currentPoolSize + 1);
            position += 2;
            // add utility class method name into constant pool
            currentPoolSize++;
            poolEndPatch[position++] = UTF8;
            writeUnsignedShort(poolEndPatch, position, MAPPING[i][1].methodName.length);
            position += 2;
            System.arraycopy(MAPPING[i][1].methodName, 0, poolEndPatch, position, MAPPING[i][1].methodName.length);
            position += MAPPING[i][1].methodName.length;
            // add utility class method descriptor into constant pool
            currentPoolSize++;
            poolEndPatch[position++] = UTF8;
            writeUnsignedShort(poolEndPatch, position, MAPPING[i][1].methodDescriptor.length);
            position += 2;
            System.arraycopy(MAPPING[i][1].methodDescriptor, 0, poolEndPatch, position, MAPPING[i][1].methodDescriptor.length);
            position += MAPPING[i][1].methodDescriptor.length;
        }
        for (int i = 1; i < utilClasses.utilClassesRefactoring.to.length; i++) {
            // add utility class info into constant pool
            currentPoolSize++;
            poolEndPatch[position++] = CLASS;
            writeUnsignedShort(poolEndPatch, position, currentPoolSize);
            position += 2;
            // add utility class utf8 into constant pool
            currentPoolSize++;
            poolEndPatch[position++] = UTF8;
            writeUnsignedShort(poolEndPatch, position, utilClasses.utilClassesRefactoring.to[i].length);
            position += 2;
            System.arraycopy(utilClasses.utilClassesRefactoring.to[i], 0, poolEndPatch, position, utilClasses.utilClassesRefactoring.to[i].length);
            position += utilClasses.utilClassesRefactoring.to[i].length;
        }
        final MethodsPatch methodsPatch = MethodsPatch.getPatchForMethodRedirects(clazz, cfRefs, methodRefRedirects);
        return new MethodsRedirectPatch(currentPoolSize, poolEndPatch, methodRefRedirects, methodsPatch, utilClasses);
    }
    /**
     * Lookups methods in class constant pool that are defined in <code>MethodRedirection.MAPPING</code>.
     * Return value is bijection of the following form:
     * retVal == null -> indicates no matches were found
     * <b>retVal[i] == index</b> into class constant pool if method ref was found matching MethodRedirection.MAPPING[i][0]
     * <b>retVal[i] == 0</b> -> indicates no match was found for method specified at MethodRedirection.MAPPING[i][0]
     */
    public static int[] getMatches(final ConstantPoolRefs cpRefs) {
        int[] retVal = null;
        int classIndex;
        int classNameIndex;
        int methodNameAndTypeIndex;
        int methodNameIndex;
        int methodDescriptorIndex;

        for (int j = 0; j < MAPPING.length; j++) {
            for (int i = 1; i < cpRefs.getSize(); i++) {
                if (cpRefs.isMethodRef(i)) {
                    // method reference found
                    classIndex = cpRefs.getMethodRef_ClassIndex(i);
                    classNameIndex = cpRefs.getClass_NameIndex(classIndex);
                    if (cpRefs.utf8EqualsTo(classNameIndex, MAPPING[j][0].className)) {
                        // class we are searching for
                        methodNameAndTypeIndex = cpRefs.getMethodRef_NameAndTypeIndex(i);
                        methodNameIndex = cpRefs.getNameAndType_NameIndex(methodNameAndTypeIndex);
                        if (cpRefs.utf8EqualsTo(methodNameIndex, MAPPING[j][0].methodName)) {
                            // method name we are searching for in this class
                            methodDescriptorIndex = cpRefs.getNameAndType_DescriptorIndex(methodNameAndTypeIndex);
                            if (cpRefs.utf8EqualsTo(methodDescriptorIndex, MAPPING[j][0].methodDescriptor)) {
                                // exact className, methodName & methodDescriptor match
                                if (retVal == null) {
                                    retVal = new int[MAPPING.length];
                                }
                                retVal[j] = i;
                            }
                        }
                    }
                }
            }
        }

        return retVal;
    }

    private static int getPoolEndPatchSize(final int[] matches, final int matchesCount, final UtilityClasses utilClasses) {
        int patchSize = 3 * (utilClasses.utilClassesRefactoring.to.length - 1); // new utility class infos
        for (int i = 1; i < utilClasses.utilClassesRefactoring.to.length; i++) {
            patchSize += 3 + utilClasses.utilClassesRefactoring.to[i].length; // generated class name strings
        }
        patchSize += 5 * matchesCount; // new method refs
        patchSize += 5 * matchesCount; // new name_and_types
        for (int i = 0; i < matches.length; i++) if (matches[i] != 0) {
            patchSize += MAPPING[i][1].methodName.length + 3; // new methodName strings
            patchSize += MAPPING[i][1].methodDescriptor.length + 3; // new methodDescriptor strings
        }
        return patchSize;
    }

    private static byte[] renameUtilityClassName(final byte[] clazz, final ClassFileRefs cfRefs, final byte[] oldUtilityClassName) {
        // first - detect package name length of current class being processed (includes all path separators)
        final ConstantPoolRefs cpRefs = cfRefs.getConstantPool();
        final int classNameLength = cpRefs.getUtf8_Length(cpRefs.getClass_NameIndex(cfRefs.getThisClassIndex()));
        final int classNameBytesRef = cpRefs.getUtf8_BytesRef(cpRefs.getClass_NameIndex(cfRefs.getThisClassIndex()));
        final int packageLastPS = getLastPathSeparatorIndex(clazz, classNameBytesRef, classNameLength);
        final int packageLength = (packageLastPS == -1 ? 0 : packageLastPS - classNameBytesRef);
        // second - detect future (after rename) utility full class name length
        final int oldUtilityClassNameLastPS = getLastPathSeparatorIndex(oldUtilityClassName);
        final int oldUtilityClassSimpleNameLength = oldUtilityClassNameLastPS == -1 ? oldUtilityClassName.length : oldUtilityClassName.length - oldUtilityClassNameLastPS - 1;
        final byte[] newUtilityClassName = new byte[packageLength + oldUtilityClassSimpleNameLength];
        // third - copy package name & utility simple class name
        if (packageLength > 0) {
            System.arraycopy(clazz, classNameBytesRef, newUtilityClassName, 0, packageLength);
        }
        System.arraycopy(oldUtilityClassName, oldUtilityClassNameLastPS + 1, newUtilityClassName, packageLength, oldUtilityClassSimpleNameLength);
        // finally - return renamed utility class name (its package have been renamed to package of current class being processed)
        return newUtilityClassName;
    }

    private static Utf8InfoMapping getMappingForUtilityClassesRefactoring(final int size, final byte[][] tempFrom, final byte[][] tempTo) {
        final byte[][] from = new byte[size][];
        final byte[][] to = new byte[size][];
        int min = Integer.MAX_VALUE;

        for (int i = 0; i < tempTo.length; i++) {
            if (tempTo[i] != null) {
                from[i + 1] = tempFrom[i];
                to[i + 1] = tempTo[i];
                if (min > from[i + 1].length) {
                    min = from[i + 1].length;
                }
            }
        }

        return new Utf8InfoMapping(from, to, min);
    }
}
