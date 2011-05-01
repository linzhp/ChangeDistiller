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
package org.evolizer.changedistiller.job;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.ui.progress.IProgressConstants;
import org.evolizer.changedistiller.ChangeDistillerPlugin;
import org.evolizer.changedistiller.distilling.Distiller;
import org.evolizer.changedistiller.model.entities.ClassHistory;
import org.evolizer.core.util.resourcehandling.EvolizerFileHandler;
import org.evolizer.versioncontrol.cvs.model.entities.Revision;

/**
 * Distill all revisions of selected Java file.
 * 
 * @author fluri
 */
public class SourceDistiller extends Job {

    /**
     * Standard tick for progress monitor.
     */
    public static final int WORK = 1000;

    private static final String CVS_DEAD = "dead";
    private static final String DOT = ".";
    private static final Logger LOGGER =
            ChangeDistillerPlugin.getLogManager().getLogger(SourceDistiller.class.getCanonicalName());

    private static final int TICK_5 = 5;
    private static final int TICK_15 = 15;
    private static final int TICK_20 = 20;
    private static final int TICK_50 = 50;

    private List<Revision> fRevisions;
    private ICompilationUnit fCompilationUnit;
    private IProject fProject;
    private String fElementName;

    /**
     * Creates a new source distiller job.
     * 
     * @param name
     *            of this job.
     */
    public SourceDistiller(String name) {
        super(name);
    }

    /**
     * Sets project in which the {@link ICompilationUnit} resides.
     * 
     * @param project
     *            in which the compilation unit resides
     */
    public void setProject(IProject project) {
        fProject = project;
    }

    /**
     * Sets the {@link Revision}s to distill.
     * 
     * @param revisions
     *            to distill
     */
    public void setRevisionsToDistill(List<Revision> revisions) {
        fRevisions = revisions;
        String source = null;

        // no revisions given
        if ((fRevisions == null) || (fRevisions.size() == 0)) {
            fRevisions = new LinkedList<Revision>();
            // only one dead revision
        } else if ((fRevisions.size() == 1) && fRevisions.get(0).getState().equals(CVS_DEAD)
                && ((fRevisions.get(0).getSource() == null) || fRevisions.get(0).getSource().equals(""))) {
            fRevisions = new LinkedList<Revision>();

        } else {
            // if last revision of file is dead, use the second last
            if (fRevisions.get(fRevisions.size() - 1).getState().equals(CVS_DEAD)) {
                source = fRevisions.get(fRevisions.size() - 2).getSource();
            } else {
                source = fRevisions.get(fRevisions.size() - 1).getSource();
            }
            // source must not be empty, distiller cannot reconstruct revision set
            if ((source != null) && !source.equals("")) {
                EvolizerFileHandler fh = new EvolizerFileHandler(fProject);
                fh.cleanup();
                fh.open();

                fCompilationUnit =
                        JavaCore.createCompilationUnitFrom(fh.createFile(fRevisions.get(fRevisions.size() - 1)
                                .getFile().getName(), source));

                ASTParser parser = ASTParser.newParser(AST.JLS3);
                parser.setSource(source.toCharArray());
                CompilationUnit cu = (CompilationUnit) parser.createAST(null);
                PackageDeclaration packageDeclaration = cu.getPackage();
                String packageName =
                        (packageDeclaration != null) ? packageDeclaration.getName().getFullyQualifiedName() : "";

                fElementName = packageName + DOT + getClassName(fCompilationUnit);
                fh.close();
            } else {
                fRevisions = new LinkedList<Revision>();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IStatus run(IProgressMonitor monitor) {

        if ((fRevisions != null) && fRevisions.isEmpty()) {
            return new Status(
                    IStatus.WARNING,
                    ChangeDistillerPlugin.PLUGIN_ID,
                    "Could not distill; revision set was empty.");
        }
        if (fElementName == null) {
            fElementName = getElementName(fCompilationUnit);
        }

        // first check whether class has already a history in evolizer db
        if (existsClassHistory(fElementName)) {
            return infoStatus("ClassHistory for " + fElementName + " already exists in DB.");
        }

        setProperty(IProgressConstants.ICON_PROPERTY, ChangeDistillerPlugin.getDefault().getImageRegistry().get("main"));

        if (fProject == null) {
            fProject = fCompilationUnit.getJavaProject().getProject();
        }

        IStatus jobStatus = Status.OK_STATUS;

        // each compilation unit (normaly a class in a file) has a class history
        ClassHistory classHistory = null;

        // file handler to temporary save IFile for distilling
        EvolizerFileHandler fileHandler = new EvolizerFileHandler(fProject);
        fileHandler.cleanup();
        fileHandler.open();

        // IFile of selected compilation unit
        IFile file = (IFile) fCompilationUnit.getResource();

        if ((fRevisions == null) || fRevisions.isEmpty()) {
            String path = file.getProjectRelativePath().toString();
            fRevisions =
                    ChangeDistillerPlugin.getPersistencyProvider().query(
                            "select r from Revision as r " + "where r.file.path='" + path + "' "
                                    + "order by r.report.creationTime asc",
                            Revision.class);
        }

        file.getFileExtension();

        IFile leftFile = null;
        IFile rightFile = null;

        monitor.beginTask("Distilling revisions", WORK);
        int tick = (WORK - TICK_50) / fRevisions.size();

        LOGGER.info("Distilling file " + file.getProjectRelativePath().toString());

        // it is time to distill subsequent revisions
        for (int i = 0; (i < fRevisions.size()) && !monitor.isCanceled(); i++) {
            monitor.setTaskName("Distilling revisions " + "(" + (i + 1) + "/" + fRevisions.size() + ")");
            Revision r = fRevisions.get(i);

            if (r.getNumber().lastIndexOf('.') < 2) {
                monitor.subTask("load " + r.getNumber());

                // load source from evolizer database
                // distill iff source is available
                if ((r.getSource() != null) && !r.getSource().equals("")) {
                    rightFile = fileHandler.createFile("file_" + r.getNumber() + ".java", r.getSource());

                    // process only revisions > 1.1
                    if (!r.getNumber().equals("1.1") && (leftFile != null) && (rightFile != null)) {
                        monitor.subTask("distill >>" + r.getNumber());
                        Distiller distiller = new Distiller();
                        distiller.setClassHistory(classHistory);
                        distiller.performDistilling(leftFile, rightFile);
                        classHistory = distiller.getClassHistory();
                        if (classHistory != null) {
                            classHistory.updateLatestVersionWithRevision(r);
                        }
                        distiller = null;
                    }
                    leftFile = rightFile;
                }
            }
            monitor.worked(tick);
        }

        if (fileHandler != null) {
            fileHandler.close();
        }
        if (monitor.isCanceled()) {
            jobStatus = Status.CANCEL_STATUS;
        }
        if ((jobStatus.getSeverity() == IStatus.OK) && (classHistory != null)) {
            monitor.setTaskName("Calculate history values...");
            monitor.worked(TICK_5);
            if (classHistory.hasChanges()) {
                monitor.setTaskName("Open Evolizer session...");
                monitor.worked(TICK_5);
                monitor.setTaskName("Save class history");
                monitor.subTask("begin transaction");
                ChangeDistillerPlugin.getPersistencyProvider().startTransaction();
                monitor.worked(TICK_5);
                monitor.subTask("save object");
                ChangeDistillerPlugin.getPersistencyProvider().saveObject(classHistory);
                monitor.worked(TICK_20);
                monitor.subTask("commit transaction");
                ChangeDistillerPlugin.getPersistencyProvider().endTransaction();
                ChangeDistillerPlugin.getPersistencyProvider().flush();
                classHistory = null;
                ChangeDistillerPlugin.getPersistencyProvider().clear();
                monitor.worked(TICK_15);
            }
        }
        monitor.done();

        return jobStatus;
    }

    private boolean existsClassHistory(String elementName) {
        List<?> result =
                ChangeDistillerPlugin.getPersistencyProvider().query(
                        "select c from ClassHistory as c where c.uniqueName='" + elementName + "'",
                        ClassHistory.class);
        return !result.isEmpty();
    }

    private String getElementName(ICompilationUnit icu) {
        return fCompilationUnit.getParent().getElementName() + DOT + getClassName(icu);
    }

    private String getClassName(ICompilationUnit icu) {
        int pointIndex = fCompilationUnit.getElementName().lastIndexOf('.');
        return fCompilationUnit.getElementName().substring(0, pointIndex);
    }

    private IStatus infoStatus(String infoMsg) {
        return new Status(
                IStatus.INFO,
                ChangeDistillerPlugin.getDefault().getBundle().getSymbolicName(),
                IStatus.OK,
                infoMsg,
                null);
    }
}
