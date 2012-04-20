/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.persistit.mxbeans;

import java.rmi.RemoteException;

import com.persistit.Management;
import com.persistit.Persistit;
import com.persistit.Management.VolumeInfo;

/**
 * Interface for a service object that exposes information about the Persistit
 * environment. With this public API, embedding applications can query
 * performance metrics and resources within Persistit that are not exposed by
 * the normal access methods. For example, this class provides methods that
 * enumerate the volumes currently mounted, the size and effectiveness of buffer
 * pool components, and many other items that may be useful in tuning and
 * managing Persistit.
 * 
 * @version 1.0
 */
public interface ManagementMXBean {

    public final static String MXBEAN_NAME = "com.persistit:type=Persistit";

    /**
     * Indicate whether Persistit is currently in the initialized state.
     * 
     * @return The state
     */
    public boolean isInitialized() throws RemoteException;

    /**
     * Return the version name of the current Persistit instance.
     * 
     * @return the version name
     */
    public String getVersion() throws RemoteException;

    /**
     * Return the copyright notice for the current Persistit instance.
     * 
     * @return the copyright notice
     */
    public String getCopyright() throws RemoteException;

    /**
     * Return the system time at which Persistit was initialized.
     * 
     * @return start time, in milliseconds since January 1, 1970 00:00:00 GMT.
     */
    public long getStartTime() throws RemoteException;

    /**
     * Return the elapsed time since startup in milliseconds
     * 
     * @return elapsed time in milliseconds
     */
    public long getElapsedTime() throws RemoteException;

    /**
     * Return the port on which a local RMI server has been registered, or -1 if
     * not registered.
     * 
     * @return the port
     * @throws RemoteException
     */
    public int getRmiPort() throws RemoteException;

    /**
     * Return the aggregate number of transactions committed since Persistit was
     * initialized
     * 
     * @return total number of transactions committed
     * @throws RemoteException
     */
    public long getCommittedTransactionCount() throws RemoteException;

    /**
     * Return the aggregate number of transaction rollback events since
     * Persistit was initialized
     * 
     * @return total number of transactions rolled back
     * @throws RemoteException
     */
    public long getRollbackCount() throws RemoteException;

    /**
     * Return an array of information of all volumes that Persistit
     * knows about, or null if there are none.
     *
     * @return array of volumes
     * @throws RemoteException
     */
    public Management.VolumeInfo[] getVolumes() throws RemoteException;

    /**
     * @param max Maximum number of transactions to report on.
     * @return Report on the <code>max</code> longest-running transactions.
     * @throws RemoteException
     */
    public String transactionReport(int max) throws RemoteException;

    /**
     * Indicate whether Persistit will suspend its shutdown activities on
     * invocation of {@link com.persistit.Persistit#close}. This flag is
     * intended for use by management tools that need to keep Persistit open
     * even when the application has requested it to close so that the final
     * state of the Persistit environment can be examined.
     * 
     * @return <code>true</code> if Persistit will wait when attempting to
     *         close; <code>false</code> if the <code>close</code> operation
     *         will not be suspended.
     */
    public boolean isShutdownSuspended() throws RemoteException;

    /**
     * Control whether Persistit will suspend its shutdown activities on
     * invocation of {@link com.persistit.Persistit#close}. This flag is
     * intended for use by management tools that need to keep Persistit open
     * even when the application has requested it to close so that the final
     * state of the Persistit environment can be examined.
     * 
     * @param suspended
     *            <code>true</code> to specify that Persistit will wait when
     *            attempting to close; otherwise <code>false</code>.
     */
    public void setShutdownSuspended(boolean suspended) throws RemoteException;

    /**
     * Indicate whether Persistit is suspending all update operations. When
     * enabled, Persistit will indefinitely delay each Thread that attempts to
     * perform an update operation.
     * 
     * @return <code>true</code> if Persistit will suspend any attempt to update
     *         a <code>Volume</code>; otherwise <code>false</code>.
     * @throws RemoteException
     */
    public boolean isUpdateSuspended() throws RemoteException;

    /**
     * Control whether Persistit will suspend all update operations. When
     * enabled, Persistit will delay each Thread that attempts to perform an
     * update operation indefinitely.
     * 
     * @param suspended
     * @throws RemoteException
     */
    public void setUpdateSuspended(boolean suspended) throws RemoteException;

    /**
     * Flush and sync all dirty data in Persistit by invoking
     * {@link Persistit#flush} and {@link Persistit#force}.
     * 
     * @throws RemoteException
     */
    public void flushAndForce() throws RemoteException;

    /**
     * Use a simple command-line processor to invoke a task. Allows task
     * invocation to be scripted through a command-line JMX client. This method
     * runs the Task, which in some cases may take a long time. In contrast, the
     * {@link #launch(String)} method initiates the task in a new thread and
     * returns immediately.
     * 
     * @param commandLine
     * @return The final status of the Task, as a String
     * @throws RemoteException
     */
    public String execute(final String commandLine) throws RemoteException;

    /**
     * Use a simple command-line processor to start a task. Allows task
     * invocation to be scripted through a command-line JMX client. This method
     * starts the task in a new thread and returns immediately. In contrast, the
     * {@link #execute(String)} method completes the task before returning.
     * 
     * @param commandLine
     * @return The taskId, as a String
     * @throws RemoteException
     */
    public String launch(final String commandLine) throws RemoteException;

    /**
     * Query information about a specific volume from it's name.
     *
     * @param volumeName name of the volume to lookup
     * @return information about the volume, or null if none found
     * @throws RemoteException
     */
    public Management.VolumeInfo volumeByName(final String volumeName) throws RemoteException;
}
