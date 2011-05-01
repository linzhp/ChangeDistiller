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

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BlockComment;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

/**
 * Visitor that removes comments that are dead code and joins successive line comments.
 * 
 * @author fluri
 * 
 */
public class ASTCommentVisitor extends ASTVisitor {

    private static final String NON_NLS = "NON-NLS";

    private static Pattern sPatternCatchAll =
            Pattern.compile("(?s).*([aA-zZ0-9]*\\.)*[aA-zZ0-9]+?\\((?s).*?\\);(?s).*" + "| (?s).+=(?s).+?;(?s).*"
                    + "| (?s).*if\\s*\\((?s).*\\)\\s*\\{*\\s*(?s).*"
                    + "| (?s).*?try\\s*\\{(?s).*?\\}\\s*catch\\s*\\((?s).*?\\)\\s*\\{(?s).*?\\}(?s).*");

    private Document fDocument;
    private Vector<Comment> fComments;
    private Comment fLastComment;
    private ASTNode fVisitedComment;
    private boolean fClean;
    private int fCommentsCount;

    /**
     * Creates a new comment visitor.
     * 
     * @param document
     *            to visit the containing source code
     */
    public ASTCommentVisitor(Document document) {
        fDocument = document;
        fComments = new Vector<Comment>();
    }

    /**
     * Adjust the length of the of the visited node to include the given comment.
     * 
     * @param node
     *            the comment that is added to the visited comment.
     */
    public void add(Comment node) {
        if (!getCommentString(node).contains(NON_NLS)) {
            int newLength = node.getLength();
            if (fVisitedComment != null) {
                newLength = newLength + (node.getStartPosition() - fVisitedComment.getStartPosition());
                fVisitedComment.setSourceRange(fVisitedComment.getStartPosition(), newLength);
            } else {
                fVisitedComment = node;
            }
            fLastComment = node;
            fComments.add((Comment) fVisitedComment);
        }
    }

    /**
     * Returns the comments processed.
     * 
     * @return the comments processed
     */
    public Vector<Comment> getComments() {
        if (!fClean) { // fComments might still contain commented source code
            removeCommentedSourceCode();
        }
        return fComments;
    }

    /**
     * Returns the number of comments processed.
     * 
     * @return the number of comments processed
     */
    public int getCommentsCount() {
        return fCommentsCount;
    }

    /**
     * Visits the given {@link ASTNode} before <code>visit</code> is executed.
     * 
     * <p>
     * If the last node was a line comment, this node is counted as "in the same block". Therefore visitComment should
     * not be empty, but contain the start of the block. Further the "not yet complete" block has to be removed from the
     * result Vector.
     * 
     * @param node
     *            the node to visit
     */
    @Override
    public void preVisit(ASTNode node) {
        if ((fLastComment != null) && (fLastComment.getNodeType() == ASTNode.LINE_COMMENT)
                && (node.getNodeType() == ASTNode.LINE_COMMENT) && !getCommentString(node).contains(NON_NLS)) {
            // Check if there are only blanks / tabs between lastNode and node
            int startOfElementAfterLastNode = fLastComment.getStartPosition() + fLastComment.getLength() + 1;
            String betweenNodes = "";
            try {
                int length = node.getStartPosition() - startOfElementAfterLastNode;
                if (length < 0) {
                    length = 0;
                }
                betweenNodes = fDocument.get(startOfElementAfterLastNode, length);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
            betweenNodes = betweenNodes.replaceAll("\t", "");
            betweenNodes = betweenNodes.replaceAll(" ", "");

            // If node is the direct successor of last comment
            if (betweenNodes.length() == 0) {
                fVisitedComment = fComments.remove(fComments.size() - 1);
            } else {
                fVisitedComment = null;
            }
        } else {
            fVisitedComment = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(BlockComment node) {
        fCommentsCount++;
        add(node);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(Javadoc node) {
        fCommentsCount++;
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visit(LineComment node) {
        fCommentsCount++;
        add(node);
        return true;
    }

    private String getCommentString(ASTNode node) {
        try {
            return fDocument.get(node.getStartPosition(), node.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Uses some regex patterns to guess whether a comment is actually commented source code and - if this is the case -
     * removes it from the set of comments.
     */
    private void removeCommentedSourceCode() {
        Vector<Comment> cleanComments = new Vector<Comment>();
        for (Comment comment : fComments) {
            Matcher matcher = sPatternCatchAll.matcher(getCommentString(comment));

            if ((comment.getNodeType() == ASTNode.JAVADOC) || !matcher.matches()) { // Javadocs often contain source
                // code examples
                cleanComments.add(comment);
            }
        }
        fComments = cleanComments;
        fClean = true;
    }
}
