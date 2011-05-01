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
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.evolizer.changedistiller.model.classifiers.EntityType;

/**
 * Visitor that counts how many of the different {@link ASTNode} types that occur in a document are commented.
 * 
 * @author fluri
 * 
 */
public class ASTNodeToCommentCounter extends ASTVisitor {

    private List<Comment> fComments;
    private Document fSource;

    private ASTNode fLastVisitedNode;
    private Stack<ASTNode[]> fLastCommentTuples = new Stack<ASTNode[]>();

    private int[] fASTNodeTypeCounts = new int[EntityType.getNumberOfEntityTypes()];
    private int[] fASTNodeOccurrenceCount = new int[EntityType.getNumberOfEntityTypes()];

    private int fEmtpyCommentCount;

    /**
     * Creates a new visitor.
     * 
     * @param root
     *            of the AST
     * @param comments
     *            list of comments that as count input
     * @param source
     *            document in which the AST resides
     */
    public ASTNodeToCommentCounter(ASTNode root, List<Comment> comments, Document source) {
        fComments = comments;
        fSource = source;
        fLastVisitedNode = root;
    }

    /**
     * Returns the number of processed {@link ASTNode}s.
     * 
     * @return the number of processed AST nodes
     */
    public int[] getNodeOccurrences() {
        return fASTNodeOccurrenceCount;
    }

    /**
     * Returns the count for each {@link ASTNode} type.
     * 
     * @return the count for each node type
     */
    public int[] getNodeTypeCounts() {
        return fASTNodeTypeCounts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postVisit(ASTNode node) {
        if (isUnusableNode(node)) {
            return;
        }
        countOccurrence(node);
        if (!fLastCommentTuples.isEmpty() && (node == fLastCommentTuples.peek()[2])) {
            ASTNode preceedingNode = fLastCommentTuples.peek()[0];
            ASTNode commentNode = fLastCommentTuples.peek()[1];
            ASTNode succeedingNode = fLastCommentTuples.peek()[2];

            if (preceedingNode == null) {
                setNodeCount(succeedingNode);
            } else if (succeedingNode == null) {
                setNodeCount(preceedingNode);
            } else {
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

                if (rateForPreceeding > rateForSucceeding) {
                    setNodeCount(preceedingNode);
                } else {
                    setNodeCount(succeedingNode);
                }
            }
            fLastCommentTuples.pop();
        }
        fLastVisitedNode = node;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preVisit(ASTNode node) {
        if (isUnusableNode(node)) {
            return;
        }
        int i = 0;
        while (i < fComments.size()) {
            Comment comment = fComments.get(i);
            if ((fLastVisitedNode != null) && (fLastVisitedNode.getStartPosition() >= 0)
                    && (fLastVisitedNode.getStartPosition() <= comment.getStartPosition())
                    && (comment.getStartPosition() <= node.getStartPosition())) {
                ASTNode[] commentTuple = new ASTNode[]{fLastVisitedNode, comment, node};
                fLastCommentTuples.push(commentTuple);
                fComments.remove(i--);
            }
            i++;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(FieldDeclaration node) {
        visitDeclaration(node);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(MethodDeclaration node) {
        visitDeclaration(node);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(PackageDeclaration node) {
        if (node.getJavadoc() != null) {
            checkEmptyJavaDoc(node, node.getJavadoc());
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(TypeDeclaration node) {
        visitDeclaration(node);
        return true;
    }

    private void checkEmptyJavaDoc(ASTNode node, Javadoc javaDoc) {
        String doc = getCommentString(javaDoc);
        String[] tmp = doc.split("/\\*+\\s*");
        doc = "";
        for (String s : tmp) {
            doc += s;
        }
        try {
            doc = doc.split("\\s*\\*/")[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            doc = doc.replace('/', ' ');
        }
        doc = doc.replace('*', ' ').trim();
        if (!doc.equals("")) {
            setNodeCount(node);
        } else {
            fEmtpyCommentCount++;
        }
    }

    private void countOccurrence(ASTNode node) {
        int nodeType = node.getNodeType();
        if (!(node instanceof Expression)) {
            if (node.getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
                nodeType = ((ExpressionStatement) node).getExpression().getNodeType();
            }
            fASTNodeOccurrenceCount[nodeType]++;
        }
    }

    private String getASTString(ASTNode node) {
        String result = node.toString();
        if ((node.getNodeType() == ASTNode.METHOD_DECLARATION) || (node.getNodeType() == ASTNode.TYPE_DECLARATION)
                || (node.getNodeType() == ASTNode.FIELD_DECLARATION)) {
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

    private void setNodeCount(ASTNode node) {
        ASTNode result = node;
        if (node instanceof Expression) {
            ASTNode tmp = node;
            while (!(tmp instanceof Statement) && !(tmp instanceof BodyDeclaration) && (tmp.getParent() != null)) {
                tmp = tmp.getParent();
            }
            if ((tmp instanceof Statement) || (tmp instanceof BodyDeclaration)) {
                result = tmp;
            }
        }
        if (result.getNodeType() == ASTNode.EXPRESSION_STATEMENT) {
            result = ((ExpressionStatement) result).getExpression();
        }
        fASTNodeTypeCounts[result.getNodeType()]++;
    }

    private void visitDeclaration(BodyDeclaration node) {
        if (node.getJavadoc() != null) {
            checkEmptyJavaDoc(node, node.getJavadoc());
        }
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
}
