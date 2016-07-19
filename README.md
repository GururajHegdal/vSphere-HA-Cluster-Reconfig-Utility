# vSphere HA Cluster (& ESXi hosts) Reconfiguration utility
### 1. Details
Utility to add vSphere HA Cluster Advanced option and reconfigure HA on all clustered hosts - for changes to take effect. 
And restore the HA cluster configuration, incase there is a failure.
 * Illustrates how to reconfigure HA Cluster to ADD specified advanced option, if it does not exist already.
 * Illustrates multi threaded way of reconfiguring HA on all Clustered ESXi hosts and tracking the reconfiguration task until completion.
 * Illustrates how to reconfigure HA Cluster to REMOVE specified advanced option

Flow through of the solution:
 * Connect to provided vCenter Server and Retrieve all Clusters. Check if user provided cluster exists and if vSphere HA is enabled on cluster.
 * Check and Add advanced option 'das.heartbeatDsPerHost' with value '3' [HA chooses by default 2 heartbeat datastores for
  each host in an HA cluster. This option can be used to increase the number to a value in the range of 2 to 5 inclusive.]
 * Reconfigure HA on all Clustered hosts, in a MULTI THREADED fashion. Wait until all reconfigure HA tasks complete
 * If there is a failure, report what all ESXi hosts reconfigure HA task failed AND
 * Revert the configuration changes made (i) Remove added advanced option from Cluster (ii) Reconfigure HA on all hosts, to revert the state

### 2. How to run the Utility?
##### Run from Dev IDE

 * Import files under the src/reconfigha/ folder into your IDE.
 * Required libraries are embedded within Runnable-Jar/fdmconfig.jar, extract & import the libraries into the project.
 *  Run the utility from 'RunApp' program by providing arguments like:  
 _--vsphereip 192.168.10.1 --username adminUser --password dummyPasswd --clusterName GuruCluster_


##### Run from Pre-built Jars
 * Copy/Download the fdmconfig.jar from Runnable-jar folder (from the uploaded file) and unzip on to local drive folder say c:\fdmconfig
 * Open a command prompt and cd to the folder, lets say cd fdmconfig
 * Run a command like shown below to see various usage commands:  
 _C:\fdmconfig>java -jar fdmconfig.jar --help_
 
### 3. Sample output
```
######################### Cluster Configuration Script execution STARTED #########################
Reading vSphere IP and Credentials information from command line arguments
-------------------------------------------------------------------
vSphere IP: 192.168.10.1
Username:adminUser
password: ******
Cluster Name:GuruCluster
-------------------------------------------------------------------

Logging into vSphere : 192.168.10.1, with provided credentials
Succesfully logged into vSphere: 192.168.10.1
Found Clusters in inventory. Check and retrieve HA Enabled Cluster
HA is enabled on Cluster: GuruCluster
Found ESXi host(s). Check for all connected hosts
Found ESXi host: 192.168.10.22 in connected state
Found ESXi host: 192.168.10.33 in connected state

******************************************************************************
			 CLUSTER : GuruCluster
******************************************************************************
Cluster Reconfiguration task is still running, wait for the task to complete
Reconfigure Cluster task succeeded
Successfully added advanced option: "das.heartbeatDsPerHost" to Cluster: GuruCluster

Trigger Reconfigure HA operation on all clustered hosts for changes to take effect ...
[192.168.10.22] Trigger Reconfig HA operation on host ...
[192.168.10.22] Reconfig HA task on host is still running, wait for the task to complete
[192.168.10.33] Trigger Reconfig HA operation on host ...
[192.168.10.33] Reconfig HA task on host is still running, wait for the task to complete
[192.168.10.22] Reconfig HA task on host is still running, wait for the task to complete
[192.168.10.33] Reconfig HA task on host is still running, wait for the task to complete
[192.168.10.22] Reconfig HA on Host task succeeded
[192.168.10.33] Reconfig HA on Host task succeeded
Reconfigure HA on ALL clustered hosts completed
Successful in adding Advanced option to HA cluster and reconfiguring HA on all cluster hosts
######################### Cluster Configuration Script execution completed #########################
```
