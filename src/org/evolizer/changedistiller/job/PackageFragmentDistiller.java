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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.evolizer.changedistiller.ChangeDistillerPlugin;
import org.evolizer.versioncontrol.cvs.model.entities.Revision;

/**
 * Distill package fragments.
 * 
 * @author fluri
 * @see Job
 */
public class PackageFragmentDistiller extends Job {

    /**
     * Standard tick for progress monitor.
     */
    public static final int WORK = 500;

    private IPackageFragment fFragment;

    /**
     * Creates a new package fragment distiller job.
     * 
     * @param name
     *            the name of this job
     */
    public PackageFragmentDistiller(String name) {
        super(name);
    }

    /**
     * Sets the package fragment to distill.
     * 
     * @param fragment
     *            to distill
     */
    public void setPackageFragment(IPackageFragment fragment) {
        fFragment = fragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IStatus run(IProgressMonitor monitor) {
        boolean jobCanceled = false;
        boolean jobError = false;
        IStatus result = Status.OK_STATUS;
        try {
            monitor.beginTask("Distill package", WORK);
            IJavaElement[] javaElements = fFragment.getChildren();
            int tick = WORK / javaElements.length;

            // process each Java element in package fragment.
            for (int i = 0; (i < javaElements.length) && !jobError && !jobCanceled && !monitor.isCanceled(); i++) {
                // compilation units are distilled in a separate job
                if (javaElements[i].getElementType() == IJavaElement.COMPILATION_UNIT) {
                    SourceDistiller job = new SourceDistiller("Process class " + javaElements[i].getElementName());
                    List<Revision> revisions =
                            ChangeDistillerPlugin.getPersistencyProvider()
                                    .query(
                                            "from Revision as r where r.file.path like '%/"
                                                    + javaElements[i].getElementName()
                                                    + "' and r.number not like '1.%.%'"
                                                    + " order by r.report.creationTime asc",
                                            Revision.class);
                    job.setProject(fFragment.getJavaProject().getProject());
                    job.setRevisionsToDistill(revisions);
                    job.setProgressGroup(monitor, SourceDistiller.WORK);
                    job.setPriority(Job.LONG);
                    monitor.setTaskName(job.getName() + " (" + (i + 1) + "/" + javaElements.length + ")");
                    job.schedule();
                    job.join();
                    jobCanceled = job.getResult().getSeverity() == IStatus.CANCEL;
                    jobError = job.getResult().getSeverity() == IStatus.ERROR;
                    if (jobError) {
                        result = job.getResult();
                    }
                    if (job.getResult().getSeverity() == IStatus.INFO) {
                        ChangeDistillerPlugin.getDefault().addToFailureSet(javaElements[i].getElementName());
                    }
                }
                monitor.worked(tick);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        if (monitor.isCanceled() || jobCanceled) {
            result = Status.CANCEL_STATUS;
        }
        return result;
    }
}
