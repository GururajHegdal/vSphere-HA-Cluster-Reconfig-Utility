/**
 * Utility class to add HA Cluster Advanced option and reconfigure HA on all clustered hosts - for changes
 * to take effect. And restore the HA cluster configuration, incase there is a failure.
 *
 * -- Add advanced option 'das.heartbeatDsPerHost' with value '3' [HA chooses by default 2 heartbeat datastores for
 *  each host in an HA cluster. This option can be used to increase the number to a value in the range of 2 to 5 inclusive.]
 * -- Reconfigure HA on all Clustered hosts, in a MULTI THREADED fashion. Wait until all reconfigure HA tasks complete
 * -- If there is a failure, report what all ESXi hosts reconfigure HA task failed AND
 *    Revert the configuration changes made
 *    ---- Remove added advanced option
 *    ---- Reconfigure HA on all hosts, to revert the state
 *
 * Copyright (c) 2016
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * @author Gururaja Hegdal (ghegdal@vmware.com)
 * @version 1.0
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package reconfigha;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.vim25.ClusterConfigInfo;
import com.vmware.vim25.ClusterConfigInfoEx;
import com.vmware.vim25.ClusterConfigSpecEx;
import com.vmware.vim25.ClusterDasConfigInfo;
import com.vmware.vim25.HostRuntimeInfo;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.OptionValue;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;

public class FDMConfigUpdater
{
    private String vsphereIp;
    private String userName;
    private String password;
    private String url;
    private ServiceInstance si;
    private boolean cluAdvOpAdded;
    private String clusterName;

    // VC inventory related objects
    public static final String DC_MOR_TYPE = "Datacenter";
    public static final String CLUSTER_COMPRES_MOR_TYPE = "ClusterComputeResource";
    public static final String VC_ROOT_TYPE = "VCRoot";
    public static final String HOST_MOR_TYPE = "HostSystem";
    public static final String VM_MOR_TYPE = "VirtualMachine";

    // FDM Advanced option
    private final String HA_ADV_CONFIG_OPTION = "das.heartbeatDsPerHost";
    private final String HA_ADV_CONFIG_VAL = "3";
    private boolean haAdvOptionAlreadyExists;

    /**
     * Constructor
     */
    public FDMConfigUpdater(String[] cmdProps)
    {
        makeProperties(cmdProps);
    }

    /**
     * Default constructor
     */
    public FDMConfigUpdater()
    {
        //Placeholder
    }

    /**
     * Read properties from command line arguments
     */
    private void
    makeProperties(String[] cmdProps)
    {
        // get the property value and print it out
        System.out.println("Reading vSphere IP and Credentials information from command line arguments");
        System.out.println("-------------------------------------------------------------------");

        for (int i = 0; i < cmdProps.length; i++) {
            if (cmdProps[i].equals("--vsphereip")) {
                vsphereIp = cmdProps[i + 1];
                System.out.println("vSphere IP:" + vsphereIp);
            } else if (cmdProps[i].equals("--username")) {
                userName = cmdProps[i + 1];
                System.out.println("Username:" + userName);
            } else if (cmdProps[i].equals("--password")) {
                password = cmdProps[i + 1];
                System.out.println("password: ******");
            } else if (cmdProps[i].equals("--clusterName")) {
                clusterName = cmdProps[i + 1];
                System.out.println("Cluster Name:" + clusterName);
            }
        }
        System.out.println("-------------------------------------------------------------------\n");
    }

    /**
     * Validate property values
     */
    boolean
    validateProperties()
    {
        boolean val = false;
        if (vsphereIp != null) {
            url = "https://" + vsphereIp + "/sdk";

            try {
                System.out.println("Logging into vSphere : " + vsphereIp + ", with provided credentials");
                si = loginTovSphere(url);

                if (si != null) {
                    System.out.println("Succesfully logged into vSphere: " + vsphereIp);
                    val = true;
                } else {
                    System.err.println(
                        "Service Instance object for vSphere:" + vsphereIp + " is null, probably we failed to login");
                    printFailedLoginReasons();
                }
            } catch (Exception e) {
                System.err.println(
                    "Caught an exception, while logging into vSphere :" + vsphereIp + " with provided credentials");
                printFailedLoginReasons();
            }
        } else {
            System.err.println("vSphere IP is null. See below the usage of script");
            RunApp.usage();
        }

        return val;
    }

    /**
     * Login method to VC/ESXi
     */
    private ServiceInstance
    loginTovSphere(String url)
    {
        try {
            si = new ServiceInstance(new URL(url), userName, password, true);
        } catch (Exception e) {
            System.out.println("Caught exception while logging into vSphere server");
            e.printStackTrace();
        }
        return si;
    }

    /**
     * Method prints out possible reasons for failed login
     */
    private void printFailedLoginReasons()
    {
        System.err.println(
            "Possible reasons:\n1. Provided username/password credentials are incorrect\n"
                + "2. If username/password or other fields contain special characters, surround them with double "
                + "quotes and for non-windows environment with single quotes (Refer readme doc for more information)\n"
                + "3. vCenter Server/ESXi server might not be reachable");
    }

    /**
     * Check and apply Advanced option - "das.heartbeatDsPerHost" on HA Enabled Cluster
     */
    boolean
    applyHAAdvOptionClusters()
    {
        Boolean clusterConfigSuccess = false;

        // check and retrieve HA Enabled Cluster and its hosts
        Map<ManagedEntity, List<HostSystem>> allClusterNHostsMap = retrieveHAClusterNHosts(clusterName);

        if (allClusterNHostsMap != null && allClusterNHostsMap.size() > 0) {
            ManagedEntity haCluster = allClusterNHostsMap.keySet().iterator().next();

            ClusterInfoClassForRestore oriClusterInfoObj = new ClusterInfoClassForRestore();
            List<HostSystem> clusteredHosts = allClusterNHostsMap.get(haCluster);

            ClusterComputeResource haCcr = new ClusterComputeResource(si.getServerConnection(), haCluster.getMOR());
            oriClusterInfoObj.cluster = haCluster;
            oriClusterInfoObj.hosts = clusteredHosts;
            ClusterConfigInfoEx clusterConfigInfoObj = (ClusterConfigInfoEx) haCcr.getConfigurationEx();
            oriClusterInfoObj.clusterConfigInfo = clusterConfigInfoObj;
            oriClusterInfoObj.ccr = haCcr;

            try {
                System.out.println("\n******************************************************************************");
                System.out.println("\t\t\t CLUSTER : " + clusterName);
                System.out.println("******************************************************************************");
                Thread.sleep(500);

                if (reconfigClusterWithAdvOption(haCcr, clusterConfigInfoObj)) {
                    if (!haAdvOptionAlreadyExists) {
                        System.out.println("Successfully added advanced option: \"" + HA_ADV_CONFIG_OPTION
                            + "\" to Cluster: " + clusterName);
                        cluAdvOpAdded = true;
                        System.out.println(
                            "\nTrigger Reconfigure HA operation on all clustered hosts for changes to take effect ...");
                        Map<Boolean, List<HostSystem>> reconfigHostsResultMap = reconfigureHAOnCluHosts(clusteredHosts);

                        if (reconfigHostsResultMap.keySet().contains(Boolean.TRUE)) {
                            System.out.println("Reconfigure HA on ALL clustered hosts completed");
                            clusterConfigSuccess = true;
                        } else {
                            System.err.println("[ALERT] Reconfigure HA failed on the following hosts");
                            for (HostSystem failedHostSys : reconfigHostsResultMap
                                .get(reconfigHostsResultMap.keySet().iterator().next())) {
                                System.out.println("---- " + failedHostSys.getName());
                            }

                            // Revert the configuration of cluster
                            restoreClusterConfiguration(oriClusterInfoObj);
                        }
                    } else {
                        clusterConfigSuccess = true;
                    }
                } else {
                    System.out.println(
                        "Failed in adding advanced option: \"" + HA_ADV_CONFIG_OPTION + "\" to Cluster: "
                            + clusterName);
                }

            } catch (Exception e) {
                System.err.println("Caught an exception while adding advanced option to HA cluster: " + clusterName);
                e.printStackTrace();
            }
        }

        return clusterConfigSuccess;
    }

    /**
     * Restore Cluster settings (as it was before start of the test) and reconfigure HA on hosts
     */
    private void restoreClusterConfiguration(ClusterInfoClassForRestore oriClusterInfoObj)
    {
        String cluName = oriClusterInfoObj.cluster.getName();
        System.out.println("\n* * * * * * * * RESTORE SETTINGS ON CLUSTER : " + cluName + " * * * * * * * *");

        try {
            if (cluAdvOpAdded) {
                System.out.println("Advanced option was added to cluster, revert the change ...");
                if (removeClusterAdvOption(oriClusterInfoObj.ccr, oriClusterInfoObj.clusterConfigInfo)) {
                    System.out.println("Successfully removed advanced option from cluster");
                    // Reconfigure HA on Host, for the cluster related changes to take effect
                    Map<Boolean, List<HostSystem>> reconfigHostsResultMap = reconfigureHAOnCluHosts(
                        oriClusterInfoObj.hosts);

                    if (reconfigHostsResultMap.keySet().contains(Boolean.TRUE)) {
                        System.out.println("Reconfigure HA on ALL clustered hosts completed");
                    } else {
                        System.err.println(
                            "[ALERT] Reconfigure HA failed on the following hosts. Pls check and reconfigure hosts manually");
                        for (HostSystem failedHostSys : reconfigHostsResultMap
                            .get(reconfigHostsResultMap.keySet().iterator().next())) {
                            System.out.println("---- " + failedHostSys.getName());
                        }
                    }
                } else {
                    System.err.println(
                        "[ALERT] Failed to restore Cluster settings. Pls check and revert the change manually");
                }
            } else {
                System.out.println("Advanced option was not added to cluster earlier");
            }
        } catch (Exception e) {
            System.err.println("[ALERT] Caught exception while restoring settings on Cluster: " + cluName);
        }
    }

    /**
     * All hosts from HA Enabled Cluster
     */
    Map<ManagedEntity, List<HostSystem>>
    retrieveHAClusterNHosts(String userRequestedClusterName)
    {
        Map<ManagedEntity, List<HostSystem>> allClusHostsMap = new HashMap<ManagedEntity, List<HostSystem>>();

        try {
            InventoryNavigator navigator = new InventoryNavigator(si.getRootFolder());

            ManagedEntity[] allClusters = navigator.searchManagedEntities(CLUSTER_COMPRES_MOR_TYPE);

            if (allClusters.length > 0) {
                System.out.println("Found Clusters in inventory. Check and retrieve HA Enabled Cluster");

                /*
                 * Traverse through each Cluster and find the user requested cluster
                 */
                for (ManagedEntity tempCluME : allClusters) {
                    if (tempCluME.getName().equals(userRequestedClusterName)) {
                        ClusterComputeResource ccr = new ClusterComputeResource(si.getServerConnection(),
                            tempCluME.getMOR());

                        // Check if HA is enabled on Cluster
                        ClusterConfigInfo tempCluConfigInfo = ccr.getConfiguration();
                        ClusterDasConfigInfo fdmConfigInfo = tempCluConfigInfo.getDasConfig();

                        if (fdmConfigInfo != null && fdmConfigInfo.enabled) {
                            System.out.println("HA is enabled on Cluster: " + tempCluME.getName());

                            // retrieve all hosts from the cluster
                            HostSystem[] allHosts = ccr.getHosts();
                            if (allHosts.length > 0) {
                                System.out.println("Found ESXi host(s). Check for all connected hosts");
                                List<HostSystem> activeHosts = new ArrayList<HostSystem>();
                                for (ManagedEntity tempHost : allHosts) {
                                    HostSystem tempHostSys = (HostSystem) tempHost;
                                    HostRuntimeInfo hostruntimeInfo = tempHostSys.getRuntime();
                                    if ((hostruntimeInfo.getConnectionState()
                                        .equals(HostSystemConnectionState.connected))) {
                                        System.out.println(
                                            "Found ESXi host: " + tempHostSys.getName() + " in connected state");
                                        activeHosts.add(tempHostSys);
                                    }
                                }
                                if (activeHosts.size() > 0) {
                                    allClusHostsMap.put(tempCluME, activeHosts);
                                } else {
                                    System.out.println(
                                        "Could not find any ESXi host in connected state, for this cluster: "
                                            + tempCluME.getName());
                                }
                            }
                        } else {
                            System.err
                                .println("HA is not enabled on the user provided cluster: " + userRequestedClusterName);
                        }
                    }
                } // End of clusters loop

                if (!(allClusHostsMap != null && allClusHostsMap.size() > 0)) {
                    System.err.println("Could not find Cluster: \"" + clusterName + " \"in vCenter Server inventory");
                }
            } else {
                System.err.println("Could not find any clusters in vCenter Server");
            }

        } catch (Exception e) {
            System.err.println("[Error] Unable to retrieve Clusters from inventory");
            e.printStackTrace();
        }

        return allClusHostsMap;
    }

    /**
     * Add advanced option "das.heartbeatDsPerHost" with value "3"
     */
    boolean
    reconfigClusterWithAdvOption(ClusterComputeResource haCcr, ClusterConfigInfoEx oriCluConfigInfo)
    {
        boolean reconfigSuccess = false;
        ClusterConfigSpecEx newSpec = new ClusterConfigSpecEx();

        // HA
        ClusterDasConfigInfo oriCluDasConfigInfo = oriCluConfigInfo.getDasConfig();
        OptionValue[] oriAdvancedOptions = oriCluDasConfigInfo.getOption();

        // Add advanced option, along with pre-existing advanced options
        OptionValue[] newAdvancedOptions;
        OptionValue newOptionValue = new OptionValue();
        newOptionValue.setKey(HA_ADV_CONFIG_OPTION);
        newOptionValue.setValue(HA_ADV_CONFIG_VAL);

        outer: if (oriAdvancedOptions != null) {
            newAdvancedOptions = new OptionValue[oriAdvancedOptions.length + 1];
            for (int i = 0; i < oriAdvancedOptions.length; i++) {
                // Check if the advanced option already exists
                if ((oriAdvancedOptions[i].getKey().equals(HA_ADV_CONFIG_OPTION))
                    && (oriAdvancedOptions[i].getValue().equals(HA_ADV_CONFIG_VAL))) {
                    System.out.println("Cluster already has the required advanced options added");
                    haAdvOptionAlreadyExists = true;
                    reconfigSuccess = true;
                    break outer;
                }
                newAdvancedOptions[i] = oriAdvancedOptions[i];
            }
            newAdvancedOptions[oriAdvancedOptions.length] = newOptionValue;
        } else {
            newAdvancedOptions = new OptionValue[1];
            newAdvancedOptions[0] = newOptionValue;
        }

        // If the advanced option does not exist already, proceed further
        if (!reconfigSuccess) {
            oriCluDasConfigInfo.setOption(newAdvancedOptions);
            newSpec.setDasConfig(oriCluDasConfigInfo);

            try {
                /*
                 * reconfigureComputeResource_Task(newSpec, modify)
                 * -- newSpec : A set of configuration changes to apply to the compute resource
                 * -- modify :
                 * (i) if set to "true". All SET properties from the newSpec is applied. And all UNSET property has
                 * no effect on the existing property value in the cluster configuration.
                 * (ii) if set to "faslse". All SET properties from the newSpec is applied. And all UNSET property
                 * portions of the specification will result in UNSET or default portions of the configuration.
                 *
                 * For the current case, we'll pass "true" with spec containing changes to ONLY Advanced options area.
                 * Rest all will be unset - and per the API call behavior, even after reconfig cluster call, other
                 * properties/settings/configurations (like DRS/DPM/Rules etc) would continue to exist unharmed.
                 */
                Task reconfigCluTask = haCcr.reconfigureComputeResource_Task(newSpec, true);

                // Monitor the task status
                int count = 10;
                while (count > 0) {
                    TaskInfoState taskState = reconfigCluTask.getTaskInfo().getState();
                    if (taskState.equals(TaskInfoState.queued) || taskState.equals(TaskInfoState.running)) {
                        System.out
                            .println("Cluster Reconfiguration task is still running, wait for the task to complete");
                        Thread.sleep(1000 * 2);
                        --count;
                    } else if (taskState.equals(TaskInfoState.success)) {
                        System.out.println("Reconfigure Cluster task succeeded");
                        reconfigSuccess = true;
                        cluAdvOpAdded = true;
                        break;
                    } else if (taskState.equals(TaskInfoState.error)) {
                        System.out.println("Reconfigure Cluster task Failed");
                        break;
                    }
                }

            } catch (Exception e) {
                System.err.println("Caught exception while reconfiguring cluster");
                e.printStackTrace();
            }
        }

        return reconfigSuccess;
    }

    /**
     * Remove advanced option "das.heartbeatDsPerHost" from cluster
     */
    boolean
    removeClusterAdvOption(ClusterComputeResource haCcr, ClusterConfigInfoEx oriCluConfigInfo)
    {
        boolean reconfigSuccess = false;
        ClusterConfigSpecEx newSpec = new ClusterConfigSpecEx();

        // HA
        ClusterDasConfigInfo oriCluDasConfigInfo = oriCluConfigInfo.getDasConfig();
        OptionValue[] oriAdvancedOptions = oriCluDasConfigInfo.getOption();

        if (oriAdvancedOptions != null) {
            oriCluDasConfigInfo.setOption(oriAdvancedOptions);
        } else {
            OptionValue[] newAdvancedOptions = new OptionValue[1];
            OptionValue newOptionValue = new OptionValue();
            newAdvancedOptions[0] = newOptionValue;
            oriCluDasConfigInfo.setOption(newAdvancedOptions);
        }

        newSpec.setDasConfig(oriCluDasConfigInfo);

        try {
            /*
             * reconfigureComputeResource_Task(newSpec, modify)
             * -- newSpec : A set of configuration changes to apply to the compute resource
             * -- modify :
             * (i) if set to "true". All SET properties from the newSpec is applied. And all UNSET property has
             * no effect on the existing property value in the cluster configuration.
             * (ii) if set to "faslse". All SET properties from the newSpec is applied. And all UNSET property
             * portions of the specification will result in UNSET or default portions of the configuration.
             *
             * For the current case, we'll pass "true" with spec containing changes to ONLY Advanced options area.
             * Rest all will be unset - and per the API call behavior, even after reconfig cluster call, other
             * properties/settings/configurations (like DRS/DPM/Rules etc) would continue to exist unharmed.
             */
            Task reconfigCluTask = haCcr.reconfigureComputeResource_Task(newSpec, true);

            // Monitor the task status
            int count = 10;
            while (count > 0) {
                TaskInfoState taskState = reconfigCluTask.getTaskInfo().getState();
                if (taskState.equals(TaskInfoState.queued) || taskState.equals(TaskInfoState.running)) {
                    System.out.println("Cluster Reconfiguration task is still running, wait for the task to complete");
                    Thread.sleep(1000 * 2);
                    --count;
                } else if (taskState.equals(TaskInfoState.success)) {
                    System.out.println("Reconfigure Cluster task succeeded");
                    reconfigSuccess = true;
                    cluAdvOpAdded = true;
                    break;
                } else if (taskState.equals(TaskInfoState.error)) {
                    System.out.println("Reconfigure Cluster task Failed");
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("Caught exception while reconfiguring cluster");
            e.printStackTrace();
        }

        return reconfigSuccess;
    }

    /**
     * Class to handle Host Reconfig HA tasks in threaded fashion
     */
    private class ThreadReconfigHA extends Thread
    {
        private static final int FDM_RECONFIG_TIMEOUT = 600; // 10 Minutes
        private static final int LOOP_DELAY = 20; // 20 seconds sleep for each iteration
        HostSystem hostSys;
        boolean isHostReconfigured = false;

        ThreadReconfigHA(HostSystem hostSystem) {
            hostSys = hostSystem;
        }

        @Override
        public void run()
        {
            String hostName = hostSys.getName();
            System.out.println("[" + hostName +"] Trigger Reconfig HA operation on host ...");
            try {
                Task reconfigHATask = hostSys.reconfigureHostForDAS();

                // Monitor the task status
                int count = FDM_RECONFIG_TIMEOUT / LOOP_DELAY; // 450 seconds timeout for HA Reconfig task
                while (count > 0) {
                    TaskInfoState reconfigHaTaskState = reconfigHATask.getTaskInfo().getState();
                    if (reconfigHaTaskState.equals(TaskInfoState.queued)
                        || reconfigHaTaskState.equals(TaskInfoState.running)) {
                        System.out
                            .println("[" + hostName +"] Reconfig HA task on host is still running, wait for the task to complete");
                        Thread.sleep(1000 * LOOP_DELAY);
                        --count;
                    } else if (reconfigHaTaskState.equals(TaskInfoState.success)) {
                        System.out.println("[" + hostName +"] Reconfig HA on Host task succeeded");
                        isHostReconfigured = true;
                        break;
                    } else if (reconfigHaTaskState.equals(TaskInfoState.error)) {
                        System.out.println("[" + hostName +"] Reconfig HA on Host task FAILED");
                        break;
                    }
                }

            } catch (Exception e) {
                System.err.println("[" + hostName +"] Caught exception while reconfiguring HA on host");
            }
        }
    }

    /**
     * Reconfigure HA on all ESXi hosts
     */
    private Map<Boolean, List<HostSystem>>
    reconfigureHAOnCluHosts(List<HostSystem> allHostSys)
    {
        Boolean allHostsConfigured = false;
        List<ThreadReconfigHA> allHAThreadObj = new ArrayList<ThreadReconfigHA>();
        List<HostSystem> listOfHaReconfigFailedHosts = new ArrayList<HostSystem>();

        int reconfigSuccessHostCnt = 0;

        try {
            for (HostSystem tempHostSys : allHostSys) {
                ThreadReconfigHA reconfigHAThreadObj = new ThreadReconfigHA(tempHostSys);
                reconfigHAThreadObj.start();
                allHAThreadObj.add(reconfigHAThreadObj);
            }

            // Now wait for all threads to complete
            for (ThreadReconfigHA tempReconfigThreadObj : allHAThreadObj) {
                tempReconfigThreadObj.join();
                if (tempReconfigThreadObj.isHostReconfigured) {
                    ++ reconfigSuccessHostCnt;
                } else {
                    listOfHaReconfigFailedHosts.add(tempReconfigThreadObj.hostSys);
                }
            }
        } catch (Exception e) {
            System.err.println("Caught exception while reconfiguring HA on clustered hosts");
        }

        // Check if all reconfig HA operation on hosts gone through fine
        if ((listOfHaReconfigFailedHosts.size() == 0) &&
            (reconfigSuccessHostCnt == allHostSys.size())) {
            allHostsConfigured = true;
        }

        Map <Boolean, List<HostSystem>> resultMapObj = new HashMap<Boolean, List<HostSystem>>();
        resultMapObj.put(allHostsConfigured, listOfHaReconfigFailedHosts);

        return resultMapObj;
    }

    /**
     * Class to hold the cluster configuration related information
     */
    class ClusterInfoClassForRestore
    {
        ManagedEntity cluster;
        List<HostSystem> hosts;
        ClusterConfigInfoEx clusterConfigInfo;
        ClusterComputeResource ccr;
    }
}