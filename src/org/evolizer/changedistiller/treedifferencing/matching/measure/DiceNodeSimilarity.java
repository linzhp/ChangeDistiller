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
package org.evolizer.changedistiller.treedifferencing.matching.measure;

import java.util.Enumeration;
import java.util.Set;

import org.evolizer.changedistiller.treedifferencing.Node;
import org.evolizer.changedistiller.treedifferencing.NodePair;

/**
 * Implementation of the dice inner node similarity calculator.
 * 
 * @author fluri
 * 
 */
public class DiceNodeSimilarity implements INodeSimilarityCalculator {

    private Set<? extends NodePair> fLeafMatchSet;
    private IStringSimilarityCalculator fStringSimilarity;
    private double fStringThreshold;

    /**
     * Creates a new dice similarity calculator.
     * 
     * @param stringSimilarity
     *            similarity calculator for strings
     * @param stringSimilarityThreshold
     *            to verify when two strings are similar
     */
    public DiceNodeSimilarity(IStringSimilarityCalculator stringSimilarity, double stringSimilarityThreshold) {
        fStringSimilarity = stringSimilarity;
        fStringThreshold = stringSimilarityThreshold;
    }

    /**
     * {@inheritDoc}
     */
    public void setLeafMatchSet(Set<? extends NodePair> leafMatchSet) {
        fLeafMatchSet = leafMatchSet;
    }

    /**
     * {@inheritDoc}
     */
    public double calculateSimilarity(Node left, Node right) {
        int intersection = 0;
        for (NodePair p : fLeafMatchSet) {
            Node l = p.getLeft();
            Node r = p.getRight();
            if (left.isNodeDescendant(l) && right.isNodeDescendant(r)) {
                intersection++;
            }
        }
        if ((left.getLabel() == right.getLabel())
                && (fStringSimilarity.calculateSimilarity(left.getValue(), right.getValue()) >= fStringThreshold)) {
            intersection++;
        }
        int union = countNodes(left) + countNodes(right);
        return (double) 2 * intersection / union;
    }

    @SuppressWarnings("unchecked")
    private int countNodes(Node node) {
        if (node.isLeaf()) {
            return 1;
        } else {
            int count = 1;
            for (Enumeration e = node.children(); e.hasMoreElements();) {
                count += countNodes((Node) e.nextElement());
            }
            return count;
        }
    }

}
