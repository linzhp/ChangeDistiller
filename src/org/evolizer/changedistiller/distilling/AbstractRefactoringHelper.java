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

/**
 * Refactoring helpers support {@link Distiller} in deciding whether an added and a deleted class/field/method represent
 * a refactoring. The helpers for the different structure entities have to extend this class to be conform to the
 * {@link Distiller}.
 * 
 * <p>
 * Since the {@link Distiller} assumes that the refactoring helper updates the {@link ClassHistory} in which the
 * refactorings took place, heirs of this class have to fulfill that all {@link StructureEntityVersion} generations are
 * made via the provided {@link ClassHistory}.
 * 
 * @author fluri
 * @see ClassRefactoringHelper
 * @see FieldRefactoringHelper
 * @see MethodRefactoringHelper
 */
public abstract class AbstractRefactoringHelper {

    private ClassHistory fClassHistory;
    private double fThreshold = 1.0;

    /**
     * Creates a new refactoring helper.
     * 
     * @param classHistory
     *            on which the helper creates new {@link StructureEntityVersion}s
     */
    public AbstractRefactoringHelper(ClassHistory classHistory) {
        fClassHistory = classHistory;
    }

    /**
     * Creates a {@link StructureEntityVersion} with the given name and attaches it to the {@link ClassHistory}.
     * 
     * @param name
     *            of the structure entity version
     * @param modifiers
     *            of the structure entity version
     * @return structure entity version with the given name and modifiers
     */
    public abstract StructureEntityVersion createStructureEntityVersion(String name, int modifiers);

    /**
     * Creates a {@link StructureEntityVersion} with newEntityName and replaces the {@link StructureEntityVersion}
     * having oldEntityName (if exists) with it in the {@link ClassHistory}. took place.
     * 
     * @param oldEntityName
     *            of the structure entity version to replace
     * @param newEntityName
     *            of the new structure entity version
     * @param modifiers
     *            of the new structure entity version
     * @return structure entity with newEntityName and the modifiers that is a replacement of the structure entity
     *         version with oldEntityName in the class history
     */
    public abstract StructureEntityVersion createStructureEntityVersion(
            String oldEntityName,
            String newEntityName,
            int modifiers);

    /**
     * Extracts a short form of the unique name provided.
     * 
     * @param uniqueName
     *            to shorten
     * @return short form of unique name
     */
    public abstract String extractShortName(String uniqueName);

    /**
     * Returns the {@link EntityType} on which the refactoring helper implementation acts.
     * 
     * @return entity type the helper acts on
     */
    public abstract EntityType getType();

    /**
     * Checks whether an old and a new entity are subject of a refactoring. Whether it is a refactoring or not is
     * decided according to the similarity implementation and the given threshold.
     * 
     * @param oldEntityName
     *            of the old entity
     * @param newEntityName
     *            of the new entity
     * @param oldEntityRepresentation
     *            of the old entity
     * @param newEntityRepresentation
     *            of the new entity
     * @return <code>true</code> if the two entities are subject of a refactoring, <code>false</code> otherwise
     */
    public final boolean isRefactoring(
            String oldEntityName,
            String newEntityName,
            String oldEntityRepresentation,
            String newEntityRepresentation) {
        return similarity(oldEntityName, newEntityName, oldEntityRepresentation, newEntityRepresentation) >= getThreshold();
    }

    /**
     * Sets the threshold for the similarity calculation.
     * 
     * @param threshold
     *            for the similarity calculation
     */
    public void setThreshold(double threshold) {
        fThreshold = threshold;
    }

    /**
     * Calculates the similarity between two entities representated by their names and the string representation of
     * them.
     * 
     * @param oldEntityName
     *            of the old entity
     * @param newEntityName
     *            of the new entity
     * @param oldEntityRepresentation
     *            of the old entity
     * @param newEntityRepresentation
     *            of the new entity
     * @return similarity value the two entities; <code>1.0</code> is the highest, <code>0.0</code> the lowest
     *         similarity value
     */
    public abstract double similarity(
            String oldEntityName,
            String newEntityName,
            String oldEntityRepresentation,
            String newEntityRepresentation);

    /**
     * Returns the {@link ClassHistory} in which the refactorings took place.
     * 
     * @return class history in which the refactorings took place
     */
    protected ClassHistory getClassHistory() {
        return fClassHistory;
    }

    /**
     * Returns the threshold for the similarity calculation.
     * 
     * @return threshold for the similarity calculation
     */
    protected double getThreshold() {
        return fThreshold;
    }
}
