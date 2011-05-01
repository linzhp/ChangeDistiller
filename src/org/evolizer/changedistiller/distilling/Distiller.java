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

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.compare.structuremergeviewer.DiffContainer;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.DocumentRangeNode;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IFile;
import org.evolizer.changedistiller.ChangeDistillerPlugin;
import org.evolizer.changedistiller.distilling.changeclassifying.ChangeClassifier;
import org.evolizer.changedistiller.jdt.AbstractASTHelper;
import org.evolizer.changedistiller.jdt.JavaASTHelper;
import org.evolizer.changedistiller.model.classifiers.ChangeType;
import org.evolizer.changedistiller.model.classifiers.EntityType;
import org.evolizer.changedistiller.model.entities.ClassHistory;
import org.evolizer.changedistiller.model.entities.Delete;
import org.evolizer.changedistiller.model.entities.Insert;
import org.evolizer.changedistiller.model.entities.Move;
import org.evolizer.changedistiller.model.entities.SourceCodeChange;
import org.evolizer.changedistiller.model.entities.SourceCodeEntity;
import org.evolizer.changedistiller.model.entities.StructureEntityVersion;
import org.evolizer.changedistiller.model.entities.Update;
import org.evolizer.changedistiller.treedifferencing.ITreeEditOperation;
import org.evolizer.changedistiller.treedifferencing.Node;
import org.evolizer.changedistiller.treedifferencing.TreeDifferencer;
import org.evolizer.changedistiller.treedifferencing.operation.DeleteOperation;
import org.evolizer.changedistiller.treedifferencing.operation.InsertOperation;
import org.evolizer.changedistiller.treedifferencing.operation.MoveOperation;
import org.evolizer.changedistiller.treedifferencing.operation.UpdateOperation;

/**
 * Core functionality of ChangeDistiller. It distills {@link SourceCodeChange}s between two {@link IFile}s and
 * classifies them into {@link ChangeType}s. It also generates a {@link ClassHistory} for the provided class in the
 * file(s).
 * 
 * @author fluri
 * 
 */
public class Distiller {

    private static final String DOT = ".";

    private final double fAttributeRefactoringThreshold = 0.65;
    private final double fInnerClassRefactoringThreshold = 0.65;
    private final double fMethodRefactoringThreshold = 0.6;

    private AbstractASTHelper fLeftASTHelper;
    private AbstractASTHelper fRightASTHelper;

    private TreeDifferencer fASTDifferencer;
    private List<SourceCodeChange> fChanges;

    private ClassHistory fClassHistory;
    private boolean fIsRootClass = true;

    /**
     * Returns the generated or updated {@link ClassHistory} for the files to extract the {@link SourceCodeChange}s
     * from.
     * 
     * @return the generated or updated class history
     */
    public ClassHistory getClassHistory() {
        return fClassHistory;
    }

    /**
     * Returns all classified {@link SourceCodeChange} that were extracted by distiller from the two files.
     * 
     * @return all classified source code changes that were extracted by distiller
     */
    public List<SourceCodeChange> getSourceCodeChanges() {
        return fChanges;
    }

    /**
     * Performs a change distilling pass between the two given files.
     * 
     * @param leftFile
     *            to distill from
     * @param rightFile
     *            to distill from
     */
    public void performDistilling(IFile leftFile, IFile rightFile) {
        DiffNode diff = StructureDiffUtils.compare(leftFile, rightFile);
        if (diff != null) {
            fLeftASTHelper = new JavaASTHelper(leftFile);
            fRightASTHelper = new JavaASTHelper(rightFile);

            fASTDifferencer = new TreeDifferencer();
            fChanges = new LinkedList<SourceCodeChange>();

            // find class node in difference tree
            DiffNode classNode = StructureDiffUtils.findClass(diff.getChildren());
            fIsRootClass = true;

            if ((classNode != null)
            // hack. special case second class inside file deleted argouml
                    // org.argouml.kernel.Project.java 1.94->1.95
                    && (classNode.getKind() != Differencer.ADDITION) && (classNode.getKind() != Differencer.DELETION)) {
                processClassContainer(classNode, fLeftASTHelper.getTopLevelName());
            }
        }
    }

    /**
     * Sets the {@link ClassHistory} to operate on.
     * 
     * @param classHistory
     *            the class history to set
     */
    public void setClassHistory(ClassHistory classHistory) {
        fClassHistory = classHistory;
    }

    private void addSourceCodeChanges(
            String rootName,
            StructureEntityVersion rootEntity,
            SourceCodeEntity parentEntity,
            DiffNode diffNode,
            RefactoringContainer refactoringContainer) {
        if (!StructureDiffUtils.isUsable(diffNode)) {
            return;
        }
        if (StructureDiffUtils.isInsert(diffNode)) {
            Insert ins =
                    new Insert(rootEntity, fRightASTHelper.createSourceCodeEntity(
                            rootName + DOT + diffNode.getName(),
                            StructureDiffUtils.convert((DocumentRangeNode) diffNode.getRight())), parentEntity);

            if (StructureDiffUtils.isMethodOrConstructor(diffNode)) {
                refactoringContainer.fAddedMethods.add(new RefactoringCandidate(ins, diffNode));
            } else if (StructureDiffUtils.isAttribute(diffNode)) {
                refactoringContainer.fAddedAttributes.add(new RefactoringCandidate(ins, diffNode));
            } else if (StructureDiffUtils.isClassOrInterface(diffNode)) {
                refactoringContainer.fAddedInnerClasses.add(new RefactoringCandidate(ins, diffNode));
            }
        } else if (StructureDiffUtils.isDeletion(diffNode)) {
            if (diffNode.getLeft() instanceof DocumentRangeNode) {
                Delete del =
                        new Delete(rootEntity, fLeftASTHelper.createSourceCodeEntity(rootName + DOT
                                + diffNode.getName(), StructureDiffUtils
                                .convert((DocumentRangeNode) diffNode.getLeft())), parentEntity);

                if (StructureDiffUtils.isMethodOrConstructor(diffNode)) {
                    refactoringContainer.fDeletedMethods.add(new RefactoringCandidate(del, diffNode));
                } else if (StructureDiffUtils.isAttribute(diffNode)) {
                    refactoringContainer.fDeletedAttributes.add(new RefactoringCandidate(del, diffNode));
                } else if (StructureDiffUtils.isClassOrInterface(diffNode)) {
                    refactoringContainer.fDeletedInnerClasses.add(new RefactoringCandidate(del, diffNode));
                }
            }
        } else if (StructureDiffUtils.isChange(diffNode)) {
            String entityName = rootName + DOT + diffNode.getName();
            if (StructureDiffUtils.isUsable(diffNode)) {
                List<SourceCodeChange> newChanges = new LinkedList<SourceCodeChange>();

                StructureEntityVersion sev = null;
                int modifiers =
                        fRightASTHelper.extractModifiers(StructureDiffUtils.convert((DocumentRangeNode) diffNode
                                .getId()));
                if (StructureDiffUtils.isMethodOrConstructor(diffNode)) {
                    sev = fClassHistory.createMethod(entityName, modifiers);
                } else if (StructureDiffUtils.isClassOrInterface(diffNode)) {
                    sev = fClassHistory.getClass(entityName, modifiers);
                } else if (StructureDiffUtils.isAttribute(diffNode)) {
                    sev = fClassHistory.createAttribute(entityName, modifiers);
                }

                extractBodyChanges(diffNode, sev, newChanges);
                extractDeclarationChanges(diffNode, sev, newChanges);

                // only save bcos or dcos if they are not empty
                if (newChanges.isEmpty()) {
                    if (StructureDiffUtils.isMethodOrConstructor(diffNode)) {
                        fClassHistory.deleteMethod(sev);
                    } else if (StructureDiffUtils.isAttribute(diffNode)) {
                        fClassHistory.deleteAttribute(sev);
                    }
                } else {
                    List<SourceCodeChange> classifiedChanges = ChangeClassifier.classifyOperations(newChanges);
                    sev.addAllSourceCodeChanges(classifiedChanges);
                    fChanges.addAll(classifiedChanges);
                }
            }
        }
    }

    private void checkRefactorings(
            StructureEntityVersion clazz,
            List<RefactoringCandidate> added,
            List<RefactoringCandidate> deleted,
            AbstractRefactoringHelper refactoringHelper) {
        processRefactorings(refactoringHelper, clazz.getUniqueName(), added, deleted);
        processRemainingDiffs(clazz, added, refactoringHelper, fRightASTHelper);
        processRemainingDiffs(clazz, deleted, refactoringHelper, fLeftASTHelper);
    }

    private Delete createDeleteOperation(StructureEntityVersion structureEntity, DeleteOperation delete) {
        if (fLeftASTHelper.isASTNode(delete.getNodeToDelete())) {
            SourceCodeEntity parent = delete.getParentNode().getEntity();
            return new Delete(structureEntity, delete.getNodeToDelete().getEntity(), parent);
        }
        return null;
    }

    private Insert createInsertOperation(StructureEntityVersion structureEntity, InsertOperation insert) {
        if (fLeftASTHelper.isASTNode(insert.getNodeToInsert())) {
            SourceCodeEntity parent = insert.getParentNode().getEntity();

            return new Insert(structureEntity, insert.getNodeToInsert().getEntity(), parent);
        }
        return null;
    }

    private Move createMoveOperation(StructureEntityVersion structureEntity, MoveOperation move) {
        if (fLeftASTHelper.isASTNode(move.getNodeToMove())) {
            return new Move(structureEntity, move.getNodeToMove().getEntity(), move.getNewNode().getEntity(), move
                    .getOldParent().getEntity(), move.getNewParent().getEntity());
        }
        return null;
    }

    private Update createUpdateOperation(StructureEntityVersion structureEntity, UpdateOperation update) {
        if (fLeftASTHelper.isASTNode(update.getNodeToUpdate())) {
            SourceCodeEntity entity =
                    new SourceCodeEntity(update.getOldValue(), update.getNodeToUpdate().getEntity().getType(), update
                            .getNodeToUpdate().getEntity().getModifiers(), update.getNodeToUpdate().getEntity()
                            .getSourceRange());
            return new Update(structureEntity, entity, update.getNewNode().getEntity(), ((Node) update
                    .getNodeToUpdate().getParent()).getEntity());
        }
        return null;
    }

    private void extractBodyChanges(
            DiffNode diffNode,
            StructureEntityVersion structureEntity,
            List<SourceCodeChange> changes) {
        if (StructureDiffUtils.isMethodOrConstructor(diffNode)) {
            Node leftRoot =
                    fLeftASTHelper.createBodyTree(structureEntity.getUniqueName(), StructureDiffUtils
                            .convert((DocumentRangeNode) diffNode.getLeft()));
            Node rightRoot =
                    fRightASTHelper.createBodyTree(structureEntity.getUniqueName(), StructureDiffUtils
                            .convert((DocumentRangeNode) diffNode.getRight()));
            extractFineGrainedChanges(structureEntity, changes, leftRoot, rightRoot);
        }
    }

    private void extractDeclarationChanges(
            DiffNode diffNode,
            StructureEntityVersion structureEntity,
            List<SourceCodeChange> changes) {
        if (StructureDiffUtils.isDeclaration(diffNode)) {
            Node leftRoot =
                    fLeftASTHelper.createDeclarationTree(structureEntity.getUniqueName(), StructureDiffUtils
                            .convert((DocumentRangeNode) diffNode.getLeft()));
            Node rightRoot =
                    fRightASTHelper.createDeclarationTree(structureEntity.getUniqueName(), StructureDiffUtils
                            .convert((DocumentRangeNode) diffNode.getRight()));
            extractFineGrainedChanges(structureEntity, changes, leftRoot, rightRoot);
        }
    }

    private void extractFineGrainedChanges(
            StructureEntityVersion structureEntity,
            List<SourceCodeChange> changes,
            Node leftRoot,
            Node rightRoot) {
        if ((leftRoot != null) && (rightRoot != null)) {
            fASTDifferencer.calculateEditScript(leftRoot, rightRoot);
            List<ITreeEditOperation> ops = fASTDifferencer.getEditScript();
            extractSourceCodeChanges(structureEntity, changes, ops);
        }
    }

    private void extractSourceCodeChanges(
            StructureEntityVersion structureEntity,
            List<SourceCodeChange> changes,
            List<ITreeEditOperation> ops) {
        for (ITreeEditOperation op : ops) {
            SourceCodeChange co = null;
            if (op.getOperationType() == ITreeEditOperation.INSERT) {
                InsertOperation ins = (InsertOperation) op;
                co = createInsertOperation(structureEntity, ins);
            } else if (op.getOperationType() == ITreeEditOperation.DELETE) {
                DeleteOperation del = (DeleteOperation) op;
                co = createDeleteOperation(structureEntity, del);
            } else if (op.getOperationType() == ITreeEditOperation.MOVE) {
                MoveOperation mov = (MoveOperation) op;
                co = createMoveOperation(structureEntity, mov);
            } else if (op.getOperationType() == ITreeEditOperation.UPDATE) {
                UpdateOperation upd = (UpdateOperation) op;
                co = createUpdateOperation(structureEntity, upd);
            }
            if (co != null) {
                changes.add(co);
            }
        }
    }

    private void processClassContainer(DiffNode classNode, String entityName) {
        String className = (entityName.equals("") ? "" : entityName + DOT) + classNode.getName();

        // entity for the class to proceed
        SourceCodeEntity structureEntity =
                fLeftASTHelper.createSourceCodeEntity(className, StructureDiffUtils
                        .convert((DocumentRangeNode) classNode.getLeft()));

        int modifiers = -1;
        if (structureEntity == null) {
            structureEntity =
                    fLeftASTHelper.createSourceCodeEntity(className, StructureDiffUtils
                            .convert((DocumentRangeNode) classNode.getId()));
            modifiers =
                    fLeftASTHelper.extractModifiers(StructureDiffUtils.convert((DocumentRangeNode) classNode.getId()));
        } else {
            modifiers =
                    fRightASTHelper.extractModifiers(StructureDiffUtils.convert((DocumentRangeNode) classNode.getId()));
        }

        // prepare class history
        if (fClassHistory == null) {
            fClassHistory = new ClassHistory(className, modifiers);
        }
        ClassHistory tmp = null;
        tmp = fClassHistory;
        StructureEntityVersion clazz;
        if (!fIsRootClass) {
            clazz = new StructureEntityVersion(EntityType.CLASS, className, modifiers);
            fClassHistory = tmp.createInnerClassHistory(clazz);
        } else {
            fIsRootClass = false;
            clazz = fClassHistory.getClass(className, modifiers);
        }

        // keep track of added/deleted methods/attributes to detect refactorings
        RefactoringContainer refactoringContainer = new RefactoringContainer();

        // changes for the class declaration
        addSourceCodeChanges(entityName, clazz, structureEntity, classNode, refactoringContainer);

        IDiffElement[] elements = classNode.getChildren();
        for (IDiffElement element : elements) {
            if (element instanceof DiffContainer) {
                DiffContainer container = (DiffContainer) element;

                if (container instanceof DiffNode) {
                    DiffNode dn = (DiffNode) container;
                    if (dn.getId() instanceof DocumentRangeNode) {
                        DocumentRangeNode drn = (DocumentRangeNode) dn.getId();

                        if (StructureDiffUtils.isAttribute(drn) || StructureDiffUtils.isMethodOrConstructor(drn)) {
                            addSourceCodeChanges(className, clazz, structureEntity, dn, refactoringContainer);
                        }

                        if (StructureDiffUtils.isClassOrInterface(drn)) {
                            if (StructureDiffUtils.isInsert(dn) || StructureDiffUtils.isDeletion(dn)) {
                                addSourceCodeChanges(className, clazz, structureEntity, dn, refactoringContainer);
                            } else {
                                processClassContainer(dn, className);
                            }
                        }
                    }
                }
            }
        }
        AbstractRefactoringHelper helper = new MethodRefactoringHelper(fClassHistory);
        helper.setThreshold(fMethodRefactoringThreshold);
        checkRefactorings(clazz, refactoringContainer.fAddedMethods, refactoringContainer.fDeletedMethods, helper);
        helper = new FieldRefactoringHelper(fClassHistory);
        helper.setThreshold(fAttributeRefactoringThreshold);
        checkRefactorings(clazz, refactoringContainer.fAddedAttributes, refactoringContainer.fDeletedAttributes, helper);
        helper = new ClassRefactoringHelper(fClassHistory);
        helper.setThreshold(fInnerClassRefactoringThreshold);
        checkRefactorings(
                clazz,
                refactoringContainer.fAddedInnerClasses,
                refactoringContainer.fDeletedInnerClasses,
                helper);
        for (Iterator<ClassHistory> it = fClassHistory.getInnerClassHistories().values().iterator(); it.hasNext();) {
            ClassHistory ch = it.next();
            if (!ch.hasChanges()) {
                it.remove();
            }
        }
        fClassHistory = tmp;
    }

    private void processRefactorings(
            AbstractRefactoringHelper refactoringHelper,
            String className,
            List<RefactoringCandidate> added,
            List<RefactoringCandidate> deleted) {
        List<RefactoringPair> refactorings =
                RefactoringExtractor.extractRefactorings(added, deleted, refactoringHelper);
        for (RefactoringPair pair : refactorings) {
            DiffNode leftDiffNode = pair.getDeletedEntity().getDiffNode();
            DiffNode rightDiffNode = pair.getInsertedEntity().getDiffNode();

            DocumentRangeNode leftDrn = (DocumentRangeNode) leftDiffNode.getLeft();
            DocumentRangeNode rightDrn = (DocumentRangeNode) rightDiffNode.getRight();

            String newQualifiedName = className + DOT + rightDiffNode.getName();
            String nameL = refactoringHelper.extractShortName(leftDiffNode.getName());
            String nameR = refactoringHelper.extractShortName(rightDiffNode.getName());

            List<SourceCodeChange> newChanges = new LinkedList<SourceCodeChange>();

            int modifiers = fRightASTHelper.extractModifiers(StructureDiffUtils.convert(rightDrn));
            StructureEntityVersion structureEntityVersion =
                    refactoringHelper.createStructureEntityVersion(newQualifiedName, modifiers);
            if (!nameL.equals(nameR)) {
                structureEntityVersion =
                        refactoringHelper.createStructureEntityVersion(
                                className + DOT + leftDiffNode.getName(),
                                newQualifiedName,
                                modifiers);
                Update upd =
                        new Update(structureEntityVersion, fLeftASTHelper.createDeclarationRootSourceCodeEntity(nameL
                                .trim(), StructureDiffUtils.convert(leftDrn)), fRightASTHelper
                                .createDeclarationRootSourceCodeEntity(nameR.trim(), StructureDiffUtils
                                        .convert(rightDrn)), fLeftASTHelper.createSourceCodeEntity(
                                className,
                                StructureDiffUtils.convert((DocumentRangeNode) ((DiffNode) leftDiffNode.getParent())
                                        .getLeft())));
                newChanges.add(upd);
            }
            Node leftRoot = fLeftASTHelper.createDeclarationTree(newQualifiedName, StructureDiffUtils.convert(leftDrn));
            Node rightRoot =
                    fRightASTHelper.createDeclarationTree(newQualifiedName, StructureDiffUtils.convert(rightDrn));

            extractFineGrainedChanges(structureEntityVersion, newChanges, leftRoot, rightRoot);

            leftRoot = fLeftASTHelper.createBodyTree(newQualifiedName, StructureDiffUtils.convert(leftDrn));
            rightRoot = fRightASTHelper.createBodyTree(newQualifiedName, StructureDiffUtils.convert(rightDrn));
            extractFineGrainedChanges(structureEntityVersion, newChanges, leftRoot, rightRoot);
            List<SourceCodeChange> classifiedChanges = ChangeClassifier.classifyOperations(newChanges);
            fChanges.addAll(classifiedChanges);
            structureEntityVersion.addAllSourceCodeChanges(classifiedChanges);
        }
    }

    private void processRemainingDiffs(
            StructureEntityVersion clazz,
            List<RefactoringCandidate> candidates,
            AbstractRefactoringHelper helper,
            AbstractASTHelper astHelper) {
        for (RefactoringCandidate candidate : candidates) {
            if (!candidate.isRefactoring()) {
                List<SourceCodeChange> classifiedChanges =
                        ChangeClassifier.classifyOperations(Arrays.asList(candidate.getSourceCodeChange()));
                astHelper.extractModifiers(StructureDiffUtils.convert((DocumentRangeNode) candidate.getDiffNode()
                        .getId()));
                clazz.addAllSourceCodeChanges(classifiedChanges);
                fChanges.addAll(classifiedChanges);
            }
        }
    }

    private final class RefactoringContainer {

        private List<RefactoringCandidate> fAddedAttributes = new LinkedList<RefactoringCandidate>();
        private List<RefactoringCandidate> fDeletedAttributes = new LinkedList<RefactoringCandidate>();
        private List<RefactoringCandidate> fAddedInnerClasses = new LinkedList<RefactoringCandidate>();
        private List<RefactoringCandidate> fDeletedInnerClasses = new LinkedList<RefactoringCandidate>();
        private List<RefactoringCandidate> fAddedMethods = new LinkedList<RefactoringCandidate>();
        private List<RefactoringCandidate> fDeletedMethods = new LinkedList<RefactoringCandidate>();

        private RefactoringContainer() {}
    }
}
