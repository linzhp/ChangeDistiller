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

import java.util.Hashtable;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.evolizer.changedistiller.model.classifiers.EntityType;
import org.evolizer.changedistiller.model.classifiers.SourceRange;
import org.evolizer.changedistiller.model.entities.SourceCodeEntity;
import org.evolizer.changedistiller.treedifferencing.Node;

/**
 * Visitor to generate an intermediate tree (general, rooted, labeled, valued tree) out of a method body.
 * 
 * @author fluri
 * 
 */
public class JavaASTBodyTransformer extends ASTVisitor {

    private static final String COLON = ":";
    private List<Comment> fComments;
    private Stack<Node> fNodeStack = new Stack<Node>();
    private Document fSource;

    private ASTNode fLastVisitedNode;
    private Node fLastAddedNode;

    private Stack<ASTNode[]> fLastCommentTuples = new Stack<ASTNode[]>();
    private Stack<Node[]> fLastCommentNodeTuples = new Stack<Node[]>();
    private AbstractASTHelper fASTHelper;

    /**
     * Creates a new structure transformer.
     * 
     * @param root
     *            the root node of the tree to generate
     * @param astRoot
     *            the AST root node, necessary for comment attachment
     * @param comments
     *            to attach
     * @param source
     *            the document in which the AST resides
     * @param astHelper
     *            the helper that helps with conversions for the change history meta model.
     */
    public JavaASTBodyTransformer(
            Node root,
            ASTNode astRoot,
            List<Comment> comments,
            Document source,
            AbstractASTHelper astHelper) {
        fNodeStack.clear();
        fLastVisitedNode = astRoot;
        fLastAddedNode = root;
        fNodeStack.push(root);
        fComments = comments;
        fSource = source;
        fASTHelper = astHelper;
    }

    /**
     * Prepares node for comment attachment.
     * 
     * @param node
     *            the node to prepare for comment attachment
     */
    @Override
    public void preVisit(ASTNode node) {
        if (isUnusableNode(node)) {
            return;
        }
        int i = 0;
        while (i < fComments.size()) {
            // for (int i = 0; i < fComments.size(); i++) {
            Comment comment = fComments.get(i);
            if ((fLastVisitedNode != null) && (fLastVisitedNode.getStartPosition() > 0)
                    && (fLastVisitedNode.getStartPosition() < comment.getStartPosition())
                    && (comment.getStartPosition() < node.getStartPosition())) {
                String commentString = null;
                try {
                    commentString = fSource.get(comment.getStartPosition(), comment.getLength());
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }

                ASTNode[] commentTuple = new ASTNode[]{fLastVisitedNode, comment, node};
                fLastCommentTuples.push(commentTuple);

                Node[] nodeTuple = new Node[2];
                nodeTuple[0] = fLastAddedNode; // preceeding node

                pushValuedNode(comment, commentString);
                pop(comment);
                nodeTuple[1] = fLastAddedNode; // comment
                fLastCommentNodeTuples.push(nodeTuple);

                fComments.remove(i--);
            }
            i++;
        }
    }

    /**
     * Does the comment to code association for the triple {preceedingNode, comment, succeedingNode}
     * 
     * @param node
     *            succeeding node of the triple
     */
    @Override
    public void postVisit(ASTNode node) {
        if (isUnusableNode(node)) {
            return;
        }
        if (!fLastCommentTuples.isEmpty() && (node == fLastCommentTuples.peek()[2])) {
            ASTNode preceedingNode = fLastCommentTuples.peek()[0];
            ASTNode commentNode = fLastCommentTuples.peek()[1];
            ASTNode succeedingNode = fLastCommentTuples.peek()[2];

            if ((preceedingNode != null) && (succeedingNode != null)) {
                String preceedingNodeString = getASTString(preceedingNode);
                String succeedingNodeString = getASTString(succeedingNode);
                String commentNodeString = getCommentString(commentNode);

                int rateForPreceeding = 0;
                int rateForSucceeding = 0;

                rateForPreceeding += proximityRating(preceedingNode, commentNode);
                rateForSucceeding += proximityRating(commentNode, succeedingNode);

                if (rateForPreceeding == rateForSucceeding) {
                    rateForPreceeding += wordMatching(preceedingNodeString, commentNodeString);
                    rateForSucceeding += wordMatching(succeedingNodeString, commentNodeString);
                }
                if (rateForPreceeding == rateForSucceeding) {
                    rateForSucceeding++;
                }

                Node[] nodeTuple = fLastCommentNodeTuples.peek();
                if (rateForPreceeding > rateForSucceeding) {
                    nodeTuple[1].addAssociatedNode(nodeTuple[0]);
                    nodeTuple[0].addAssociatedNode(nodeTuple[1]);
                } else {
                    nodeTuple[1].addAssociatedNode(fLastAddedNode);
                    fLastAddedNode.addAssociatedNode(nodeTuple[1]);
                }
            }
            fLastCommentTuples.pop();
            fLastCommentNodeTuples.pop();
        }
    }

    /**
     * Calculates the proximity between the two given {@link ASTNode}. Usually one of the nodes is a comment.
     * 
     * @param nodeOne
     *            to calculate the proximity
     * @param nodeTwo
     *            to calculate the proximity
     * @return <code>2</code> if the comment node is on the same line as the other node, <code>1</code> if they are on
     *         adjacent line, <code>0</code> otherwise (times two)
     */
    private int proximityRating(ASTNode left, ASTNode right) {
        int result = 0;
        ASTNode nodeOne = left;
        ASTNode nodeTwo = right;
        // swap code, if nodeOne is not before nodeTwo
        if ((nodeTwo.getStartPosition() - nodeOne.getStartPosition()) < 0) {
            ASTNode tmpNode = nodeOne;
            nodeOne = nodeTwo;
            nodeTwo = tmpNode;
        }

        try {
            int endOfNodePosition = nodeOne.getStartPosition() + nodeOne.getLength();

            // comment (nodeTwo) inside nodeOne
            if (endOfNodePosition > nodeTwo.getStartPosition()) {

                // find position before comment start
                String findNodeEndTemp =
                        fSource.get(nodeOne.getStartPosition(), (nodeTwo.getStartPosition() - nodeOne
                                .getStartPosition()));

                // remove white space between nodeOne and comment (nodeTwo)
                int lastNonSpaceChar = findNodeEndTemp.lastIndexOf("[^\\s]");
                if (lastNonSpaceChar > -1) {
                    findNodeEndTemp = findNodeEndTemp.substring(lastNonSpaceChar);
                }

                // end position of nodeOne before comment without succeeding white space
                endOfNodePosition = nodeTwo.getStartPosition() - findNodeEndTemp.length();
            }
            String betweenOneAndComment =
                    fSource.get(endOfNodePosition, (nodeTwo.getStartPosition() - endOfNodePosition));

            // Comment is on the same line as code, but node in code
            int positionAfterBracket = betweenOneAndComment.lastIndexOf("}");
            int positionAfterSemicolon = betweenOneAndComment.lastIndexOf(";");
            int sameLinePosition = Math.max(positionAfterBracket, positionAfterSemicolon);
            if (sameLinePosition > -1) {
                betweenOneAndComment =
                        betweenOneAndComment.substring(sameLinePosition + 1, betweenOneAndComment.length());
            }

            // 2 points if on the same line as well as inside the code,
            // i.e. there is no line break between the code and the comment
            String newLine = System.getProperty("line.separator");
            if (betweenOneAndComment.indexOf(newLine) == -1) {
                result += 2;

                // 1 point if on the succeeding line,
                // i.e. only one line break between the code and the comment
            } else if (betweenOneAndComment.replaceFirst(newLine, "").indexOf(newLine) == -1) {
                result++;
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        return result * 2;
    }

    /**
     * Calculates the word matching between the candidate and the comment string.
     * 
     * @param candidate
     *            to match with
     * @param comment
     *            to match for
     * @return number of tokens the candidate and comment string share (times 2)
     */
    private int wordMatching(String candidate, String comment) {
        int result = 0;

        // split and tokenize candidate string into a hash table
        Hashtable<String, Integer> tokenMatchTable = new Hashtable<String, Integer>();
        String[] candidateTokens = candidate.split("[\\.\\s]+");
        for (String candidateToken : candidateTokens) {
            if (tokenMatchTable.containsKey(candidateToken)) {
                tokenMatchTable.put(candidateToken, tokenMatchTable.remove(candidateToken) + 1);
            } else {
                tokenMatchTable.put(candidateToken, 1);
            }
        }

        // find comment tokens in candidate tokens;
        // number of occurrences are taken as points
        String[] commentTokens = comment.split("\\s+");
        for (String commentToken : commentTokens) {
            if (tokenMatchTable.containsKey(commentToken)) {
                result += tokenMatchTable.get(commentToken);
            }
        }

        return result * 2;
    }

    private String getASTString(ASTNode node) {
        String result = node.toString();
        if ((node.getNodeType() == ASTNode.METHOD_DECLARATION) || (node.getNodeType() == ASTNode.TYPE_DECLARATION)) {
            BodyDeclaration bd = (BodyDeclaration) node;
            if (bd.getJavadoc() != null) {
                ASTNode doc = bd.getJavadoc();
                if (doc.toString().length() > -1) {
                    result = result.substring(doc.toString().length());
                }
            }
        } else if (node.getNodeType() == ASTNode.COMPILATION_UNIT) {
            result = "";
        }

        return result;
    }

    private String getCommentString(ASTNode node) {
        try {
            return fSource.get(node.getStartPosition(), node.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(AssertStatement node) {
        String value = node.getExpression().toString();
        if (node.getMessage() != null) {
            value += COLON + node.getMessage().toString();
        }
        pushValuedNode(node, value);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(AssertStatement node) {
        pop(node);
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
    // do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(BreakStatement node) {
        pushValuedNode(node, node.getLabel() != null ? node.getLabel().toString() : "");
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(BreakStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(CatchClause node) {
        pushValuedNode(node, ((SimpleType) node.getException().getType()).getName().getFullyQualifiedName());
        // since exception type is used as value, visit children by hand
        node.getBody().accept(this);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(CatchClause node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(ConstructorInvocation node) {
        pushValuedNode(node, node.toString());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(ConstructorInvocation node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(ContinueStatement node) {
        pushValuedNode(node, node.getLabel() != null ? node.getLabel().toString() : "");
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(ContinueStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(DoStatement node) {
        pushValuedNode(node, node.getExpression().toString());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(DoStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(EmptyStatement node) {
        pushEmptyNode(node);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(EmptyStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(EnhancedForStatement node) {
        pushValuedNode(node, node.getParameter().toString() + COLON + node.getExpression().toString());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(EnhancedForStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(ExpressionStatement node) {
        pushValuedNode(node.getExpression(), node.toString());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(ExpressionStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(ForStatement node) {
        String value = "";
        if (node.getExpression() != null) {
            value = node.getExpression().toString();
        }
        pushValuedNode(node, value);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(ForStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(IfStatement node) {
        String expression = node.getExpression().toString();
        push(fASTHelper.convertNode(node), expression, node.getStartPosition(), node.getLength());
        if (node.getThenStatement() != null) {
            push(EntityType.THEN_STATEMENT, expression, node.getThenStatement().getStartPosition(), node
                    .getThenStatement().getLength());
            node.getThenStatement().accept(this);
            pop(node.getThenStatement());
        }
        if (node.getElseStatement() != null) {
            push(EntityType.ELSE_STATEMENT, expression, node.getElseStatement().getStartPosition(), node
                    .getElseStatement().getLength());
            node.getElseStatement().accept(this);
            pop(node.getElseStatement());
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(IfStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(LabeledStatement node) {
        pushValuedNode(node, node.getLabel().getFullyQualifiedName());
        node.getBody().accept(this);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(LabeledStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(ReturnStatement node) {
        pushValuedNode(node, node.getExpression() != null ? node.getExpression().toString() : "");
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(ReturnStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(SuperConstructorInvocation node) {
        pushValuedNode(node, node.toString());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(SuperConstructorInvocation node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(SwitchCase node) {
        pushValuedNode(node, node.getExpression() != null ? node.getExpression().toString() : "default");
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(SwitchCase node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(SwitchStatement node) {
        pushValuedNode(node, node.getExpression().toString());
        visitList(node.statements());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(SwitchStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(SynchronizedStatement node) {
        pushValuedNode(node, node.getExpression().toString());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(SynchronizedStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(ThrowStatement node) {
        pushValuedNode(node, node.getExpression().toString());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(ThrowStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean visit(TryStatement node) {
        pushEmptyNode(node);
        push(EntityType.BODY, "", node.getBody().getStartPosition(), node.getBody().getLength());
        node.getBody().accept(this);
        pop(node.getBody());
        visitList(EntityType.CATCH_CLAUSES, node.catchClauses());
        if (node.getFinally() != null) {
            push(EntityType.FINALLY, "", node.getFinally().getStartPosition(), node.getFinally().getLength());
            node.getFinally().accept(this);
            pop(node.getFinally());
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(TryStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(VariableDeclarationStatement node) {
        pushValuedNode(node, node.toString());
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(VariableDeclarationStatement node) {
        pop(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(WhileStatement node) {
        push(fASTHelper.convertNode(node), node.getExpression().toString(), node.getStartPosition(), node.getLength());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endVisit(WhileStatement node) {
        pop(node);
    }

    private void visitList(List<ASTNode> list) {
        for (ASTNode element : list) {
            element.accept(this);
        }
    }

    private void visitList(EntityType parentLabel, List<ASTNode> list) {
        if (!list.isEmpty()) {
            int[] position = extractPosition(list);
            push(parentLabel, "", position[0], position[1]);
            visitList(list);
            pop(null);
        }
    }

    private void pushValuedNode(ASTNode node, String value) {
        push(fASTHelper.convertNode(node), value, node.getStartPosition(), node.getLength());
    }

    private void pushEmptyNode(ASTNode node) {
        push(fASTHelper.convertNode(node), "", node.getStartPosition(), node.getLength());
    }

    private void push(EntityType label, String value, int offset, int length) {
        Node n =
                new Node(
                        label,
                        value.trim(),
                        new SourceCodeEntity(value.trim(), label, new SourceRange(offset, length)));
        getCurrentParent().add(n);
        fNodeStack.push(n);
    }

    private void pop(ASTNode node) {
        fLastVisitedNode = node;
        fLastAddedNode = fNodeStack.pop();
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

    private boolean isUnusableNode(ASTNode node) {
        switch (node.getNodeType()) {
            case ASTNode.BLOCK_COMMENT:
            case ASTNode.JAVADOC:
            case ASTNode.LINE_COMMENT:
                return true;
            default:
                return false;
        }
    }
}
