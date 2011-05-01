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
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.ui.progress.IProgressConstants;
import org.evolizer.changedistiller.ChangeDistillerPlugin;
import org.evolizer.versioncontrol.cvs.model.entities.Revision;

/**
 * Distill changes of an entire project.
 * 
 * @author fluri
 */
public class ProjectDistiller extends Job {

    /**
     * Standard tick value for progress monitor.
     */
    public static final int WORK = 2000;

    private static final String FILE_SEPARATOR = "\\\\|/";

    private IJavaProject fJavaProject;

    /**
     * Creates a new project distiller job.
     * 
     * @param name
     *            of this job
     */
    public ProjectDistiller(String name) {
        super(name);
    }

    /**
     * Sets project to distill.
     * 
     * @param project
     *            to distill
     */
    public void setJavaProject(IJavaProject project) {
        fJavaProject = project;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IStatus run(IProgressMonitor monitor) {
        boolean jobCanceled = false;
        boolean jobError = false;
        IStatus result = Status.OK_STATUS;
        setProperty(IProgressConstants.ICON_PROPERTY, ChangeDistillerPlugin.getDefault().getImageRegistry().get("main"));
        try {
            List<Object[]> files =
                    ChangeDistillerPlugin.getPersistencyProvider().query(
                            "select f.id,f.path from File as f where f.path like '%.java'",
                            Object[].class);

            int size = files.size();
            int tick = files.size() / WORK;
            for (int i = 0; (i < size) && !jobError && !jobCanceled && !monitor.isCanceled(); i++) {
                String fileName = getName((String) files.get(i)[1]);
                monitor.setTaskName("Process revisions for file: " + fileName + " (" + i + "/" + size + ")");

                List<Revision> revisions =
                        ChangeDistillerPlugin.getPersistencyProvider().query(
                                "from Revision as r where r.file.id=" + files.get(i)[0]
                                        + " and r.number not like '1.%.%'" + " order by r.report.creationTime asc",
                                Revision.class);

                SourceDistiller job = new SourceDistiller("Process class " + fileName);
                job.setProject(fJavaProject.getProject());
                job.setRevisionsToDistill(revisions);
                job.setProgressGroup(monitor, SourceDistiller.WORK);
                job.setPriority(Job.LONG);
                job.schedule();
                job.join();
                jobCanceled = job.getResult().getSeverity() == IStatus.CANCEL;
                jobError = job.getResult().getSeverity() == IStatus.ERROR;
                if (jobError) {
                    result = job.getResult();
                }

                monitor.worked(tick);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (monitor.isCanceled() || jobCanceled) {
            result = Status.CANCEL_STATUS;
        }
        return result;
    }

    private String getName(String fullPath) {
        if (fullPath.equals(FILE_SEPARATOR)) {
            return FILE_SEPARATOR;
        }
        String[] pathElements = fullPath.split(FILE_SEPARATOR);
        return pathElements[pathElements.length - 1];
    }
}
