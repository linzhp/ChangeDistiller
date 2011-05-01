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

import simpack.accessor.string.StringAccessor;
import simpack.measure.sequence.Levenshtein;

/**
 * Helps finding refactorings of classes.
 * 
 * @author fluri
 * @see AbstractRefactoringHelper
 */
public class ClassRefactoringHelper extends AbstractRefactoringHelper {

    /**
     * Creates a new refactoring helper.
     * 
     * @param classHistory
     *            on which the helper creates new {@link StructureEntityVersion}s
     */
    public ClassRefactoringHelper(ClassHistory classHistory) {
        super(classHistory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StructureEntityVersion createStructureEntityVersion(String name, int modifiers) {
        return getClassHistory().createInnerClass(name, modifiers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StructureEntityVersion createStructureEntityVersion(String oldEntityName, String newEntityName, int modifiers) {
        StructureEntityVersion clazz = createStructureEntityVersion(oldEntityName, modifiers);
        if (!oldEntityName.equals(newEntityName)) {
            clazz.setUniqueName(newEntityName);
            getClassHistory().overrideClassHistory(oldEntityName, newEntityName);
        }
        return clazz;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String extractShortName(String fullName) {
        return fullName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityType getType() {
        return EntityType.TYPE_DECLARATION;
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
        Levenshtein<String> lm =
                new Levenshtein<String>(new StringAccessor(oldEntityName), new StringAccessor(newEntityName));
        return lm.getSimilarity();
    }
}
