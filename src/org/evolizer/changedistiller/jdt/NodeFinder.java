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
// package org.eclipse.jdt.astview;
package org.evolizer.changedistiller.jdt;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * For a give range finds the node covered and the node covering. ATTENTION this class is copied from
 * org.eclipse.jdt.astview!!
 * 
 * @author fluri
 * 
 */
public class NodeFinder extends GenericVisitor {

    private int fStart;

    private int fEnd;
    private ASTNode fCoveringNode;
    private ASTNode fCoveredNode;

    /**
     * Creates a new node finder.
     * 
     * @param offset
     *            to the start the search
     * @param length
     *            within the the search should take place
     */
    public NodeFinder(int offset, int length) {
        super(true); // include Javadoc tags
        fStart = offset;
        fEnd = offset + length;
    }

    /**
     * A visitor that maps a selection to a given ASTNode. The result node is determined as follows:
     * <ul>
     * <li>first the visitor tries to find a node with the exact start and length</li>
     * <li>if no such node exists than the node that encloses the range defined by start and end is returned.</li>
     * <li>if the length is zero than also nodes are considered where the node's start or end position matches
     * <code>start</code>.</li>
     * <li>otherwise <code>null</code> is returned.</li>
     * </ul>
     * 
     * @param root
     *            the root to perform the search on
     * @param start
     *            the start position to start the search
     * @param length
     *            the length in which the node should be found
     * @return the found AST node
     */
    public static ASTNode perform(ASTNode root, int start, int length) {
        NodeFinder finder = new NodeFinder(start, length);
        root.accept(finder);
        ASTNode result = finder.getCoveredNode();
        return result;
    }

    /**
     * Returns the covered node. If more than one nodes are covered by the selection, the returned node is first covered
     * node found in a top-down traversal of the AST
     * 
     * @return ASTNode
     */
    @SuppressWarnings("unqualified-field-access")
    public ASTNode getCoveredNode() {
        return fCoveredNode;
    }

    /**
     * Returns the covering node. If more than one nodes are covering the selection, the returned node is last covering
     * node found in a top-down traversal of the AST
     * 
     * @return ASTNode
     */
    @SuppressWarnings("unqualified-field-access")
    public ASTNode getCoveringNode() {
        return fCoveringNode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean visitNode(ASTNode node) {
        int nodeStart = node.getStartPosition();
        int nodeEnd = nodeStart + node.getLength();
        if ((nodeEnd < fStart) || (fEnd < nodeStart)) {
            return false;
        }
        if ((nodeStart <= fStart) && (fEnd <= nodeEnd)) {
            fCoveringNode = node;
        }
        if ((fStart <= nodeStart) && (nodeEnd <= fEnd)) {
            if (fCoveringNode == node) { // nodeStart == fStart && nodeEnd == fEnd
                fCoveredNode = node;
                return true; // look further for node with same length as parent
            } else if (fCoveredNode == null) { // no better found
                fCoveredNode = node;
            }
            return false;
        }
        return true;
    }
}
