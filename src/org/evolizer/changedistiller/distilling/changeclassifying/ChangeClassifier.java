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
package org.evolizer.changedistiller.distilling.changeclassifying;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.evolizer.changedistiller.model.classifiers.ChangeType;
import org.evolizer.changedistiller.model.classifiers.EntityType;
import org.evolizer.changedistiller.model.entities.Delete;
import org.evolizer.changedistiller.model.entities.Insert;
import org.evolizer.changedistiller.model.entities.Move;
import org.evolizer.changedistiller.model.entities.SourceCodeChange;
import org.evolizer.changedistiller.model.entities.SourceCodeEntity;
import org.evolizer.changedistiller.model.entities.StructureEntityVersion;
import org.evolizer.changedistiller.model.entities.Update;

/**
 * Classifies {@link SourceCodeChange}s into {@link ChangeType}s
 * 
 * @author fluri
 * 
 */
public final class ChangeClassifier {

    private static final String COLON = ":";
    private static final String FINAL = "final";
    private static final String PRIVATE = "private";
    private static final String PROTECTED = "protected";
    private static final String PUBLIC = "public";
    private static final String VOID_RETURN = ": void";

    private static List<Insert> sInserts;
    private static List<Delete> sDeletes;
    private static List<Move> sMoves;
    private static List<Update> sUpdates;

    private static List<SourceCodeChange> sClassifiedChanges;
    private static List<Insert> sInsertsToDelete;

    private ChangeClassifier() {}

    /**
     * Returns the classified {@link SourceCodeChange}s according to the taxonomy of source code changes.
     * 
     * @param sourceCodeChanges
     *            to classifies
     * @return the classified source code changes
     */
    public static List<SourceCodeChange> classifyOperations(List<SourceCodeChange> sourceCodeChanges) {
        splitOperations(sourceCodeChanges);
        sClassifiedChanges = new LinkedList<SourceCodeChange>();
        sInsertsToDelete = new LinkedList<Insert>();
        SourceCodeChange scc = null;
        for (Iterator<Insert> it = sInserts.iterator(); it.hasNext();) {
            Insert ins = it.next();
            if (!sInsertsToDelete.contains(ins)) {
                scc = classify(ins);
                if ((scc != null) && !sClassifiedChanges.contains(scc)) {
                    sClassifiedChanges.add(scc);
                    it.remove();
                }
            }
        }
        for (Insert ins : sInsertsToDelete) {
            sInserts.remove(ins);
        }
        sInsertsToDelete.clear();
        for (Iterator<Delete> it = sDeletes.iterator(); it.hasNext();) {
            Delete del = it.next();
            scc = classify(del);
            if ((scc != null) && !sClassifiedChanges.contains(scc)) {
                sClassifiedChanges.add(scc);
                it.remove();
            }
        }
        for (Iterator<Move> it = sMoves.iterator(); it.hasNext();) {
            Move mov = it.next();
            scc = classify(mov);
            if ((scc != null) && !sClassifiedChanges.contains(scc)) {
                sClassifiedChanges.add(scc);
                it.remove();
            }
        }
        for (Iterator<Update> it = sUpdates.iterator(); it.hasNext();) {
            Update upd = it.next();
            scc = classify(upd);
            if ((scc != null) && !sClassifiedChanges.contains(scc)) {
                sClassifiedChanges.add(scc);
                it.remove();
            }
        }

        return sClassifiedChanges;
    }

    private static SourceCodeChange classify(Insert insert) {
        SourceCodeChange result = null;

        if (insert.getChangeType() != ChangeType.UNCLASSIFIED_CHANGE) {
            return insert;
        }

        // ugly hack ;)
        if (insert.getChangedEntity().getType() == EntityType.THEN_STATEMENT) {
            return null;
        }

        if ((insert.getParentEntity().getType() != null)
                && (insert.getParentEntity().getType() == EntityType.MODIFIERS)) {
            result = extractModifiersChange(insert);

        } else if (insert.getChangedEntity().getType() == EntityType.METHOD) {
            insert.setChangeType(ChangeType.ADDITIONAL_FUNCTIONALITY);
            result = insert;
        } else if (insert.getChangedEntity().getType() == EntityType.ATTRIBUTE) {
            insert.setChangeType(ChangeType.ADDITIONAL_OBJECT_STATE);
            result = insert;
        } else if (insert.getChangedEntity().getType() == EntityType.CLASS) {
            insert.setChangeType(ChangeType.ADDITIONAL_CLASS);
            result = insert;
        } else if (insert.getRootEntity().getType() == EntityType.METHOD) {
            result = handleMethodSignatureChange(insert);
            if (result == null) {
                result = handleNormalInsert(insert);
            }
        } else if (insert.getRootEntity().getType() == EntityType.ATTRIBUTE) {
            result = handleFieldDeclarationChange(insert);
        } else if (insert.getRootEntity().getType() == EntityType.CLASS) {
            result = handleTypeDeclarationChange(insert);
            if (result == null) {
                result = handleInheritanceChange(insert);
            }
        }
        return result;
    }

    private static SourceCodeChange handleInheritanceChange(Insert insert) {
        SourceCodeChange result = null;
        if (EntityType.isType(insert.getChangedEntity().getType())) {
            if (insert.getParentEntity().getType() == EntityType.SUPER_INTERFACE_TYPES) {
                insert.setChangeType(ChangeType.PARENT_INTERFACE_INSERT);
                result = insert;
            } else {
                boolean check = true;
                Delete del = null;
                for (Iterator<Delete> it = sDeletes.iterator(); it.hasNext() && check;) {
                    del = it.next();
                    if ((del.getRootEntity().getType() == EntityType.CLASS)
                            && (del.getParentEntity().getType() != EntityType.SUPER_INTERFACE_TYPES)
                            && EntityType.isType(del.getChangedEntity().getType())) {
                        check = false;
                    }
                }
                if (check) {
                    insert.setChangeType(ChangeType.PARENT_CLASS_INSERT);
                    result = insert;
                } else {
                    result =
                            new Update(
                                    insert.getRootEntity(),
                                    del.getChangedEntity(),
                                    insert.getChangedEntity(),
                                    insert.getParentEntity());
                    result.setChangeType(ChangeType.PARENT_CLASS_CHANGE);
                    sDeletes.remove(del);
                }
            }
        }
        return result;
    }

    private static SourceCodeChange handleFieldDeclarationChange(Insert insert) {
        SourceCodeChange result = null;
        // may lead to incorrect result (never happened so far); better: check for each
        // possible kind of type
        if (EntityType.isType(insert.getChangedEntity().getType())) {
            Delete del =
                    findSpDeleteOperation(
                            insert.getRootEntity().getType(),
                            insert.getRootEntity().getUniqueName(),
                            insert.getParentEntity().getType(),
                            insert.getParentEntity().getUniqueName(),
                            null);
            if (del != null) {
                result =
                        new Update(insert.getRootEntity(), del.getChangedEntity(), insert.getChangedEntity(), insert
                                .getParentEntity());
                sDeletes.remove(del);
                result.setChangeType(ChangeType.ATTRIBUTE_TYPE_CHANGE);
            }
        } else if (insert.getChangedEntity().getType() == EntityType.JAVADOC) {
            Delete del =
                    findDeleteOperation(
                            insert.getRootEntity().getType(),
                            insert.getRootEntity().getUniqueName(),
                            insert.getParentEntity().getType(),
                            insert.getParentEntity().getUniqueName(),
                            insert.getChangedEntity().getType(),
                            null);
            if (del != null) {
                result =
                        new Update(insert.getRootEntity(), del.getChangedEntity(), insert.getChangedEntity(), insert
                                .getParentEntity());
                result.setChangeType(ChangeType.DOC_UPDATE);
                sDeletes.remove(del);
            } else {
                insert.setChangeType(ChangeType.DOC_INSERT);
                result = insert;
            }
        }
        return result;
    }

    private static SourceCodeChange handleFieldDeclarationChange(Delete delete) {
        SourceCodeChange result = null;
        if (delete.getChangedEntity().getType() == EntityType.JAVADOC) {
            delete.setChangeType(ChangeType.DOC_DELETE);
            result = delete;
        }
        return result;
    }

    private static SourceCodeChange handleTypeDeclarationChange(Delete delete) {
        SourceCodeChange result = null;
        if (delete.getChangedEntity().getType() == EntityType.JAVADOC) {
            delete.setChangeType(ChangeType.DOC_DELETE);
            result = delete;
        }
        return result;
    }

    private static SourceCodeChange handleTypeDeclarationChange(Insert insert) {
        SourceCodeChange result = null;
        if (insert.getChangedEntity().getType() == EntityType.JAVADOC) {
            Delete del =
                    findDeleteOperation(
                            insert.getRootEntity().getType(),
                            insert.getRootEntity().getUniqueName(),
                            insert.getParentEntity().getType(),
                            insert.getParentEntity().getUniqueName(),
                            insert.getChangedEntity().getType(),
                            null);
            if (del != null) {
                result =
                        new Update(insert.getRootEntity(), del.getChangedEntity(), insert.getChangedEntity(), insert
                                .getParentEntity());
                result.setChangeType(ChangeType.DOC_UPDATE);
                sDeletes.remove(del);
            } else {
                insert.setChangeType(ChangeType.DOC_INSERT);
                result = insert;
            }
        }
        return result;
    }

    private static SourceCodeChange handleTypeDeclarationChange(Update update) {
        SourceCodeChange result = null;
        if (update.getChangedEntity().getType() == EntityType.JAVADOC) {
            update.setChangeType(ChangeType.DOC_UPDATE);
            result = update;
        }
        return result;
    }

    private static SourceCodeChange handleMethodSignatureChange(Insert insert) {
        SourceCodeChange result = null;

        if (insert.getChangedEntity().getType() == EntityType.JAVADOC) {
            Delete del =
                    findDeleteOperation(
                            insert.getRootEntity().getType(),
                            insert.getRootEntity().getUniqueName(),
                            insert.getParentEntity().getType(),
                            insert.getParentEntity().getUniqueName(),
                            insert.getChangedEntity().getType(),
                            null);
            if (del != null) {
                result =
                        new Update(insert.getRootEntity(), del.getChangedEntity(), insert.getChangedEntity(), insert
                                .getParentEntity());
                result.setChangeType(ChangeType.DOC_UPDATE);
                sDeletes.remove(del);
            } else {
                insert.setChangeType(ChangeType.DOC_INSERT);
                result = insert;
            }
        } else if (insert.getParentEntity().getType() == EntityType.PARAMETERS) {
            result = extractParameterChange(insert);
        } else if (insert.getParentEntity().getType() == EntityType.METHOD_DECLARATION) {
            result = extractReturnChange(insert);
        }
        return result;
    }

    private static SourceCodeChange extractReturnChange(Insert insert) {
        SourceCodeChange result = null;
        // may lead to incorrect result (never happened so far); better: check for each
        // possible kind of type
        if (EntityType.isType(insert.getChangedEntity().getType())) {
            if (insert.getChangedEntity().getUniqueName().endsWith(VOID_RETURN)) {
                Delete del =
                        findSpDeleteOperation(
                                insert.getRootEntity().getType(),
                                insert.getRootEntity().getUniqueName(),
                                insert.getParentEntity().getType(),
                                insert.getParentEntity().getUniqueName(),
                                null);

                del.setChangeType(ChangeType.RETURN_TYPE_DELETE);
                result = del;
            } else {
                Delete del = null;
                boolean check = true;
                // if a non-void type deletion in method declaration occurred
                // => RETURN_TYPE_CHANGE
                for (Iterator<Delete> it = sDeletes.iterator(); it.hasNext() && check;) {
                    del = it.next();
                    if ((insert.getRootEntity().getType() == del.getRootEntity().getType())
                            && insert.getRootEntity().getUniqueName().equals(del.getRootEntity().getUniqueName())
                            && (del.getParentEntity().getType() == EntityType.METHOD_DECLARATION)
                            && del.getParentEntity().getUniqueName().equals(insert.getParentEntity().getUniqueName())
                            && EntityType.isType(del.getChangedEntity().getType())
                            && !del.getChangedEntity().getUniqueName().matches(".*: void")) {
                        check = false;
                    }
                }
                if (!check) {
                    result =
                            new Update(
                                    insert.getRootEntity(),
                                    del.getChangedEntity(),
                                    insert.getChangedEntity(),
                                    insert.getParentEntity());
                    result.setChangeType(ChangeType.RETURN_TYPE_CHANGE);
                    sDeletes.remove(del);
                } else {
                    insert.setChangeType(ChangeType.RETURN_TYPE_INSERT);
                    result = insert;
                }
            }
        }
        return result;
    }

    private static SourceCodeChange extractParameterChange(Insert insert) {
        SourceCodeChange result = null;
        if (insert.getChangedEntity().getType() == EntityType.SINGLE_VARIABLE_DECLARATION) {
            // SingleVariableDeclaration has changed, but the type node (child)
            // remains the same => PARAMETER_RENAMING
            Move mov =
                    findMoveOperation(
                            insert.getRootEntity().getType(),
                            insert.getRootEntity().getUniqueName(),
                            EntityType.SINGLE_VARIABLE_DECLARATION,
                            null,
                            EntityType.SINGLE_VARIABLE_DECLARATION,
                            insert.getChangedEntity().getUniqueName(),
                            null,
                            null);

            Delete del =
                    findDeleteOperation(
                            insert.getRootEntity().getType(),
                            insert.getRootEntity().getUniqueName(),
                            EntityType.PARAMETERS,
                            "",
                            EntityType.SINGLE_VARIABLE_DECLARATION,
                            insert.getChangedEntity().getUniqueName());
            // parameter renaming
            if (mov != null) {
                Delete d =
                        findDeleteOperation(
                                insert.getRootEntity().getType(),
                                insert.getRootEntity().getUniqueName(),
                                EntityType.PARAMETERS,
                                "",
                                EntityType.SINGLE_VARIABLE_DECLARATION,
                                mov.getParentEntity().getUniqueName());
                if (d == null) {
                    insert.setChangeType(ChangeType.PARAMETER_INSERT);
                    result = insert;
                } else {
                    result =
                            new Update(insert.getRootEntity(), insert.getChangedEntity(), d.getChangedEntity(), insert
                                    .getParentEntity());
                    result.setChangeType(ChangeType.PARAMETER_RENAMING);
                    sMoves.remove(mov);
                    sDeletes.remove(d);
                }

                // SingleVariableDeclaration remains the same but the type
                // node (child) are not equal => PARAMETER_TYPE_CHANGE
            } else if (del != null) {
                Delete dell =
                        findDeleteOperation(
                                insert.getRootEntity().getType(),
                                insert.getRootEntity().getUniqueName(),
                                del.getChangedEntity().getType(),
                                del.getChangedEntity().getUniqueName(),
                                null,
                                null);
                if (dell == null) {
                    insert.setChangeType(ChangeType.PARAMETER_INSERT);
                    result = insert;
                } else {

                    // WTF how to remove the insert?

                    Insert i =
                            findInsertOperation(insert.getRootEntity().getType(), insert.getRootEntity()
                                    .getUniqueName(), insert.getChangedEntity().getType(), insert.getChangedEntity()
                                    .getUniqueName(), null, null);
                    if (i == null) {
                        insert.setChangeType(ChangeType.PARAMETER_INSERT);
                        result = insert;
                    } else {
                        result =
                                new Update(
                                        insert.getRootEntity(),
                                        dell.getChangedEntity(),
                                        i.getChangedEntity(),
                                        insert.getChangedEntity());
                        result.setChangeType(ChangeType.PARAMETER_TYPE_CHANGE);
                        sDeletes.remove(del);
                        sDeletes.remove(dell);
                        sInsertsToDelete.add(i);
                    }
                }
            } else {
                insert.setChangeType(ChangeType.PARAMETER_INSERT);
                result = insert;
            }
        }
        return result;
    }

    private static SourceCodeChange extractModifiersChange(Insert insert) {
        SourceCodeChange result = null;

        if (insert.getChangedEntity().getUniqueName().equals(FINAL)) {
            return handleFinalChange(insert);
        } else if (insert.getChangedEntity().getUniqueName().equals(PUBLIC)) {
            result = extractIncreasingAccessibilityChange(insert);
        } else if (insert.getChangedEntity().getUniqueName().equals(PRIVATE)) {
            result = extractDecreasingAccessibilityChange(insert);
        } else if (insert.getChangedEntity().getUniqueName().equals(PROTECTED)) {
            Delete delPublic =
                    findDeleteOperation(
                            insert.getRootEntity().getType(),
                            insert.getRootEntity().getUniqueName(),
                            EntityType.MODIFIERS,
                            "",
                            EntityType.MODIFIER,
                            PUBLIC);
            Delete delPrivate =
                    findDeleteOperation(
                            insert.getRootEntity().getType(),
                            insert.getRootEntity().getUniqueName(),
                            EntityType.MODIFIERS,
                            "",
                            EntityType.MODIFIER,
                            PRIVATE);

            // indeed there are other cases in which protected can be inserted,
            // but these cases are covered with other operations
            if ((delPublic == null) && (delPrivate == null)) {
                insert.setChangeType(ChangeType.INCREASING_ACCESSIBILITY_CHANGE);
                result = insert;
            }
        }
        return result;
    }

    private static SourceCodeChange handleFinalChange(Insert insert) {
        if (insert.getRootEntity().getType() == EntityType.CLASS) {
            insert.setChangeType(ChangeType.REMOVING_CLASS_DERIVABILITY);
        } else if (insert.getRootEntity().getType() == EntityType.METHOD) {
            insert.setChangeType(ChangeType.REMOVING_METHOD_OVERRIDABILITY);
        } else if (insert.getRootEntity().getType() == EntityType.ATTRIBUTE) {
            insert.setChangeType(ChangeType.REMOVING_ATTRIBUTE_MODIFIABILITY);
        } else {
            return null;
        }
        return insert;
    }

    private static SourceCodeChange handleNormalInsert(Insert insert) {
        SourceCodeChange result = null;
        if (insert.getChangedEntity().getType() == EntityType.ELSE_STATEMENT) {
            insert.setChangeType(ChangeType.ALTERNATIVE_PART_INSERT);
            result = insert;
        } else if ((insert.getChangedEntity().getType() == EntityType.BLOCK_COMMENT)
                || (insert.getChangedEntity().getType() == EntityType.LINE_COMMENT)) {
            insert.setChangeType(ChangeType.COMMENT_INSERT);
            result = insert;
        } else if (EntityType.isAtStatementLevel(insert.getChangedEntity().getType())) {
            insert.setChangeType(ChangeType.STATEMENT_INSERT);
            result = insert;
        }
        return result;
    }

    private static SourceCodeChange extractIncreasingAccessibilityChange(Insert insert) {
        insert.setChangeType(ChangeType.INCREASING_ACCESSIBILITY_CHANGE);
        SourceCodeChange result = null;

        Delete delProtected =
                findDeleteOperation(
                        insert.getRootEntity().getType(),
                        insert.getRootEntity().getUniqueName(),
                        EntityType.MODIFIERS,
                        "",
                        EntityType.MODIFIER,
                        PROTECTED);
        Delete delPrivate =
                findDeleteOperation(
                        insert.getRootEntity().getType(),
                        insert.getRootEntity().getUniqueName(),
                        EntityType.MODIFIERS,
                        "",
                        EntityType.MODIFIER,
                        PRIVATE);
        if (delProtected != null) {
            result =
                    new Update(
                            insert.getRootEntity(),
                            delProtected.getChangedEntity(),
                            insert.getChangedEntity(),
                            insert.getParentEntity());
            result.setChangeType(ChangeType.INCREASING_ACCESSIBILITY_CHANGE);
            sDeletes.remove(delProtected);
        } else if (delPrivate != null) {
            result =
                    new Update(insert.getRootEntity(), delPrivate.getChangedEntity(), insert.getChangedEntity(), insert
                            .getParentEntity());
            result.setChangeType(ChangeType.INCREASING_ACCESSIBILITY_CHANGE);
            sDeletes.remove(delPrivate);
        } else {
            result = insert;
        }
        return result;
    }

    private static SourceCodeChange extractDecreasingAccessibilityChange(Insert insert) {
        insert.setChangeType(ChangeType.DECREASING_ACCESSIBILITY_CHANGE);
        SourceCodeChange result = null;

        Delete delProtected =
                findDeleteOperation(
                        insert.getRootEntity().getType(),
                        insert.getRootEntity().getUniqueName(),
                        EntityType.MODIFIERS,
                        "",
                        EntityType.MODIFIER,
                        PROTECTED);
        Delete delPublic =
                findDeleteOperation(
                        insert.getRootEntity().getType(),
                        insert.getRootEntity().getUniqueName(),
                        EntityType.MODIFIERS,
                        "",
                        EntityType.MODIFIER,
                        PUBLIC);
        if (delProtected != null) {
            result =
                    new Update(
                            insert.getRootEntity(),
                            delProtected.getChangedEntity(),
                            insert.getChangedEntity(),
                            insert.getParentEntity());
            result.setChangeType(ChangeType.DECREASING_ACCESSIBILITY_CHANGE);
            sDeletes.remove(delProtected);
        } else if (delPublic != null) {
            result =
                    new Update(insert.getRootEntity(), delPublic.getChangedEntity(), insert.getChangedEntity(), insert
                            .getParentEntity());
            sDeletes.remove(delPublic);
            result.setChangeType(ChangeType.DECREASING_ACCESSIBILITY_CHANGE);
        } else {
            result = insert;
        }
        return result;
    }

    private static SourceCodeChange classify(Delete delete) {
        SourceCodeChange result = null;

        if (delete.getChangeType() != ChangeType.UNCLASSIFIED_CHANGE) {
            return delete;
        }

        // ugly hack ;)
        if (delete.getChangedEntity().getType() == EntityType.THEN_STATEMENT) {
            return null;
        }

        if ((delete.getParentEntity().getType() != null)
                && (delete.getParentEntity().getType() == EntityType.MODIFIERS)) {
            result = extractModifiersChange(delete);

        } else if (delete.getChangedEntity().getType() == EntityType.METHOD) {
            delete.setChangeType(ChangeType.REMOVED_FUNCTIONALITY);
            result = delete;
        } else if (delete.getChangedEntity().getType() == EntityType.ATTRIBUTE) {
            delete.setChangeType(ChangeType.REMOVED_OBJECT_STATE);
            result = delete;

        } else if (delete.getChangedEntity().getType() == EntityType.CLASS) {
            delete.setChangeType(ChangeType.REMOVED_CLASS);
            result = delete;
        } else if (delete.getRootEntity().getType() == EntityType.METHOD) {
            result = handleMethodSignatureChange(delete);
            if (result == null) {
                result = handleNormalDelete(delete);
            }
        } else if (delete.getRootEntity().getType() == EntityType.ATTRIBUTE) {
            result = handleFieldDeclarationChange(delete);
        } else if (delete.getRootEntity().getType() == EntityType.CLASS) {
            result = handleTypeDeclarationChange(delete);
            if (result == null) {
                result = handleInheritanceChange(delete);
            }
        }
        return result;
    }

    private static SourceCodeChange handleInheritanceChange(Delete delete) {
        if (EntityType.isType(delete.getChangedEntity().getType())) {
            if (delete.getParentEntity().getType() == EntityType.SUPER_INTERFACE_TYPES) {
                delete.setChangeType(ChangeType.PARENT_INTERFACE_DELETE);
            } else {
                delete.setChangeType(ChangeType.PARENT_CLASS_DELETE);
            }
        }
        return delete;
    }

    private static SourceCodeChange handleMethodSignatureChange(Delete delete) {
        if (delete.getChangedEntity().getType() == EntityType.JAVADOC) {
            delete.setChangeType(ChangeType.DOC_DELETE);
        } else if (delete.getParentEntity().getType() == EntityType.PARAMETERS) {
            if (delete.getChangedEntity().getType() == EntityType.SINGLE_VARIABLE_DECLARATION) {
                delete.setChangeType(ChangeType.PARAMETER_DELETE);
            }
        } else if (delete.getParentEntity().getType() == EntityType.METHOD_DECLARATION) {
            if (EntityType.isType(delete.getChangedEntity().getType())) {
                // whenever void as return type is deleted, a concrete return type was inserted. therefore we can ignore
                // this delete change
                if (delete.getChangedEntity().getUniqueName().endsWith(VOID_RETURN)) {
                    return null;
                } else {
                    delete.setChangeType(ChangeType.RETURN_TYPE_DELETE);
                }
            }
        } else {
            return null;
        }
        return delete;
    }

    private static SourceCodeChange extractModifiersChange(Delete delete) {
        SourceCodeChange result = delete;

        if (delete.getChangedEntity().getUniqueName().equals(FINAL)) {
            return handleFinalChange(delete);
        } else if (delete.getChangedEntity().getUniqueName().equals(PRIVATE)) {
            result = extractIncreasingAccessibilityChange(delete);
        } else if (delete.getChangedEntity().getUniqueName().equals(PUBLIC)) {
            result = extractDecreasingAccessibilityChange(delete);
        } else if (delete.getChangedEntity().getUniqueName().equals(PROTECTED)) {
            Insert insPublic =
                    findInsertOperation(
                            delete.getRootEntity().getType(),
                            delete.getRootEntity().getUniqueName(),
                            EntityType.MODIFIERS,
                            "",
                            EntityType.MODIFIER,
                            PUBLIC);
            Insert insPrivate =
                    findInsertOperation(
                            delete.getRootEntity().getType(),
                            delete.getRootEntity().getUniqueName(),
                            EntityType.MODIFIERS,
                            "",
                            EntityType.MODIFIER,
                            PRIVATE);
            if ((insPublic == null) && (insPrivate == null)) {
                delete.setChangeType(ChangeType.DECREASING_ACCESSIBILITY_CHANGE);
            }
        }
        return result;
    }

    private static SourceCodeChange extractDecreasingAccessibilityChange(Delete delete) {
        delete.setChangeType(ChangeType.DECREASING_ACCESSIBILITY_CHANGE);
        SourceCodeChange result;

        Insert insProtected =
                findInsertOperation(
                        delete.getRootEntity().getType(),
                        delete.getRootEntity().getUniqueName(),
                        EntityType.MODIFIERS,
                        "",
                        EntityType.MODIFIER,
                        PROTECTED);
        Insert insPrivate =
                findInsertOperation(
                        delete.getRootEntity().getType(),
                        delete.getRootEntity().getUniqueName(),
                        EntityType.MODIFIERS,
                        "",
                        EntityType.MODIFIER,
                        PRIVATE);
        if (insProtected != null) {
            result =
                    new Update(
                            delete.getRootEntity(),
                            delete.getChangedEntity(),
                            insProtected.getChangedEntity(),
                            insProtected.getParentEntity());
            sInserts.remove(insProtected);
        } else if (insPrivate != null) {
            result =
                    new Update(
                            delete.getRootEntity(),
                            delete.getChangedEntity(),
                            insPrivate.getChangedEntity(),
                            insPrivate.getParentEntity());
            sInserts.remove(insPrivate);
        } else {
            result = delete;
        }
        result.setChangeType(ChangeType.DECREASING_ACCESSIBILITY_CHANGE);
        return result;
    }

    private static SourceCodeChange extractIncreasingAccessibilityChange(Delete delete) {
        delete.setChangeType(ChangeType.INCREASING_ACCESSIBILITY_CHANGE);
        SourceCodeChange result;

        Insert insProtected =
                findInsertOperation(
                        delete.getRootEntity().getType(),
                        delete.getRootEntity().getUniqueName(),
                        EntityType.MODIFIERS,
                        "",
                        EntityType.MODIFIER,
                        PROTECTED);
        findInsertOperation(
                delete.getRootEntity().getType(),
                delete.getRootEntity().getUniqueName(),
                EntityType.MODIFIERS,
                "",
                EntityType.MODIFIER,
                PUBLIC);
        if (insProtected != null) {
            result =
                    new Update(
                            delete.getRootEntity(),
                            delete.getChangedEntity(),
                            insProtected.getChangedEntity(),
                            insProtected.getParentEntity());
            result.setChangeType(ChangeType.INCREASING_ACCESSIBILITY_CHANGE);
            sInserts.remove(insProtected);
        } else {
            result = delete;
        }
        return result;
    }

    private static SourceCodeChange handleFinalChange(Delete delete) {
        if (delete.getRootEntity().getType() == EntityType.CLASS) {
            delete.setChangeType(ChangeType.ADDING_CLASS_DERIVABILITY);
        } else if (delete.getRootEntity().getType() == EntityType.METHOD) {
            delete.setChangeType(ChangeType.ADDING_METHOD_OVERRIDABILITY);
        } else if (delete.getRootEntity().getType() == EntityType.ATTRIBUTE) {
            delete.setChangeType(ChangeType.ADDING_ATTRIBUTE_MODIFIABILITY);
        } else {
            return null;
        }
        return delete;
    }

    private static SourceCodeChange handleNormalDelete(Delete delete) {
        SourceCodeChange result = null;
        if (delete.getChangedEntity().getType() == EntityType.ELSE_STATEMENT) {
            delete.setChangeType(ChangeType.ALTERNATIVE_PART_DELETE);
            result = delete;
        } else if ((delete.getChangedEntity().getType() == EntityType.BLOCK_COMMENT)
                || (delete.getChangedEntity().getType() == EntityType.LINE_COMMENT)) {
            delete.setChangeType(ChangeType.COMMENT_DELETE);
            result = delete;
        } else if (EntityType.isAtStatementLevel(delete.getChangedEntity().getType())) {
            delete.setChangeType(ChangeType.STATEMENT_DELETE);
            result = delete;
        }
        return result;
    }

    private static SourceCodeChange classify(Move move) {
        SourceCodeChange result = null;

        if (move.getChangeType() != ChangeType.UNCLASSIFIED_CHANGE) {
            return move;
        }

        // ugly hack ;)
        if (move.getChangedEntity().getType() == EntityType.THEN_STATEMENT) {
            return null;
        }

        if (move.getRootEntity().getType() == EntityType.METHOD) {
            result = handleMethodSignatureChange(move);
            if (result == null) {
                result = handleNormalMove(move);
            }
        }
        return result;
    }

    private static SourceCodeChange handleMethodSignatureChange(Move move) {
        if ((move.getParentEntity().getType() == EntityType.PARAMETERS)
                && (move.getNewParentEntity().getType() == EntityType.PARAMETERS)
                && (move.getChangedEntity().getType() == EntityType.SINGLE_VARIABLE_DECLARATION)) {
            move.setChangeType(ChangeType.PARAMETER_ORDERING_CHANGE);
        } else {
            return null;
        }
        return move;
    }

    private static SourceCodeChange handleNormalMove(Move move) {
        SourceCodeChange result = null;
        if (EntityType.isAtStatementLevel(move.getChangedEntity().getType())) {
            if (move.getParentEntity().getUniqueName().equals(move.getNewParentEntity().getUniqueName())
                    && (move.getParentEntity().getType() == move.getNewParentEntity().getType())) {
                move.setChangeType(ChangeType.STATEMENT_ORDERING_CHANGE);
                result = move;
            } else {
                move.setChangeType(ChangeType.STATEMENT_PARENT_CHANGE);
                result = move;
            }
        }
        if ((move.getChangedEntity().getType() == EntityType.BLOCK_COMMENT)
                || (move.getChangedEntity().getType() == EntityType.LINE_COMMENT)) {
            move.setChangeType(ChangeType.COMMENT_MOVE);
            result = move;
        }
        return result;
    }

    private static SourceCodeChange classify(Update update) {
        SourceCodeChange result = null;

        if (update.getChangeType() != ChangeType.UNCLASSIFIED_CHANGE) {
            return update;
        }

        if (update.getRootEntity().getType() == EntityType.METHOD) {
            result = extractRenaming(update);
            if (result == null) {
                result = handleMethodSignatureChange(update);
                if (result == null) {
                    result = handleNormalUpdate(update);
                }
            }
        } else if (update.getRootEntity().getType() == EntityType.CLASS) {
            result = extractRenaming(update);
            if (result == null) {
                result = handleTypeDeclarationChange(update);
                if (result == null) {
                    result = handleInheritanceChange(update);
                }
            }
        } else if (update.getRootEntity().getType() == EntityType.ATTRIBUTE) {
            result = extractRenaming(update);
            if (result == null) {
                result = handleFieldDeclarationChange(update);
            }
        }
        return result;
    }

    private static SourceCodeChange handleInheritanceChange(Update update) {
        if (EntityType.isType(update.getNewEntity().getType())) {
            if (update.getParentEntity().getType() == EntityType.SUPER_INTERFACE_TYPES) {
                update.setChangeType(ChangeType.PARENT_INTERFACE_CHANGE);
            } else {
                update.setChangeType(ChangeType.PARENT_CLASS_CHANGE);
            }
        } else {
            return null;
        }
        return update;
    }

    private static SourceCodeChange handleFieldDeclarationChange(Update update) {
        if (EntityType.isType(update.getNewEntity().getType())) {
            update.setChangeType(ChangeType.ATTRIBUTE_TYPE_CHANGE);
        } else if (update.getChangedEntity().getType() == EntityType.JAVADOC) {
            update.setChangeType(ChangeType.DOC_UPDATE);
        } else {
            return null;
        }
        return update;
    }

    private static SourceCodeChange handleMethodSignatureChange(Update upd) {
        SourceCodeChange result = null;
        if (EntityType.isType(upd.getNewEntity().getType())) {
            if (upd.getParentEntity().getType() == EntityType.SINGLE_VARIABLE_DECLARATION) {
                String[] oldSplit = upd.getChangedEntity().getUniqueName().split(COLON);
                String[] newSplit = upd.getNewEntity().getUniqueName().split(COLON);
                if ((oldSplit.length > 1) && (newSplit.length > 1)) {
                    // MW: BUG FIX for IndexOutOfBoundsException
                    // BF: use 1 as index!!
                    if (!oldSplit[1].equals(newSplit[1])) {
                        upd.setChangeType(ChangeType.PARAMETER_TYPE_CHANGE);
                        result = upd;
                    }
                }
            } else {
                if (upd.getNewEntity().getUniqueName().endsWith(VOID_RETURN)) {
                    result = new Delete(upd.getRootEntity(), upd.getChangedEntity(), upd.getParentEntity());
                    result.setChangeType(ChangeType.RETURN_TYPE_DELETE);
                } else if (upd.getChangedEntity().getUniqueName().endsWith(VOID_RETURN)) {
                    result = new Insert(upd.getRootEntity(), upd.getNewEntity(), upd.getParentEntity());
                    result.setChangeType(ChangeType.RETURN_TYPE_INSERT);
                } else {
                    upd.setChangeType(ChangeType.RETURN_TYPE_CHANGE);
                    result = upd;
                }
            }
        } else if (upd.getNewEntity().getType() == EntityType.SINGLE_VARIABLE_DECLARATION) {
            upd.setChangeType(ChangeType.PARAMETER_RENAMING);
            result = upd;
        } else if (upd.getChangedEntity().getType() == EntityType.JAVADOC) {
            upd.setChangeType(ChangeType.DOC_UPDATE);
            result = upd;
        }
        return result;
    }

    private static SourceCodeChange extractRenaming(Update update) {
        if (update.getNewEntity().getType() == EntityType.METHOD_DECLARATION) {
            update.setChangeType(ChangeType.METHOD_RENAMING);
        } else if (update.getNewEntity().getType() == EntityType.FIELD_DECLARATION) {
            update.setChangeType(ChangeType.ATTRIBUTE_RENAMING);
        } else if (update.getNewEntity().getType() == EntityType.TYPE_DECLARATION) {
            update.setChangeType(ChangeType.CLASS_RENAMING);
        } else {
            return null;
        }
        return update;
    }

    private static SourceCodeChange handleNormalUpdate(Update update) {
        SourceCodeChange result = null;
        switch (update.getNewEntity().getType()) {
            case IF_STATEMENT:
            case FOR_STATEMENT:
            case WHILE_STATEMENT:
            case DO_STATEMENT:
            case ENHANCED_FOR_STATEMENT:
                update.setChangeType(ChangeType.CONDITION_EXPRESSION_CHANGE);
                result = update;
                break;
            case THEN_STATEMENT:
            case ELSE_STATEMENT:
                result = null;
                break;
            default:
                if ((update.getChangedEntity().getType() == EntityType.BLOCK_COMMENT)
                        || (update.getChangedEntity().getType() == EntityType.LINE_COMMENT)) {
                    update.setChangeType(ChangeType.COMMENT_UPDATE);
                    result = update;
                } else if (EntityType.isAtStatementLevel(update.getChangedEntity().getType())) {
                    update.setChangeType(ChangeType.STATEMENT_UPDATE);
                    result = update;
                }
        }
        return result;
    }

    private static Delete findSpDeleteOperation(
            EntityType structureEntityType,
            String structureEntityName,
            EntityType parentEntityType,
            String parentEntityName,
            String entityName) {
        for (Delete del : sDeletes) {
            if (isEqual(del.getRootEntity(), structureEntityType, structureEntityName)
                    && isEqual(del.getParentEntity(), parentEntityType, parentEntityName)
                    && EntityType.isType(del.getChangedEntity().getType())) {
                return del;
            }
        }
        return null;
    }

    private static Delete findDeleteOperation(
            EntityType structureEntityType,
            String structureEntityName,
            EntityType parentEntityType,
            String parentEntityName,
            EntityType entityType,
            String entityName) {
        for (Delete del : sDeletes) {
            if (isEqual(del.getRootEntity(), structureEntityType, structureEntityName)
                    && isEqual(del.getParentEntity(), parentEntityType, parentEntityName)
                    && isEqual(del.getChangedEntity(), entityType, entityName)) {
                return del;
            }
        }
        return null;
    }

    private static Insert findInsertOperation(
            EntityType structureEntityType,
            String structureEntityName,
            EntityType parentEntityType,
            String parentEntityName,
            EntityType entityType,
            String entityName) {
        for (Insert ins : sInserts) {
            if (isEqual(ins.getRootEntity(), structureEntityType, structureEntityName)
                    && isEqual(ins.getParentEntity(), parentEntityType, parentEntityName)
                    && isEqual(ins.getChangedEntity(), entityType, entityName)) {
                return ins;
            }
        }
        return null;
    }

    private static Move findMoveOperation(
            EntityType structureEntityType,
            String structureEntityName,
            EntityType oldParentEntityType,
            String oldParentEntityName,
            EntityType newParentEntityType,
            String newParentEntityName,
            EntityType entityType,
            String entityName) {
        for (Move mov : sMoves) {
            if (isEqual(mov.getRootEntity(), structureEntityType, structureEntityName)
                    && isEqual(mov.getParentEntity(), oldParentEntityType, oldParentEntityName)
                    && isEqual(mov.getNewParentEntity(), newParentEntityType, newParentEntityName)
                    && isEqual(mov.getChangedEntity(), entityType, entityName)) {
                return mov;
            }
        }
        return null;
    }

    private static boolean isEqual(SourceCodeEntity entity, EntityType expectedEntityType, String expectedEntityName) {
        boolean type = false;
        boolean name = false;
        if (expectedEntityType == null) {
            type = true;
        } else {
            type = entity.getType() == expectedEntityType;
        }
        if (expectedEntityName == null) {
            name = true;
        } else {
            name = entity.getUniqueName().equals(expectedEntityName);
        }
        return type && name;

    }

    private static boolean isEqual(
            StructureEntityVersion entity,
            EntityType expectedEntityType,
            String expectedEntityName) {
        boolean type = false;
        boolean name = false;
        if (expectedEntityType == null) {
            type = true;
        } else {
            type = entity.getType() == expectedEntityType;
        }
        if (expectedEntityName == null) {
            name = true;
        } else {
            name = entity.getUniqueName().equals(expectedEntityName);
        }
        return type && name;

    }

    private static void splitOperations(List<SourceCodeChange> operations) {
        sInserts = new LinkedList<Insert>();
        sDeletes = new LinkedList<Delete>();
        sMoves = new LinkedList<Move>();
        sUpdates = new LinkedList<Update>();
        for (SourceCodeChange op : operations) {
            if (isConsistent(op)) {
                if (op instanceof Insert) {
                    sInserts.add((Insert) op);
                } else if (op instanceof Delete) {
                    sDeletes.add((Delete) op);
                } else if (op instanceof Move) {
                    sMoves.add((Move) op);
                } else {
                    sUpdates.add((Update) op);
                }
            }
        }
    }

    private static boolean isConsistent(SourceCodeChange op) {
        boolean result = op.getChangedEntity() != null;
        result &= op.getParentEntity() != null;
        result &= op.getRootEntity() != null;
        if (op instanceof Move) {
            result &= ((Move) op).getNewEntity() != null;
            result &= ((Move) op).getNewParentEntity() != null;
        } else if (op instanceof Update) {
            result &= ((Update) op).getNewEntity() != null;
        }
        return result;
    }
}
