package cws.core;

import java.util.LinkedList;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import cws.core.transfer.Port;

/**
 * A VM is a virtual machine that executes Jobs.
 * 
 * It has a number of cores, and each core has a certain power measured
 * in MIPS (millions of instructions per second).
 * 
 * It has an input Port that is used to transfer data to the VM, and an output
 * Port that is used to transfer data from the VM. Both ports have the same
 * bandwidth.
 * 
 * Jobs can be queued and are executed in FIFO order. The scheduling is
 * space shared.
 * 
 * It has a price per hour. The cost of a VM is computed by multiplying the
 * runtime in hours by the hourly price. The runtime is rounded up to the
 * nearest hour for this calculation.
 * 
 * Each VM has a provisioning delay between when it is launched and when it
 * is ready, and a deprovisioning delay between when it is terminated and 
 * when the provider stops charging for it.
 *
 * @author Gideon Juve <juve@usc.edu>
 */
public class VM extends SimEntity implements WorkflowEvent {
    
    private static int next_id = 0;
    
    public static final double DEFAULT_PROVISIONING_DELAY = 60.0;
    
    public static final double DEFAULT_DEPROVISIONING_DELAY = 10.0;
    
    /** How many seconds there are in one hour */
    public static final double SECONDS_PER_HOUR = 60*60;
    
    /** The SimEntity that owns this VM */
    private int owner;
    
    /** The Cloud that runs this VM */
    private int cloud;
    
    /** The processing power of this VM */
    private int mips;
    
    /** The number of cores of this VM */
    private int cores;
    
    /** Network port for input data */
    private Port inputPort;
    
    /** Network port for output data */
    private Port outputPort;
    
    /** Current idle cores */
    private int idleCores;
    
    /** Queue of jobs submitted to this VM */
    private LinkedList<Job> jobs;
    
    /** Time that the VM was launched */
    private double launchTime;
    
    /** Time that the VM was terminated */
    private double terminateTime;
    
    /** Price per hour of usage */
    private double price;
    
    /** Is this VM running? */
    private boolean running;
    
    /** Number of CPU seconds consumed by jobs on this VM */
    private double cpuSecondsConsumed;
    
    /** Delay from when the VM is launched until it is ready */
    private double provisioningDelay = DEFAULT_PROVISIONING_DELAY;
    
    /** Delay from when the VM is terminated until it is no longer charged */
    private double deprovisioningDelay = DEFAULT_DEPROVISIONING_DELAY;
    
    public VM(int mips, int cores, double bandwidth, double price) {
        super("VM"+(next_id++));
        this.mips = mips;
        this.cores = cores;
        this.inputPort = new Port(bandwidth);
        this.outputPort = new Port(bandwidth);
        this.jobs = new LinkedList<Job>();
        this.idleCores = cores;
        this.launchTime = -1.0;
        this.terminateTime = -1.0;
        this.price = price;
        this.running = false;
        this.cpuSecondsConsumed = 0.0;
        CloudSim.addEntity(this);
    }
    
    public void setDeprovisioningDelay(double deprovisioningDelay) {
        this.deprovisioningDelay = deprovisioningDelay;
    }
    
    public double getDeprovisioningDelay() {
        return deprovisioningDelay;
    }
    
    public void setProvisioningDelay(double provisioningDelay) {
        this.provisioningDelay = provisioningDelay;
    }
    
    public double getProvisioningDelay() {
        return provisioningDelay;
    }
    
    public int getOwner() {
        return owner;
    }
    
    public void setOwner(int owner) {
        this.owner = owner;
    }
    
    public int getCloud() {
        return cloud;
    }
    
    public void setCloud(int cloud) {
        this.cloud = cloud;
    }
    
    public Port getInputPort(){
        return this.inputPort;
    }

    public Port getOutputPort() {
        return this.outputPort;
    }
    
    public int getMIPS() {
        return this.mips;
    }
    
    public int getCores() {
        return cores;
    }
    
    public int getIdleCores() {
        return idleCores;
    }
    
    public Job[] getQueuedJobs() {
        return jobs.toArray(new Job[0]);
    }
    
    public int getQueueLength() {
        return jobs.size();
    }
    
    public void setLaunchTime(double launchTime) {
        this.launchTime = launchTime;
    }
    
    public double getLaunchTime() {
        return launchTime;
    }
    
    public void setTerminateTime(double terminateTime) {
        this.terminateTime = terminateTime;
    }
    
    public double getTerminateTime() {
        return terminateTime;
    }
    
    public void setPrice(double price) {
        this.price = price;
    }
    
    public double getPrice() {
        return price;
    }
    
    /** 
     * Runtime of the VM in seconds. If the VM has not been launched, then
     * the result is 0. If the VM is not terminated, then we use the current
     * simulation time as the termination time. After the VM is terminated
     * the runtime does not change.
     */
    public double getRuntime() {
        if (launchTime < 0)
            return 0.0;
        else if (terminateTime < 0)
            return CloudSim.clock() - launchTime;
        else 
            return terminateTime - launchTime;
    }
    
    /**
     * Compute the total cost of this VM. This is computed by taking the
     * runtime, rounding it up to the nearest whole hour, and multiplying
     * by the hourly price.
     */
    public double getCost() {
        double hours = getRuntime() / SECONDS_PER_HOUR;
        hours = Math.ceil(hours);
        return hours * price;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public double getCPUSecondsConsumed() {
        return cpuSecondsConsumed;
    }
    
    /** cpu_seconds / (runtime * cores) */
    public double getUtilization() {
        double totalCPUSeconds = getRuntime() * cores;
        return cpuSecondsConsumed / totalCPUSeconds;
    }
    
    @Override
    public void startEntity() {
        // Do Nothing
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case VM_LAUNCH:
                launchVM();
                break;
            case VM_TERMINATE:
                terminateVM();
                break;
            case JOB_SUBMIT:
                queueJob(ev.getSource(), (Job)ev.getData());
                break;
            case JOB_FINISHED:
                finishJob((Job)ev.getData());
                break;
            default:
                throw new RuntimeException("Unknown event: "+ev);
        }
    }

    @Override
    public void shutdownEntity() {
        // Do Nothing
    }
    
    private void launchVM() {
        // Reset dynamic state
        jobs.clear();
        idleCores = cores;
        cpuSecondsConsumed = 0.0;
        
        // VM can now accept jobs
        running = true;
    }
    
    private void terminateVM() {
        // Can no longer accept jobs 
        running = false;
        
        // Fail any queued jobs
        for (Job job : jobs) {
            job.setState(Job.State.FAILURE);
            sendNow(job.getOwner(), JOB_FINISHED, job);
        }
        
        // Reset dynamic state
        jobs.clear();
        idleCores = cores;
    }
    
    private void queueJob(int owner, Job job) {
        // Sanity check
        if (!running) {
            throw new RuntimeException("Cannot execute jobs: VM not running");
        }
        
        job.setOwner(owner);
        job.setVM(this);
        job.setRemoteQueueTime(CloudSim.clock());
        job.setState(Job.State.QUEUED);
        
        // Queue the job
        jobs.add(job);
        
        // This shouldn't do anything if the VM is busy
        startJobs();
    }
    
    private void startJob(Job job) {
        // The job is now running
        job.setStartTime(CloudSim.clock());
        job.setState(Job.State.RUNNING);
        
        // Tell the owner
        send(job.getOwner(), 0.0, JOB_STARTED, job);
        
        // Compute the duration of the job on this VM
        int size = job.getSize();
        double duration = size*1.0 / mips;
        send(getId(), duration, JOB_FINISHED, job);
        
        // One core is now busy running the job
        idleCores--;
    }
    
    private void finishJob(Job job) {
        // Dequeue the job
        jobs.remove(job);
        
        // Complete the job
        job.setFinishTime(CloudSim.clock());
        job.setState(Job.State.SUCCESS);
        
        // Increment the usage
        cpuSecondsConsumed += job.getDuration();
        
        // Tell the owner
        send(job.getOwner(), 0.0, JOB_FINISHED, job);
        
        // The core that was running the job is now free
        idleCores++;
        
        // We may be able to start more jobs now
        startJobs();
    }
    
    private void startJobs() {
        // While there are still idle jobs and cores
        while(jobs.size() > 0 && idleCores > 0) {
            // Start the next job in the queue
            startJob(jobs.poll());
        }
    }
}