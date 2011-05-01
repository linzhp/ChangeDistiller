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
 * Helps finding refactorings of fields.
 * 
 * @author fluri
 * @see AbstractRefactoringHelper
 */
public class FieldRefactoringHelper extends AbstractRefactoringHelper {

    /**
     * Creates a new refactoring helper.
     * 
     * @param classHistory
     *            on which the helper creates new {@link StructureEntityVersion}s
     */
    public FieldRefactoringHelper(ClassHistory classHistory) {
        super(classHistory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StructureEntityVersion createStructureEntityVersion(String name, int modifiers) {
        return getClassHistory().createAttribute(name, modifiers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StructureEntityVersion createStructureEntityVersion(String oldEntityName, String newEntityName, int modifiers) {
        StructureEntityVersion attribute = createStructureEntityVersion(oldEntityName, modifiers);
        if (!oldEntityName.equals(newEntityName)) {
            attribute.setUniqueName(newEntityName);
            getClassHistory().overrideAttributeHistory(oldEntityName, newEntityName);
        }
        return attribute;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String extractShortName(String uniqueName) {
        int pos = uniqueName.indexOf(':');
        if (pos > 0) {
            return uniqueName.substring(0, pos);
        }
        return uniqueName.substring(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityType getType() {
        return EntityType.FIELD_DECLARATION;
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
        if (!oldEntityName.equals(newEntityName)) {
            Levenshtein<String> lm =
                    new Levenshtein<String>(new StringAccessor(oldEntityRepresentation), new StringAccessor(
                            newEntityRepresentation));
            return lm.getSimilarity();
        } else {
            return 1.0;
        }
    }
}
