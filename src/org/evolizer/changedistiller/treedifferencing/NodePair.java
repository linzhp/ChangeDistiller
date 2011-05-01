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
package org.evolizer.changedistiller.treedifferencing;

/**
 * A pair of nodes.
 * 
 * @author fluri
 * 
 */
public class NodePair {

    private Node fLeft;
    private Node fRight;

    /**
     * Creates a new node pair
     * 
     * @param left
     *            node of the pair
     * @param right
     *            node of the pair
     */
    public NodePair(Node left, Node right) {
        fLeft = left;
        fRight = right;
    }

    /**
     * Returns the left node of this pair.
     * 
     * @return the left node of this pair
     */
    public Node getLeft() {
        return fLeft;
    }

    /**
     * Returns the right node of this pair.
     * 
     * @return the right node of this pair
     */
    public Node getRight() {
        return fRight;
    }

}
