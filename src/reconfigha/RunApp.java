/**
 * Entry point into the vSphere HA Cluster reconfig sample
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

public class RunApp
{
    /**
     * Usage method - how to use/invoke the script, reveals the options supported through this script
     */
    public static void usage()
    {
        System.out.println(
            "Usage: java -jar fdmconfig.jar --vsphereip <vc/esxi server IP> --username <uname> --password <pwd> --clusterName <cluster name>");
        System.out.println("\nExample : To apply HA configuration on a specific cluster");
        System.out.println(
            "\"java -jar fdmconfig.jar --vsphereip 10.1.2.3 --username adminUser --password dummy --clusterName TestCluster\"");
     }

    /**
     * Main entry point into the Script
     */
    public static void main(String[] args) {

        System.out
            .println("######################### Cluster Configuration Script execution STARTED #########################");

        // Read command line arguments
        if (args.length > 0 && args.length > 6) {
            FDMConfigUpdater fdmConfigSample = new FDMConfigUpdater(args);
            if (fdmConfigSample.validateProperties()) {
                if(fdmConfigSample.applyHAAdvOptionClusters()) {
                    System.out
                    .println("Successful in adding Advanced option to HA cluster and reconfiguring HA on all cluster hosts");
                }
            }
        } else {
            usage();
        }

        try {
            Thread.sleep(1000 * 2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(
            "######################### Cluster Configuration Script execution completed #########################");
    }
}