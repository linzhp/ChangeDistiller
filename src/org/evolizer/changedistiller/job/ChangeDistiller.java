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

import java.util.List;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressConstants;
import org.evolizer.changedistiller.ChangeDistillerPlugin;
import org.evolizer.versioncontrol.cvs.model.entities.Revision;

/**
 * Handles the change distilling of {@link IJavaElement}s.
 * 
 * @author fluri
 */
public class ChangeDistiller {

    private static final String CHANGE_DISTILLER = "ChangeDistiller";
    private static final String CHANGE_DISTILLER_COMPLETED = CHANGE_DISTILLER + " completed";
    private static final String CHANGE_DISTILLER_COMPLETED_NOT_ALL_FILES =
            CHANGE_DISTILLER_COMPLETED
                    + " Not all files were distilled; see details and/or error log for more information";
    private static final int TICKS = 1000;

    /**
     * Distill change history for each given {@link IJavaElement}.
     * 
     * @param elements
     *            the elements to distill their change history
     * @throws ExecutionException
     *             whenever the underlying {@link Job} could not finish his job.
     */
    public void process(final IJavaElement... elements) throws ExecutionException {
        final IProgressMonitor group = Job.getJobManager().createProgressGroup();
        ChangeDistillerPlugin.getDefault().initializeFailureSet();

        // main ChangeDistiller job
        Job mainJob = new Job(CHANGE_DISTILLER) {

            @Override
            protected IStatus run(IProgressMonitor monitor) {
                setProperty(IProgressConstants.ICON_PROPERTY, ChangeDistillerPlugin.getDefault().getImageRegistry()
                        .get("main"));
                group.beginTask("Process Selection", TICKS);
                boolean jobCanceled = false;
                boolean jobError = false;
                Action finishedAction = null;

                // iterate over selected elements
                monitor.beginTask("Process selected items...", elements.length);

                // select project from first selected element
                ChangeDistillerPlugin.initializePersistencyProvider(elements[0].getJavaProject().getProject());
                for (int i = 0; (i < elements.length) && !monitor.isCanceled() && !jobCanceled && !jobError; i++) {
                    monitor.subTask("Item (" + i + "/" + elements.length + "): " + elements[i].getElementName());

                    // process each element and display result status
                    IStatus result = processElement(elements[i], group, TICKS / elements.length);

                    if (result.getSeverity() == IStatus.ERROR) {
                        finishedAction = getChangeDistillerErrorAction(result);
                        jobError = true;
                    } else if (result.getSeverity() == IStatus.CANCEL) {
                        finishedAction = getChangeDistillerCanceledAction();
                        jobCanceled = true;
                    } else {
                        finishedAction = getChangeDistillerCompletedAction(result);
                    }
                    monitor.worked(1);
                }
                monitor.done();
                if (finishedAction != null) {
                    if (isModal(this)) {
                        ChangeDistillerPlugin.showMessage(finishedAction);
                    } else {
                        setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
                        setProperty(IProgressConstants.ACTION_PROPERTY, finishedAction);
                    }
                }
                ChangeDistillerPlugin.getPersistencyProvider().close();
                return Status.OK_STATUS;
            }

            @Override
            public boolean belongsTo(Object family) {
                if (family instanceof String) {
                    return ((String) family).equals(CHANGE_DISTILLER);
                }
                return false;
            }
        };
        mainJob.setUser(true);
        mainJob.schedule();
    }

    /**
     * Returns the canceled message dialog action.
     * 
     * @return the canceled message dialog action
     */
    protected Action getChangeDistillerCanceledAction() {
        return new Action("ASTDiff processing status") {

            @Override
            public void run() {
                MessageDialog.openInformation(
                        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                        "ChangeDistiller canceled",
                        "Differencing has been canceled");
            }
        };
    }

    /**
     * Returns an action to show message dialogs for each kind of status.
     * 
     * @param status
     *            of which the message is made
     * @return the action representing the message dialog to show
     */
    protected Action getChangeDistillerCompletedAction(IStatus status) {
        return new Action("ChangeDistiller processing status") {

            @Override
            public void run() {
                if (ChangeDistillerPlugin.getDefault().getFailureSet().isEmpty()) {
                    MessageDialog.openInformation(
                            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                            CHANGE_DISTILLER_COMPLETED,
                            "Differencing has been completed");
                } else {
                    MultiStatus status =
                            new MultiStatus(
                                    ChangeDistillerPlugin.getDefault().getBundle().getSymbolicName(),
                                    IStatus.OK,
                                    CHANGE_DISTILLER_COMPLETED_NOT_ALL_FILES,
                                    null);
                    for (String failure : ChangeDistillerPlugin.getDefault().getFailureSet()) {
                        status.add(new Status(IStatus.ERROR, ChangeDistillerPlugin.getDefault().getBundle()
                                .getSymbolicName(), IStatus.OK, "Failed file: " + failure, null));
                    }
                    ErrorDialog.openError(
                            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                            CHANGE_DISTILLER_COMPLETED,
                            CHANGE_DISTILLER_COMPLETED_NOT_ALL_FILES,
                            status);
                }
            }
        };
    }

    /**
     * Returns the error message dialog action
     * 
     * @param errorStatus
     *            the error status of ChangeDistiller
     * @return the error message dialog action
     */
    protected Action getChangeDistillerErrorAction(final IStatus errorStatus) {
        return new Action("ChangeDistiller Error") {

            @Override
            public void run() {
                Status error =
                        new Status(
                                IStatus.ERROR,
                                ChangeDistillerPlugin.getDefault().getBundle().getSymbolicName(),
                                IStatus.ERROR,
                                errorStatus.getMessage(),
                                errorStatus.getException());
                ErrorDialog.openError(
                        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                        "ChangeDistiller error",
                        "Error occurred; see details or error log for more information",
                        error);
            }
        };
    }

    private boolean isModal(Job job) {
        Boolean result = (Boolean) job.getProperty(IProgressConstants.PROPERTY_IN_DIALOG);
        if (result == null) {
            return false;
        }
        return result.booleanValue();
    }

    private IStatus processElement(IJavaElement element, IProgressMonitor group, int tick) {
        Job changeDistillerJob = null;
        try {
            // ICompilationUnit
            if (element.getElementType() == IJavaElement.COMPILATION_UNIT) {
                changeDistillerJob = new SourceDistiller("Process class " + element.getElementName());
                SourceDistiller job = (SourceDistiller) changeDistillerJob;
                List<Revision> revisions =
                        ChangeDistillerPlugin.getPersistencyProvider().query(
                                "from Revision as r where r.file.path like '%/" + element.getElementName()
                                        + "' and r.number not like '1.%.%'" + " order by r.report.creationTime asc",
                                Revision.class);
                job.setProject(element.getJavaProject().getProject());
                job.setRevisionsToDistill(revisions);

                // IPackageFragment; but only process source fragments (exclude binary Jars)
            } else if (element.getElementType() == IJavaElement.PACKAGE_FRAGMENT) {
                IPackageFragment fragment = (IPackageFragment) element;

                if ((fragment.getKind() == IPackageFragmentRoot.K_SOURCE) && fragment.containsJavaResources()) {
                    changeDistillerJob = new PackageFragmentDistiller("Process Package " + fragment.getElementName());
                    ((PackageFragmentDistiller) changeDistillerJob).setPackageFragment(fragment);
                }

                // IJavaProject
            } else if (element.getElementType() == IJavaElement.JAVA_PROJECT) {

                changeDistillerJob = new ProjectDistiller("Process project " + element.getElementName());
                ((ProjectDistiller) changeDistillerJob).setJavaProject((IJavaProject) element);

            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        if (changeDistillerJob != null) {
            changeDistillerJob.setProgressGroup(group, tick);
            changeDistillerJob.setPriority(Job.LONG);
            changeDistillerJob.schedule();

            try {
                // wait until ChangeDistiller finished selected element
                changeDistillerJob.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return changeDistillerJob.getResult();
        }
        return Status.OK_STATUS;
    }
}
