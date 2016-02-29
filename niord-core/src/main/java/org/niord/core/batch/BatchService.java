/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang.StringUtils;
import org.niord.core.batch.vo.BatchExecutionVo;
import org.niord.core.batch.vo.BatchInstanceVo;
import org.niord.core.batch.vo.BatchStatusVo;
import org.niord.core.batch.vo.BatchTypeVo;
import org.niord.core.sequence.DefaultSequence;
import org.niord.core.sequence.Sequence;
import org.niord.core.sequence.SequenceService;
import org.niord.core.service.BaseService;
import org.niord.core.service.UserService;
import org.niord.core.util.JsonUtils;
import org.niord.model.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.operations.NoSuchJobException;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Provides an interface for managing batch jobs.
 * <p>
 * Batch jobs have up to three associated batch job folders under the "batchRoot" path:
 * <ul>
 *     <li>
 *         <b>[jobName]/in</b>:
 *         The <b>in</b> folders will be monitored periodically, and any file placed in this folder will
 *         result in the <b>jobName</b> batch job being executed.
 *     </li>
 *     <li>
 *         <b>[jobName]/execution/[year]/[month]/[jobNo]</b>:
 *         Stores any input file associated with a batch job along with log files for the executions steps.
 *         These directories will be cleaned up after a configurable amount of time.
 *     </li>
 *     <li>
 *         <b>[jobName]/out</b>:
 *         Any file-based result from the execution of a batch job can be placed here.
 *         Not implemented yet.
 *     </li>
 * </ul>
 *
 * <p>
 * Note to self: Currently, there is a bug in Wildfly, so that the batch job xml files cannot be loaded
 * from an included jar.
 * Hence, move the xml files to META-INF/batch-jobs of the web application you are working on.
 *
 * @see <a hreg="https://issues.jboss.org/browse/WFLY-4988">Error report</a>
 * @see <a hreg="https://github.com/NiordOrg/niord-dk/tree/master/niord-dk-web">Example solution</a>
 */
@Stateless
@SuppressWarnings("unused")
public class BatchService extends BaseService {

    public static final String BATCH_REPO_FOLDER = "batch";

    @Inject
    private Logger log;

    @Inject
    UserService userService;

    @Inject
    JobOperator jobOperator;

    @Inject
    SequenceService sequenceService;

    //@Inject
    //@Setting(value = "repoRootPath", defaultValue = "${user.home}/.niord/batch-jobs", substituteSystemProperties = true)
    Path batchRoot = Paths.get(System.getProperty("user.home") + "/.niord/batch-jobs");

    /**
     * Starts a new batch job
     *
     * @param job the batch job name
     */
    public long startBatchJob(BatchData job) {

        // Note to self:
        // There are some transaction issues with storing the BatchData in the current transaction,
        // so, we leave it to the JobStartBatchlet batch step.

        // Launch the batch job
        Properties props = new Properties();
        props.put(IBatchable.BATCH_JOB_ENTITY, job);
        long executionId = jobOperator.start(job.getJobName(), props);

        log.info("Started batch job: " + job);
        return executionId;
    }

    /** Creates and initializes a new batch job data entity */
    private BatchData initBatchData(String jobName, Properties properties) throws IOException {
        // Construct a new batch data entity
        BatchData job = new BatchData();
        job.setUser(userService.currentUser());
        job.setJobName(jobName);
        job.setJobNo(getNextJobNo(jobName));
        job.writeProperties(properties);
        return job;
    }

    /**
     * Starts a new batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJobWithDeflatedData(String jobName, Object data, String dataFileName, Properties properties) throws Exception {

        BatchData job = initBatchData(jobName, properties);

        if (data != null) {
            dataFileName = StringUtils.isNotBlank(dataFileName) ? dataFileName : "batch-data.zip";
            job.setDataFileName(dataFileName);
            Path path = batchRoot.resolve(job.computeDataFilePath());
            createDirectories(path.getParent());
            try (FileOutputStream file = new FileOutputStream(path.toFile());
                 GZIPOutputStream gzipOut = new GZIPOutputStream(file);
                 ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut)) {
                objectOut.writeObject(data);
            }
        }

        return startBatchJob(job);
    }


    /**
     * Starts a new batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJobWithJsonData(String jobName, Object data, String dataFileName, Properties properties) throws Exception {

        BatchData job = initBatchData(jobName, properties);

        if (data != null) {
            dataFileName = StringUtils.isNotBlank(dataFileName) ? dataFileName : "batch-data.json";
            job.setDataFileName(dataFileName);
            Path path = batchRoot.resolve(job.computeDataFilePath());
            createDirectories(path.getParent());
            JsonUtils.writeJson(data, path);
        }

        return startBatchJob(job);
    }


    /**
     * Starts a new file-based batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJobWithDataFile(String jobName, InputStream in, String dataFileName, Properties properties) throws IOException {

        BatchData job = initBatchData(jobName, properties);

        if (in != null) {
            job.setDataFileName(dataFileName);
            Path path = batchRoot.resolve(job.computeDataFilePath());
            createDirectories(path.getParent());
            Files.copy(in, path);
        }

        return startBatchJob(job);
    }


    /**
     * Starts a new file-based batch job
     *
     * @param jobName the batch job name
     */
    public long startBatchJobWithDataFile(String jobName, Path file, Properties properties) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Invalid file " + file);
        }

        try (InputStream in = new FileInputStream(file.toFile())) {
            return startBatchJobWithDataFile(
                    jobName,
                    in,
                    file.getFileName().toString(),
                    properties);
        }
    }


    /**
     * Returns the next sequence number for the batch job
     * @param jobName the name of the batch job
     * @return the next sequence number for the batch job
     */
    private Long getNextJobNo(String jobName) {
        Sequence jobSequence = new DefaultSequence("BATCH_JOB_" + jobName, 1);
        return sequenceService.getNextValue(jobSequence);
    }


    /** Creates the given directories if they do not exist */
    private Path createDirectories(Path path) throws IOException {
        if (path != null && !Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }


    /**
     * Returns the data file associated with the given batch job instance.
     * Returns null if no data file is found
     *
     * @param instanceId the batch job instance
     * @return the data file associated with the given batch job instance.
     */
    public Path getBatchJobDataFile(Long instanceId) {
        BatchData job = findByInstanceId(instanceId);

        if (job == null || job.getDataFileName() == null) {
            return null;
        }

        Path path = batchRoot.resolve(job.computeDataFilePath());
        return Files.isRegularFile(path) ? path : null;
    }


    /**
     * Loads the batch job JSON data file as the given class.
     * Returns null if no data file is found
     *
     * @param instanceId the batch job instance
     * @return the data
     */
    public <T> T readBatchJobJsonDataFile(Long instanceId, Class<T> dataClass) throws IOException {
        Path path = getBatchJobDataFile(instanceId);

        return path != null ? JsonUtils.readJson(dataClass, path) : null;
    }


    /**
     * Loads the batch job JSON data file as the given class.
     * Returns null if no data file is found
     *
     * @param instanceId the batch job instance
     * @return the data
     */
    public <T> T readBatchJobJsonDataFile(Long instanceId, TypeReference typeRef) throws IOException {
        Path path = getBatchJobDataFile(instanceId);

        return path != null ? JsonUtils.readJson(typeRef, path) : null;
    }


    /**
     * Loads the batch job deflated data file as the given class.
     * Returns null if no data file is found
     *
     * @param instanceId the batch job instance
     * @return the data
     */
    @SuppressWarnings("all")
    public <T> T readBatchJobDeflatedDataFile(Long instanceId) throws IOException, ClassNotFoundException {
        Path path = getBatchJobDataFile(instanceId);

        if (path != null) {
            try (FileInputStream file = new FileInputStream(path.toFile());
                 GZIPInputStream gzipIn = new GZIPInputStream(file);
                 ObjectInputStream objectIn = new ObjectInputStream(gzipIn)) {
                return (T) objectIn.readObject();
            }
        }

        return null;
    }


    /**
     * Returns the batch data entity with the given instance id.
     * Returns null if no batch data entity is not found.
     *
     * @param instanceId the instance id
     * @return the batch data entity with the given instance id
     */
    public BatchData findByInstanceId(Long instanceId) {
        try {
            return em.createNamedQuery("BatchData.findByInstanceId", BatchData.class)
                    .setParameter("instanceId", instanceId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the batch data entities with the given instance ids.
     *
     * @param instanceIds the instance ids
     * @return the batch data entities with the given instance ids
     */
    public List<BatchData> findByInstanceIds(Set<Long> instanceIds) {
        return em.createNamedQuery("BatchData.findByInstanceIds", BatchData.class)
                .setParameter("instanceIds", instanceIds)
                .getResultList();
    }


    /**
     * Creates or updates the batch job.
     *
     * @param job the batch job entity
     */
    public BatchData saveBatchJob(BatchData job) {
        Objects.requireNonNull(job, "Invalid job parameter");
        Objects.requireNonNull(job.getInstanceId(), "Invalid job instance ID");

        job = saveEntity(job);
        em.flush();
        return job;
    }

    /**
     * Returns the batch job names
     * @return the batch job names
     */
    @SuppressWarnings("all")
    public List<String> getJobNames() {
        // Sadly, this gets reset upon every JVM restart
        /*
        return jobOperator.getJobNames()
                .stream()
                .sorted()
                .collect(Collectors.toList());
        */

        // Look up the names from the database
        return (List<String>)em
                .createNativeQuery("select distinct JOBNAME from JOB_INSTANCE order by lower(JOBNAME)")
                .getResultList();
    }


    /**
     * Stops the given batch job execution
     *
     * @param executionId the execution ID
     */
    public void stopExecution(long executionId) {
        jobOperator.stop(executionId);
    }


    /**
     * Restarts the given batch job execution
     *
     * @param executionId the execution ID
     */
    public long restartExecution(long executionId) {
        return jobOperator.restart(executionId, new Properties());
    }

    /**
     * Abandons the given batch job execution
     *
     * @param executionId the execution ID
     */
    public void abandonExecution(long executionId) {
        jobOperator.abandon(executionId);
    }

    /**
     * Returns the paged search result for the given batch type
     * @param jobName the job name
     * @param start the start index of the paged search result
     * @param count the max number of instances per start
     * @return the paged search result for the given batch type
     */
    public PagedSearchResultVo<BatchInstanceVo> getJobInstances(
           String jobName, int start, int count) throws Exception {

        Objects.requireNonNull(jobName);
        PagedSearchResultVo<BatchInstanceVo> result = new PagedSearchResultVo<>();

        result.setTotal(jobOperator.getJobInstanceCount(jobName));

        jobOperator.getJobInstances(jobName, start, count).forEach(i -> {
            BatchInstanceVo instance = new BatchInstanceVo();
            instance.setName(i.getJobName());
            instance.setInstanceId(i.getInstanceId());
            result.getData().add(instance);
            jobOperator.getJobExecutions(i).forEach(e -> {
                BatchExecutionVo execution = new BatchExecutionVo();
                execution.setExecutionId(e.getExecutionId());
                execution.setBatchStatus(e.getBatchStatus());
                execution.setStartTime(e.getStartTime());
                execution.setEndTime(e.getEndTime());
                instance.getExecutions().add(execution);
            });
            instance.updateExecutions();
        });

        // Next, copy the associated batch data to the instance VO
        Set<Long> instanceIds = result.getData().stream()
                .map(BatchInstanceVo::getInstanceId).collect(Collectors.toSet());
        Map<Long, BatchData> batchDataLookup = findByInstanceIds(instanceIds)
                .stream()
                .collect(Collectors.toMap(BatchData::getInstanceId, Function.identity()));
        for (BatchInstanceVo i : result.getData()) {
            BatchData data = batchDataLookup.get(i.getInstanceId());
            if (data != null) {
                i.setFileName(data.getDataFileName());
                i.setJobNo(data.getJobNo());
                i.setUser(data.getUser() != null ? data.getUser().getName() : null);
                i.setJobName(data.getJobName());
                i.setProperties(data.readProperties());
            }
        }

        result.updateSize();
        return result;
    }


    /**
     * Returns the status of the batch job system
     * @return the status of the batch job system
     */
    public BatchStatusVo getStatus() {
        BatchStatusVo status = new BatchStatusVo();
        getJobNames().forEach(name -> {
            // Create a status for the batch type
            BatchTypeVo batchType = new BatchTypeVo();
            batchType.setName(name);
            try {
                batchType.setRunningExecutions(jobOperator.getRunningExecutions(name).size());
            } catch (NoSuchJobException ignored) {
                // When the JVM has restarted the call will fail until the job has executed the first time.
                // A truly annoying behaviour, given that we use persisted batch jobs.
            }
            batchType.setInstanceCount(jobOperator.getJobInstanceCount(name));

            // Update the global batch status with the batch type
            status.getTypes().add(batchType);
            status.setRunningExecutions(status.getRunningExecutions() + batchType.getRunningExecutions());
        });
        return status;
    }

}