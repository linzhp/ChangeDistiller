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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.DocumentRangeNode;
import org.eclipse.jface.text.BadLocationException;

/**
 * Provides a method to extract refactorings from a list of added and a list of deleted entities.
 * 
 * @author fluri
 * @see AbstractRefactoringHelper
 */
public final class RefactoringExtractor {

    private RefactoringExtractor() {}

    /**
     * Extracts all refactorings that result from add and delete operations of entities.
     * 
     * <p>
     * For instance, if a method was deleted from a class body and a new one was inserted, this method checks whether
     * the two operations reflect a refactoring.
     * 
     * @param addedEntities
     *            list of added entities
     * @param deletedEntities
     *            list of deleted entities
     * @param refactoringHelper
     *            that knows how to deal with a possible refactoring. It corresponds to type of added/deleted entities
     * @return list of refactoring pairs extracted from the added/deleted entity lists
     */
    public static List<RefactoringPair> extractRefactorings(
            List<RefactoringCandidate> addedEntities,
            List<RefactoringCandidate> deletedEntities,
            AbstractRefactoringHelper refactoringHelper) {
        List<RefactoringPair> refactorings = new ArrayList<RefactoringPair>();
        List<RefactoringPair> refactoringCandidates = new ArrayList<RefactoringPair>();
        for (RefactoringCandidate rightCandidate : addedEntities) {
            DiffNode rightDiffNode = rightCandidate.getDiffNode();
            String rightName = rightDiffNode.getName();
            DocumentRangeNode rightDrn = (DocumentRangeNode) rightDiffNode.getRight();

            for (RefactoringCandidate leftCandidate : deletedEntities) {
                DiffNode leftDiffNode = leftCandidate.getDiffNode();
                String leftName = leftDiffNode.getName();
                DocumentRangeNode leftDrn = (DocumentRangeNode) leftDiffNode.getLeft();
                // fix for Bug 68
                if (rightDrn.getTypeCode() == leftDrn.getTypeCode()) {
                    if (refactoringHelper.isRefactoring(
                            leftName,
                            rightName,
                            getDocString(leftDrn),
                            getDocString(rightDrn))) {
                        double similarity =
                                refactoringHelper.similarity(
                                        leftName,
                                        rightName,
                                        getDocString(leftDrn),
                                        getDocString(rightDrn));
                        refactoringCandidates.add(new RefactoringPair(leftCandidate, rightCandidate, similarity));
                    }
                }
            }
        }

        Collections.sort(refactoringCandidates);

        for (RefactoringPair pair : refactoringCandidates) {
            RefactoringCandidate left = pair.getDeletedEntity();
            RefactoringCandidate right = pair.getInsertedEntity();
            if (!(left.isRefactoring() || right.isRefactoring())) {
                refactorings.add(pair);
                left.enableRefactoring();
                right.enableRefactoring();
            }
        }

        return refactorings;
    }

    private static String getDocString(DocumentRangeNode drn) {
        try {
            return drn.getDocument().get(drn.getRange().getOffset(), drn.getRange().getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        return null;
    }
}
