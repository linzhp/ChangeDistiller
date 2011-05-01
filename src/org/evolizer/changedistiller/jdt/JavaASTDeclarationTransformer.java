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

import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.evolizer.changedistiller.model.classifiers.EntityType;
import org.evolizer.changedistiller.model.classifiers.SourceRange;
import org.evolizer.changedistiller.model.entities.SourceCodeEntity;
import org.evolizer.changedistiller.treedifferencing.Node;

/**
 * Visitor to generate an intermediate tree (general, rooted, labeled, valued tree) out of a attribute, class, or method
 * declaration.
 * 
 * @author fluri
 * 
 */
public class JavaASTDeclarationTransformer extends ASTVisitor {

    private static final String COLON_SPACE = ": ";
    private boolean fEmptyJavaDoc;
    private Stack<Node> fNodeStack = new Stack<Node>();
    private boolean fInMethodDeclaration;
    private Document fSource;
    private AbstractASTHelper fASTHelper;

    /**
     * Creates a new declaration transformer.
     * 
     * @param root
     *            the root node of the tree to generate
     * @param source
     *            the document in which the AST to parse resides
     * @param astHelper
     *            the helper that helps with conversions for the change history meta model
     */
    public JavaASTDeclarationTransformer(Node root, Document source, AbstractASTHelper astHelper) {
        fSource = source;
        fNodeStack.clear();
        fNodeStack.push(root);
        fASTHelper = astHelper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(Block node) {
        // skip block as it is not interesting
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(Block node) {
    // do nothing pop is not needed (see visit(Block))
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(FieldDeclaration node) {
        if (node.getJavadoc() != null) {
            node.getJavadoc().accept(this);
        }
        visitList(EntityType.MODIFIERS, node.modifiers());
        node.getType().accept(this);
        visitList(EntityType.FRAGMENTS, node.fragments());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(FieldDeclaration node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(Javadoc node) {
        String string = null;
        try {
            string = fSource.get(node.getStartPosition(), node.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        if (checkEmptyJavaDoc(string)) {
            pushValuedNode(node, string);
        } else {
            fEmptyJavaDoc = true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(Javadoc node) {
        if (!fEmptyJavaDoc) {
            pop();
        }
        fEmptyJavaDoc = false;
    }

    private boolean checkEmptyJavaDoc(String doc) {
        String[] splittedDoc = doc.split("/\\*+\\s*");
        String result = "";
        for (String s : splittedDoc) {
            result += s;
        }
        try {
            result = result.split("\\s*\\*/")[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            result = result.replace('/', ' ');
        }
        result = result.replace('*', ' ').trim();

        return !result.equals("");
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(MethodDeclaration node) {
        if (node.getJavadoc() != null) {
            node.getJavadoc().accept(this);
        }
        fInMethodDeclaration = true;
        visitList(EntityType.MODIFIERS, node.modifiers());
        if (node.getReturnType2() != null) {
            node.getReturnType2().accept(this);
        }
        visitList(EntityType.TYPE_ARGUMENTS, node.typeParameters());
        visitList(EntityType.PARAMETERS, node.parameters());
        visitList(EntityType.THROW, node.thrownExceptions());
        // ignore body, since only declaration is interesting
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(MethodDeclaration node) {
        fInMethodDeclaration = false;
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(Modifier node) {
        pushValuedNode(node, node.getKeyword().toString());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(Modifier node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(ParameterizedType node) {
        pushEmptyNode(node);
        node.getType().accept(this);
        visitList(EntityType.TYPE_ARGUMENTS, node.typeArguments());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(ParameterizedType node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(PrimitiveType node) {
        String vName = "";
        if (fInMethodDeclaration) {
            vName += getCurrentParent().getValue() + COLON_SPACE;
        }
        pushValuedNode(node, vName + node.getPrimitiveTypeCode().toString());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(PrimitiveType node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(QualifiedType node) {
        pushEmptyNode(node);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(QualifiedType node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(SimpleType node) {
        String vName = "";
        if (fInMethodDeclaration) {
            vName += getCurrentParent().getValue() + COLON_SPACE;
        }
        pushValuedNode(node, vName + node.getName().getFullyQualifiedName());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(SimpleType node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(SingleVariableDeclaration node) {
        boolean isNotParam = getCurrentParent().getLabel() != EntityType.PARAMETERS;
        pushValuedNode(node, node.getName().getIdentifier());
        if (isNotParam) {
            visitList(EntityType.MODIFIERS, node.modifiers());
        }
        node.getType().accept(this);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(SingleVariableDeclaration node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(TypeDeclaration node) {
        if (node.getJavadoc() != null) {
            node.getJavadoc().accept(this);
        }
        visitList(EntityType.MODIFIERS, node.modifiers());
        visitList(EntityType.TYPE_ARGUMENTS, node.typeParameters());
        if (node.getSuperclassType() != null) {
            node.getSuperclassType().accept(this);
        }
        visitList(EntityType.SUPER_INTERFACE_TYPES, node.superInterfaceTypes());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(TypeDeclaration node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(TypeDeclarationStatement node) {
        // skip, only type declaration is interesting
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(TypeDeclarationStatement node) {
    // do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(TypeLiteral node) {
        pushEmptyNode(node);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(TypeLiteral node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(TypeParameter node) {
        pushValuedNode(node, node.getName().getFullyQualifiedName());
        visitList(node.typeBounds());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(TypeParameter node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(VariableDeclarationExpression node) {
        pushEmptyNode(node);
        visitList(EntityType.MODIFIERS, node.modifiers());
        node.getType().accept(this);
        visitList(EntityType.FRAGMENTS, node.fragments());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(VariableDeclarationExpression node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(VariableDeclarationFragment node) {
        pushValuedNode(node, node.getName().getFullyQualifiedName());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(VariableDeclarationFragment node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(VariableDeclarationStatement node) {
        pushEmptyNode(node);
        visitList(EntityType.MODIFIERS, node.modifiers());
        node.getType().accept(this);
        visitList(EntityType.FRAGMENTS, node.fragments());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(VariableDeclarationStatement node) {
        pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(WildcardType node) {
        String bound = node.isUpperBound() ? "extends" : "super";
        pushValuedNode(node, bound);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(WildcardType node) {
        pop();
    }

    private void visitList(List<ASTNode> list) {
        for (ASTNode node : list) {
            (node).accept(this);
        }
    }

    private void visitList(EntityType parentLabel, List<ASTNode> list) {
        int[] position = extractPosition(list);
        push(parentLabel, "", position[0], position[1]);
        if (!list.isEmpty()) {
            visitList(list);
        }
        pop();
    }

    private void pushEmptyNode(ASTNode node) {
        push(fASTHelper.convertNode(node), "", node.getStartPosition(), node.getLength());
    }

    private void pushValuedNode(ASTNode node, String value) {
        push(fASTHelper.convertNode(node), value, node.getStartPosition(), node.getLength());
    }

    private void push(EntityType label, String value, int startPosition, int length) {
        SourceCodeEntity st = null;
        st = new SourceCodeEntity(value.trim(), label, new SourceRange(startPosition, length));
        Node n = new Node(label, value.trim(), st);
        getCurrentParent().add(n);
        fNodeStack.push(n);
    }

    private void pop() {
        fNodeStack.pop();
    }

    private Node getCurrentParent() {
        return fNodeStack.peek();
    }

    private int[] extractPosition(List<ASTNode> list) {
        int offset = -1;
        int length = -1;
        if (!list.isEmpty()) {
            ASTNode first = list.get(0);
            ASTNode last = list.get(list.size() - 1);
            offset = first.getStartPosition();
            length = last.getStartPosition() + last.getLength() - offset;
        }
        return new int[]{offset, length};
    }
}
