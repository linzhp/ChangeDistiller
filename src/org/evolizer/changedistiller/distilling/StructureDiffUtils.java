/*
 * Copyright 2009 University of Zurich, Switzerland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.evolizer.changedistiller.distilling;

import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.internal.CompareUIPlugin;
import org.eclipse.compare.internal.StructureCreatorDescriptor;
import org.eclipse.compare.structuremergeviewer.DiffContainer;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.DocumentRangeNode;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.compare.structuremergeviewer.IStructureCreator;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.internal.ui.compare.JavaStructureCreator;
import org.evolizer.changedistiller.model.classifiers.SourceRange;

/**
 * Convenience methods to deal with eclipse compare data.
 * 
 * @author fluri
 * @see DiffContainer
 * @see Differencer
 * @see DiffNode
 * @see DocumentRangeNode
 * @see IDiffElement
 */
@SuppressWarnings("restriction")
public final class StructureDiffUtils {

    private static final int BUFFER_LENGTH = 1024;

    // from JavaNode; unfortunately JavaNode is not public
    private static final int INTERFACE = 4;
    private static final int CLASS = 5;
    private static final int FIELD = 8;
    private static final int CONSTRUCTOR = 10;
    private static final int METHOD = 11;

    private StructureDiffUtils() {}

    /**
     * Compares two {@link IFile}s with each other by using the Eclipse compare facilities and returns the root
     * {@link DiffNode} of the differences.
     * 
     * @param left
     *            file to compare
     * @param right
     *            file to compare
     * @return the root diff node of the differences
     */
    public static DiffNode compare(IFile left, IFile right) {
        IStructureCreator creator = new JavaStructureCreator();
        if (creator != null) {
            return compare(creator.getStructure(new ResourceNode(left)), creator.getStructure(new ResourceNode(right)));
        }
        return null;
    }

    /**
     * Returns a {@link SourceRange} that is built out of the range information of the given {@link DocumentRangeNode}.
     * 
     * @param documentRangeNode
     *            to convert the range from
     * @return the source range built from the document range node
     */
    public static SourceRange convert(DocumentRangeNode documentRangeNode) {
        if (documentRangeNode == null) {
            return null;
        }
        return new SourceRange(documentRangeNode.getRange().getOffset(), documentRangeNode.getRange().getLength());
    }

    /**
     * Returns the next class or interface {@link DiffNode} within the given hierarchical {@link IDiffElement}
     * structure.
     * 
     * @param elements
     *            to search for a class or interface node
     * @return the first class or interface diff node with the elements structure
     */
    public static DiffNode findClass(IDiffElement[] elements) {
        for (IDiffElement element : elements) {
            DiffContainer container = (DiffContainer) element;
            if (container instanceof DiffNode) {
                DiffNode dn = (DiffNode) container;
                if (dn.getId() instanceof DocumentRangeNode) {
                    DocumentRangeNode drn = (DocumentRangeNode) dn.getId();
                    if (StructureDiffUtils.isClassOrInterface(drn)) {
                        return dn;
                    } else if (container.hasChildren()) {
                        dn = findClass(container.getChildren());
                        if (dn != null) {
                            return dn;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns whether the entity in the given {@link DiffNode} is an attribute.
     * 
     * @param diffNode
     *            that represents the operation
     * @return <code>true</code> if the entity in the diff node is an attribute, <code>false</code> otherwise.
     */
    public static boolean isAttribute(DiffNode diffNode) {
        return ((DocumentRangeNode) diffNode.getId()).getTypeCode() == FIELD;
    }

    /**
     * Returns whether the entity in the given {@link DocumentRangeNode} is an attribute.
     * 
     * @param documentRangeNode
     *            that represents a range in a document
     * @return <code>true</code> if the entity in the document range node is an attribute, <code>false</code> otherwise.
     */
    public static boolean isAttribute(DocumentRangeNode documentRangeNode) {
        return documentRangeNode.getTypeCode() == FIELD;
    }

    /**
     * Returns whether the given {@link DiffNode} represents neither an insert nor a delete operation.
     * 
     * @param diffNode
     *            that represents the operation
     * @return <code>true</code> if diff node represents an neither an insert nor a delete operation, <code>false</code>
     *         otherwise.
     */
    public static boolean isChange(DiffNode diffNode) {
        return diffNode.getKind() == Differencer.CHANGE;
    }

    /**
     * Returns whether the entity in the given {@link DiffNode} is a class or an interface.
     * 
     * @param diffNode
     *            that represents the operation
     * @return <code>true</code> if the entity in the diff node is a class or an interface, <code>false</code>
     *         otherwise.
     */
    public static boolean isClassOrInterface(DiffNode diffNode) {
        DocumentRangeNode drn = (DocumentRangeNode) diffNode.getId();
        return (drn.getTypeCode() == StructureDiffUtils.CLASS) || (drn.getTypeCode() == StructureDiffUtils.INTERFACE);
    }

    /**
     * Returns whether the entity in the given {@link DocumentRangeNode} is a class or an interface.
     * 
     * @param documentRangeNode
     *            that represents a range in a document
     * @return <code>true</code> if the entity in the document range node is a class or an interface, <code>false</code>
     *         otherwise.
     */
    public static boolean isClassOrInterface(DocumentRangeNode documentRangeNode) {
        return (documentRangeNode.getTypeCode() == CLASS) || (documentRangeNode.getTypeCode() == INTERFACE);
    }

    /**
     * Returns whether the entity in the given {@link DiffNode} is a declaration (attribute, class, constructor, method,
     * or interface).
     * 
     * @param diffNode
     *            that represents the operation
     * @return <code>true</code> if the entity in the diff node is declaration, <code>false</code> otherwise.
     */
    public static boolean isDeclaration(DiffNode diffNode) {
        return isClassOrInterface(diffNode) || isMethodOrConstructor(diffNode) || isAttribute(diffNode);
    }

    /**
     * Returns whether the given {@link DiffNode} represents an delete operation.
     * 
     * @param diffNode
     *            that represents the operation
     * @return <code>true</code> if diff node represents an delete operation, <code>false</code> otherwise.
     */
    public static boolean isDeletion(DiffNode diffNode) {
        return diffNode.getKind() == Differencer.DELETION;
    }

    /**
     * Returns whether the given {@link DiffNode} represents an insert operation.
     * 
     * @param diffNode
     *            that represents the operation
     * @return <code>true</code> if diff node represents an insert operation, <code>false</code> otherwise.
     */
    public static boolean isInsert(DiffNode diffNode) {
        return diffNode.getKind() == Differencer.ADDITION;
    }

    /**
     * Returns whether the entity in the given {@link DiffNode} is a constructor or a method.
     * 
     * @param diffNode
     *            that represents the operation
     * @return <code>true</code> if the entity in the diff node is a constructor or a method, <code>false</code>
     *         otherwise.
     */
    public static boolean isMethodOrConstructor(DiffNode diffNode) {
        DocumentRangeNode drn = (DocumentRangeNode) diffNode.getId();
        return (drn.getTypeCode() == StructureDiffUtils.METHOD)
                || (drn.getTypeCode() == StructureDiffUtils.CONSTRUCTOR);
    }

    /**
     * Returns whether the entity in the given {@link DocumentRangeNode} is a constructor or a method.
     * 
     * @param documentRangeNode
     *            that represents a range in a document
     * @return <code>true</code> if the entity in the document range node is a constructor or a method,
     *         <code>false</code> otherwise.
     */
    public static boolean isMethodOrConstructor(DocumentRangeNode documentRangeNode) {
        return (documentRangeNode.getTypeCode() == METHOD) || (documentRangeNode.getTypeCode() == CONSTRUCTOR);
    }

    /**
     * Returns whether the given {@link DiffNode} is usable for further differencing.
     * 
     * @param diffNode
     *            to check for further differencing
     * @return <code>true</code> if the given diff node is usable for further differencing, <code>false</code> otherwise
     */
    public static boolean isUsable(DiffNode diffNode) {
        if ((diffNode.getLeft() == null) && (diffNode.getRight() instanceof DocumentRangeNode)) {
            return true;
        } else if ((diffNode.getLeft() instanceof DocumentRangeNode) && (diffNode.getRight() == null)) {
            return true;
        } else if ((diffNode.getLeft() instanceof DocumentRangeNode)
                && (diffNode.getRight() instanceof DocumentRangeNode)) {
            return true;
        }
        return false;
    }

    /**
     * Returns the content of the given {@link IFile} in a string representation.
     * 
     * @param file
     *            to read in
     * @return the content of the file in a string representation
     */
    public static String readFile(IFile file) {
        char[] b = new char[BUFFER_LENGTH];
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(file.getContents());
        } catch (CoreException e) {
            e.printStackTrace();
        }
        StringBuffer sb = new StringBuffer();
        int n;
        try {
            while ((n = isr.read(b)) > 0) {
                sb.append(b, 0, n);
            }
            isr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private static DiffNode compare(IStructureComparator left, IStructureComparator right) {
        Differencer differencer = new Differencer();

        Object structureDiffs =
                differencer.findDifferences(
                        false /*three way*/,
                        null /*progress monitor*/,
                        null /*data object*/,
                        null /*ancestor*/,
                        left,
                        right);
        if ((structureDiffs != null) && (structureDiffs instanceof DiffNode)) {
            return (DiffNode) structureDiffs;
        }
        return null;
    }

    private static IStructureCreator getStructureCreator(String type) {
        if (type == null) {
            return null;
        }
        String subType = type;
        if (type.startsWith(".")) {
            subType = type.substring(1);
        }
        StructureCreatorDescriptor scd = CompareUIPlugin.getDefault().getStructureCreator(subType);
        if (scd == null) {
            return null;
        }
        return scd.createStructureCreator();
    }
}
