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
package org.evolizer.changedistiller.jdt;

import org.eclipse.core.resources.IFile;
import org.evolizer.changedistiller.distilling.Distiller;
import org.evolizer.changedistiller.model.classifiers.EntityType;
import org.evolizer.changedistiller.model.classifiers.SourceRange;
import org.evolizer.changedistiller.model.entities.SourceCodeEntity;
import org.evolizer.changedistiller.treedifferencing.Node;

/**
 * AST helper should extend this class to allow {@link Distiller} having access to AST information.
 * 
 * <p>
 * AST helper act on a {@link IFile} in which the original source code resides. The {@link SourceRange} parameters in
 * the methods that are defined in this interface are relative to the provided {@link IFile}.
 * 
 * @author fluri
 */
public abstract class AbstractASTHelper {

    /**
     * Creates a new AST helper.
     * 
     * @param file
     *            on which the AST helper acts on
     */
    public AbstractASTHelper(IFile file) {}

    /**
     * Converts given node into an {@link EntityType}.
     * 
     * @param node
     *            to convert
     * @return entity type of the node
     */
    public abstract EntityType convertNode(Object node);

    /**
     * Create a root {@link SourceCodeEntity} for the body inside the {@link SourceRange} in the {@link IFile}.
     * 
     * @param name
     *            of the root source code entity
     * @param range
     *            inside the file
     * @return root source code entity for body inside the range in the file
     */
    public abstract SourceCodeEntity createBodyRootSourceCodeEntity(String name, SourceRange range);

    /**
     * Create a generic {@link Node} tree out of the body of the {@link SourceCodeEntity} declared by the range in the
     * {@link IFile} and give it a name.
     * 
     * @param name
     *            of the body tree
     * @param range
     *            inside the file
     * @return body tree inside the range in the file
     */
    public abstract Node createBodyTree(String name, SourceRange range);

    /**
     * Create a root {@link SourceCodeEntity} for the declaration inside the {@link SourceRange} in the {@link IFile}.
     * 
     * @param name
     *            of the root source code entity
     * @param range
     *            inside the file
     * @return root source code entity for declaration inside the position in the file
     */
    public abstract SourceCodeEntity createDeclarationRootSourceCodeEntity(String name, SourceRange range);

    /**
     * Create a generic {@link Node} tree out of the declaration of the {@link SourceCodeEntity} declared by the range
     * in the {@link IFile} and give it a name.
     * 
     * @param nodeName
     *            of the body tree
     * @param range
     *            inside the file
     * @return declaration tree inside the range of the file
     */
    public abstract Node createDeclarationTree(String nodeName, SourceRange range);

    /**
     * Create a {@link SourceCodeEntity} from a {@link SourceRange} in the {@link IFile} and give it the provided name.
     * 
     * @param name
     *            of the created source code entity
     * @param range
     *            inside the file
     * @return source code entity inside the position in the file
     */
    public abstract SourceCodeEntity createSourceCodeEntity(String name, SourceRange range);

    /**
     * Extract modifiers of the {@link SourceCodeEntity} inside the range in the {@link IFile}.
     * 
     * @param range
     *            inside the file
     * @return modifiers of the source code entity inside the range inside the file
     */
    public abstract int extractModifiers(SourceRange range);

    /**
     * The top level name of the source code element in the {@link IFile}. In Java, e.g., this is the package name.
     * 
     * @return top level name of source code element in the file
     */
    public abstract String getTopLevelName();

    /**
     * Is given node a correct AST node.
     * 
     * @param node
     *            to check
     * @return <code>true</code> if node is a correct AST node, <code>false</code> otherwise
     */
    public abstract boolean isASTNode(Node node);
}
