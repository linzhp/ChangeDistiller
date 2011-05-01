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
package org.evolizer.changedistiller;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.PropertyConfigurator;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.evolizer.core.exceptions.EvolizerException;
import org.evolizer.core.exceptions.EvolizerRuntimeException;
import org.evolizer.core.hibernate.session.EvolizerSessionHandler;
import org.evolizer.core.hibernate.session.api.IEvolizerSession;
import org.evolizer.core.logging.base.PluginLogManager;
import org.osgi.framework.BundleContext;

/**
 * The main ChangeDistiller plugin class.
 * 
 * @author fluri
 */
public class ChangeDistillerPlugin extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "org.evolizer.changedistiller";

    private static final String LOG_PROPERTIES_FILE = "config/log4j.properties";
    private static ChangeDistillerPlugin sPlugin;

    private Set<String> fFailures;
    private IEvolizerSession fPersistencyProvider;
    private PluginLogManager fLogManager;

    /**
     * Creates a new ChangeDistiller plugin.
     */
    public ChangeDistillerPlugin() {
        sPlugin = this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        configure();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        ChangeDistillerPlugin.sPlugin = null;
        if (fLogManager != null) {
            fLogManager.shutdown();
            fLogManager = null;
        }
        super.stop(context);
    }

    /**
     * Returns the shared instance.
     * 
     * @return the shared instance
     */
    public static ChangeDistillerPlugin getDefault() {
        return sPlugin;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initializeImageRegistry(ImageRegistry reg) {
        super.initializeImageRegistry(reg);
        put(reg, "main", "icons/main.gif");
    }

    private void put(ImageRegistry reg, String key, String path) {
        reg.put(key, ImageDescriptor.createFromURL(getBundle().getEntry("./" + path)));
    }

    /**
     * Show dialog with message of action.
     * 
     * @param action
     *            to show the message for
     */
    public static void showMessage(final Action action) {
        Display.getDefault().asyncExec(new Runnable() {

            public void run() {
                action.run();
            }
        });
    }

    /**
     * Returns the {@link PluginLogManager} of this plugin.
     * 
     * @return the log manager of this plugin
     */
    public static PluginLogManager getLogManager() {
        return getDefault().fLogManager;
    }

    /**
     * Returns an {@link InputStream} of the file represented by the file path.
     * 
     * @param filePath
     *            relative path of the file starting
     * @return the input stream of the given file
     * @throws IOException
     *             if file could not be opened
     */
    public static InputStream openBundledFile(String filePath) throws IOException {
        return ChangeDistillerPlugin.getDefault().getBundle().getEntry(filePath).openStream();
    }

    private void configure() {
        try {
            InputStream propertiesInputStream = openBundledFile(LOG_PROPERTIES_FILE);

            if (propertiesInputStream != null) {
                Properties props = new Properties();
                props.load(propertiesInputStream);
                propertiesInputStream.close();

                // Hack: Allows us, to configure hibernate logging independently from other stuff.
                PropertyConfigurator.configure(props);

                fLogManager = new PluginLogManager(this, props);
            }

            propertiesInputStream.close();
        } catch (IOException e) {
            String errorString = "Error while initializing log properties.";
            IStatus status =
                    new Status(IStatus.ERROR, getDefault().getBundle().getSymbolicName(), IStatus.ERROR, errorString
                            + e.getMessage(), e);
            getLog().log(status);

            throw new EvolizerRuntimeException(errorString, e);
        }
    }

    /**
     * Initializes failure set.
     */
    public void initializeFailureSet() {
        fFailures = new HashSet<String>();
    }

    /**
     * Adds a failure to failure set to be shown at the end of a distilling process.
     * 
     * @param failureMsg
     *            to be added
     */
    public void addToFailureSet(String failureMsg) {
        System.out.println(failureMsg);
        getFailureSet().add(failureMsg);
    }

    /**
     * Returns the {@link Set} of failures the plugin had.
     * 
     * @return the failure set
     */
    public Set<String> getFailureSet() {
        return fFailures;
    }

    /**
     * Returns the {@link IEvolizerSession} to store change history model data.
     * 
     * @return persistency provider to store change history model data
     */
    public static IEvolizerSession getPersistencyProvider() {
        return getDefault().persistencyProvider();
    }

    /**
     * Initialize persistency provider with a {@link IProject}.
     * 
     * @param project
     *            the project on which the persistency provider has to act
     */
    public static void initializePersistencyProvider(IProject project) {
        getDefault().initEvolizerSession(project);
    }

    private void initEvolizerSession(IProject project) {
        try {
            fPersistencyProvider = EvolizerSessionHandler.getHandler().getCurrentSession(project);
        } catch (EvolizerException e) {
            e.printStackTrace();
        }
    }

    private IEvolizerSession persistencyProvider() {
        return fPersistencyProvider;
    }
}
