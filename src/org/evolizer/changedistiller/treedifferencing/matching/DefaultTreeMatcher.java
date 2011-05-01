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
package org.evolizer.changedistiller.treedifferencing.matching;

import java.util.Enumeration;
import java.util.Set;

import org.evolizer.changedistiller.model.classifiers.EntityType;
import org.evolizer.changedistiller.treedifferencing.ITreeMatcher;
import org.evolizer.changedistiller.treedifferencing.Node;
import org.evolizer.changedistiller.treedifferencing.NodePair;
import org.evolizer.changedistiller.treedifferencing.matching.measure.INodeSimilarityCalculator;
import org.evolizer.changedistiller.treedifferencing.matching.measure.IStringSimilarityCalculator;

/**
 * Implementation of the default Chawathe tree matcher.
 * 
 * @author fluri
 * 
 */
public class DefaultTreeMatcher implements ITreeMatcher {

    private IStringSimilarityCalculator fLeafStringSimilarityCalculator;
    private double fLeafStringSimilarityThreshold;

    private INodeSimilarityCalculator fNodeSimilarityCalculator;
    private double fNodeSimilarityThreshold;
    private IStringSimilarityCalculator fNodeStringSimilarityCalculator;
    private double fNodeStringSimilarityThreshold;

    private boolean fDynamicEnabled;
    private int fDynamicDepth;
    private double fDynamicThreshold;

    private Set<NodePair> fMatch;

    /**
     * {@inheritDoc}
     */
    public void init(
            IStringSimilarityCalculator leafStringSimCalc,
            double leafStringSimThreshold,
            INodeSimilarityCalculator nodeSimCalc,
            double nodeSimThreshold) {
        fLeafStringSimilarityCalculator = leafStringSimCalc;
        fLeafStringSimilarityThreshold = leafStringSimThreshold;
        fNodeStringSimilarityCalculator = leafStringSimCalc;
        fNodeStringSimilarityThreshold = leafStringSimThreshold;
        fNodeSimilarityCalculator = nodeSimCalc;
        fNodeSimilarityThreshold = nodeSimThreshold;
    }

    /**
     * {@inheritDoc}
     */
    public void init(
            IStringSimilarityCalculator leafStringSimCalc,
            double leafStringSimThreshold,
            IStringSimilarityCalculator nodeStringSimCalc,
            double nodeStringSimThreshold,
            INodeSimilarityCalculator nodeSimCalc,
            double nodeSimThreshold) {
        init(leafStringSimCalc, leafStringSimThreshold, nodeSimCalc, nodeSimThreshold);
        fNodeStringSimilarityCalculator = nodeStringSimCalc;
        fNodeStringSimilarityThreshold = nodeStringSimThreshold;
    }

    /**
     * {@inheritDoc}
     */
    public void enableDynamicThreshold(int depth, double threshold) {
        fDynamicDepth = depth;
        fDynamicThreshold = threshold;
        fDynamicEnabled = true;
    }

    /**
     * {@inheritDoc}
     */
    public void disableDynamicThreshold() {
        fDynamicEnabled = false;
    }

    /**
     * {@inheritDoc}
     */
    public void setMatchingSet(Set<NodePair> matchingSet) {
        fMatch = matchingSet;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public void match(Node left, Node right) {
        // 1. M <- {} : in init

        // 2. Mark all nodes of T1 and T2 "unmatched" this is done during build of the trees

        // 3. Proceed bottom-up on tree T1
        for (Enumeration enumerate = left.postorderEnumeration(); enumerate.hasMoreElements();) {
            Node x = (Node) enumerate.nextElement();
            // For each unmatched node x in T1
            if (!x.isMatched()) {
                for (Enumeration e = right.postorderEnumeration(); e.hasMoreElements() && !x.isMatched();) {
                    Node y = (Node) e.nextElement();
                    // if there is an unmatched node y in T2
                    if (!x.isMatched() && !y.isMatched()) {
                        if (equal(x, y)) {
                            // i. Add (x, y) to M
                            fMatch.add(new NodePair(x, y));

                            // ii. Mark x and y "matched"
                            x.enableMatched();
                            y.enableMatched();
                        }
                    }
                }
            }
        }
    }

    private boolean equal(Node x, Node y) {
        // leaves
        if (x.isLeaf() && y.isLeaf()) {
            if (x.getLabel() == y.getLabel()) {
                return fLeafStringSimilarityCalculator.calculateSimilarity(x.getValue(), y.getValue()) >= fLeafStringSimilarityThreshold;
            }

            // inner nodes
        } else if ((!x.isLeaf() && !y.isLeaf()) || (x.isRoot() && y.isRoot())) {
            if (x.getLabel() == y.getLabel()) {
                // little heuristic: root nodes must not be compared by INodeSimilarityCalculator
                if (x.getLabel() == EntityType.ROOT_NODE) {
                    return x.getValue().equals(x.getValue());
                } else {
                    double t = fNodeSimilarityThreshold;
                    if (fDynamicEnabled && (x.getLeafCount() < fDynamicDepth) && (y.getLeafCount() < fDynamicDepth)) {
                        t = fDynamicThreshold;
                    }
                    double simNode = fNodeSimilarityCalculator.calculateSimilarity(x, y);
                    double simString = fNodeStringSimilarityCalculator.calculateSimilarity(x.getValue(), y.getValue());
                    return (simNode >= t) && (simString >= fNodeStringSimilarityThreshold);
                }
            }
        }
        return false;
    }
}
