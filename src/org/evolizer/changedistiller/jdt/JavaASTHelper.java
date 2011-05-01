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
package org.evolizer.changedistiller.jdt;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jface.text.Document;
import org.evolizer.changedistiller.distilling.StructureDiffUtils;
import org.evolizer.changedistiller.model.classifiers.ChangeModifier;
import org.evolizer.changedistiller.model.classifiers.EntityType;
import org.evolizer.changedistiller.model.classifiers.SourceRange;
import org.evolizer.changedistiller.model.entities.SourceCodeEntity;
import org.evolizer.changedistiller.treedifferencing.Node;
import org.evolizer.core.exceptions.EvolizerRuntimeException;

/**
 * Helper class to assist distiller with Java AST information to create source code entities and structure entities out
 * of AST nodes in a given {@link IFile}.
 * 
 * @author fluri
 */
public final class JavaASTHelper extends AbstractASTHelper {

    private static Map<Integer, EntityType> sConversionMap = new HashMap<Integer, EntityType>();
    private CompilationUnit fCU;
    private Vector<Comment> fComments;
    private Document fSource;

    /**
     * Creates a new AST helper.
     * 
     * @param file
     *            on which the AST helper acts on
     */
    @SuppressWarnings("unchecked")
    public JavaASTHelper(IFile file) {
        super(file);
        fCU = createCompilationUnit(file);
        fSource = new Document(StructureDiffUtils.readFile(file));
        ASTCommentVisitor visitor = new ASTCommentVisitor(fSource);
        Iterator<ASTNode> i = fCU.getCommentList().iterator();
        while (i.hasNext()) {
            ASTNode commentNode = i.next();
            commentNode.accept(visitor);
        }
        fComments = visitor.getComments();
    }

    /**
     * Converts modifier flag provided by JDT {@link Modifier} to {@link ChangeModifier} flag.
     * 
     * @param mod
     *            modifier flag of JDT
     * @return modifier flag of {@link ChangeModifier}
     */
    public int convertModifier(int mod) {
        int changeModifier = 0x0;
        if (Modifier.isFinal(mod)) {
            changeModifier |= ChangeModifier.FINAL;
        }
        if (Modifier.isPublic(mod)) {
            changeModifier |= ChangeModifier.PUBLIC;
        }
        if (Modifier.isPrivate(mod)) {
            changeModifier |= ChangeModifier.PRIVATE;
        }
        if (Modifier.isProtected(mod)) {
            changeModifier |= ChangeModifier.PROTECTED;
        }
        return changeModifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EntityType convertNode(Object node) {
        if (!(node instanceof ASTNode)) {
            throw new EvolizerRuntimeException("Node must be of type ASTNode.");
        }
        ASTNode astNode = (ASTNode) node;
        if (sConversionMap.isEmpty()) {
            for (Field field : EntityType.class.getFields()) {
                try {
                    for (Field astField : ASTNode.class.getFields()) {
                        if (field.getName().equals(astField.getName())) {
                            int type = astField.getInt(ASTNode.class);
                            sConversionMap.put(type, EntityType.valueOf(field.getName()));
                        }
                    }
                } catch (IllegalArgumentException e) {
                    throw new EvolizerRuntimeException("Node type '" + astNode.getClass().getCanonicalName()
                            + "' not defined in EntityType.");
                } catch (IllegalAccessException e) {
                    throw new EvolizerRuntimeException(e.getMessage());
                }
            }
        }
        return sConversionMap.get(astNode.getNodeType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceCodeEntity createBodyRootSourceCodeEntity(String structureEntityName, SourceRange range) {
        return createSourceCodeEntityInternal(structureEntityName, range);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node createBodyTree(String nodeName, SourceRange range) {
        ASTNode astNode = findCorrespondingNode(range);
        Node root = new Node(EntityType.ROOT_NODE, nodeName, createEntityFromASTNode(nodeName, astNode));
        JavaASTBodyTransformer st = new JavaASTBodyTransformer(root, astNode, fComments, fSource, this);
        astNode.accept(st);
        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceCodeEntity createDeclarationRootSourceCodeEntity(String structureEntityName, SourceRange range) {
        return createRootNode(findCorrespondingNode(range), structureEntityName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node createDeclarationTree(String nodeName, SourceRange range) {
        ASTNode astNode = findCorrespondingNode(range);
        Node root = new Node(EntityType.ROOT_NODE, nodeName, createRootNode(astNode, nodeName));
        astNode.accept(new JavaASTDeclarationTransformer(root, fSource, this));
        return root;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceCodeEntity createSourceCodeEntity(String sourceCodeEntityName, SourceRange range) {
        return createSourceCodeEntityInternal(sourceCodeEntityName, range);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int extractModifiers(SourceRange range) {
        ASTNode node = findCorrespondingNode(range);
        switch (node.getNodeType()) {
            case ASTNode.FIELD_DECLARATION:
            case ASTNode.METHOD_DECLARATION:
            case ASTNode.TYPE_DECLARATION:
                return convertModifier(((BodyDeclaration) node).getModifiers());
            default:
                return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTopLevelName() {
        PackageDeclaration packageDeclaration = fCU.getPackage();
        String packageName = (packageDeclaration != null) ? packageDeclaration.getName().getFullyQualifiedName() : "";
        return packageName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isASTNode(Node node) {
        return node.getLabel().isValidChange();
    }

    private CompilationUnit createCompilationUnit(IFile file) {
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(StructureDiffUtils.readFile(file).toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }

    private SourceCodeEntity createEntityFromASTNode(String sourceCodeEntityName, ASTNode astNode) {
        SourceCodeEntity result = null;
        if (astNode != null) {
            switch (astNode.getNodeType()) {
                case ASTNode.METHOD_DECLARATION:
                    MethodDeclaration md = (MethodDeclaration) astNode;
                    result =
                            new SourceCodeEntity(md.getName().getIdentifier(), EntityType.METHOD, new SourceRange(
                                    astNode.getStartPosition(),
                                    astNode.getLength()));
                    result.setModifiers(convertModifier(md.getModifiers()));
                    result.setUniqueName(sourceCodeEntityName);
                    break;
                case ASTNode.TYPE_DECLARATION:
                    TypeDeclaration td = (TypeDeclaration) astNode;
                    result =
                            new SourceCodeEntity(td.getName().getIdentifier(), EntityType.CLASS, new SourceRange(
                                    astNode.getStartPosition(),
                                    astNode.getLength()));
                    result.setModifiers(convertModifier(td.getModifiers()));
                    result.setUniqueName(sourceCodeEntityName);
                    break;
                case ASTNode.FIELD_DECLARATION:
                    result =
                            new SourceCodeEntity(sourceCodeEntityName, EntityType.ATTRIBUTE, new SourceRange(astNode
                                    .getStartPosition(), astNode.getLength()));
                    result.setModifiers(convertModifier(((FieldDeclaration) astNode).getModifiers()));
                    break;
                default:
                    // do nothing
            }
        }
        return result;
    }

    private SourceCodeEntity createRootNode(ASTNode astNode, String sourceCodeEntityName) {
        SourceCodeEntity result =
                new SourceCodeEntity(sourceCodeEntityName, convertNode(astNode), new SourceRange(astNode
                        .getStartPosition(), astNode.getLength()));
        switch (astNode.getNodeType()) {
            case ASTNode.FIELD_DECLARATION:
            case ASTNode.METHOD_DECLARATION:
            case ASTNode.TYPE_DECLARATION:
                result.setModifiers(convertModifier(((BodyDeclaration) astNode).getModifiers()));
                break;
            default:
                result.setModifiers(0);
        }
        return result;
    }

    private SourceCodeEntity createSourceCodeEntityInternal(String entityName, SourceRange range) {
        if (range == null) {
            return null;
        }
        SourceCodeEntity entity = createEntityFromASTNode(entityName, findCorrespondingNode(range));
        if (entity == null) {
            entity =
                    createEntityFromASTNode(entityName, findCorrespondingNode(new SourceRange(
                            range.getOffset() - 1,
                            range.getLength())));
        }
        return entity;
    }

    private ASTNode findCorrespondingNode(SourceRange range) {
        return NodeFinder.perform(fCU, range.getOffset(), range.getLength());
    }
}
