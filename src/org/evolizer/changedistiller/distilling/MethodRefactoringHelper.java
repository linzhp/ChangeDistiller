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

import org.evolizer.changedistiller.model.classifiers.EntityType;
import org.evolizer.changedistiller.model.entities.ClassHistory;
import org.evolizer.changedistiller.model.entities.StructureEntityVersion;
import org.evolizer.changedistiller.treedifferencing.matching.measure.NGramsCalculator;

/**
 * Helps finding refactorings of methods.
 * 
 * @author fluri
 * @see AbstractRefactoringHelper
 */
public class MethodRefactoringHelper extends AbstractRefactoringHelper {

    /**
     * Creates a new refactoring helper.
     * 
     * @param classHistory
     *            on which the helper creates new {@link StructureEntityVersion}s
     */
    public MethodRefactoringHelper(ClassHistory classHistory) {
        super(classHistory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StructureEntityVersion createStructureEntityVersion(String name, int modifiers) {
        return getClassHistory().createMethod(name, modifiers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StructureEntityVersion createStructureEntityVersion(String oldEntityName, String newEntityName, int modifiers) {
        StructureEntityVersion method = createStructureEntityVersion(oldEntityName, modifiers);
        if (!oldEntityName.equals(newEntityName)) {
            method.setUniqueName(newEntityName);
            getClassHistory().overrideMethodHistory(oldEntityName, newEntityName);
        }
        return method;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String extractShortName(String fullName) {
        int pos = fullName.indexOf('(');
        if (pos > 0) {
            return fullName.substring(0, pos);
        }
        return fullName.substring(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityType getType() {
        return EntityType.METHOD_DECLARATION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double similarity(
            String oldEntityName,
            String newEntityName,
            String oldEntityRepresentation,
            String newEntityRepresentation) {
        NGramsCalculator lm = new NGramsCalculator(2);
        return lm.calculateSimilarity(oldEntityName, newEntityName);
    }

}
