package com.example.auth0cleanupsb.web;

import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/batch")
public class BatchController {
  private final JobLauncher launcher;
  private final Job deleteUsersJob;

  public BatchController(JobLauncher launcher, Job deleteUsersJob) {
    this.launcher = launcher;
    this.deleteUsersJob = deleteUsersJob;
  }

  @PostMapping("/run")
  public Map<String, Object> run(@RequestParam(defaultValue = "false") boolean dryRun,
                                 @RequestParam(required = false) String inputKey) throws Exception {
    JobParameters params = new JobParametersBuilder()
        .addLong("ts", System.currentTimeMillis()) // uniqueness
        .addString("dryRun", Boolean.toString(dryRun))
        .addString("inputKey", inputKey == null ? "" : inputKey)
        .toJobParameters();
    JobExecution exec = launcher.run(deleteUsersJob, params);
    return Map.of("jobId", exec.getJobId(), "status", exec.getStatus().toString());
  }
}
