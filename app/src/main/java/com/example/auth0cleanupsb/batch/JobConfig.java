package com.example.auth0cleanupsb.batch;

import com.example.auth0cleanupsb.auth0.Auth0Client;
import com.example.auth0cleanupsb.batch.io.S3CsvResultWriter;
import com.example.auth0cleanupsb.batch.io.S3CsvUserReader;
import com.example.auth0cleanupsb.batch.model.DeleteResult;
import com.example.auth0cleanupsb.batch.model.UserDeleteRecord;
import com.example.auth0cleanupsb.batch.processor.Auth0DeleteProcessor;
import com.example.auth0cleanupsb.config.AppProperties;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.batch.repeat.policy.SimpleCompletionPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.services.s3.S3Client;



@Configuration
public class JobConfig {

  // Reader / Processor / Writer

@Bean
@StepScope  // optional but recommended
public ItemStreamReader<UserDeleteRecord> s3Reader(S3Client s3, AppProperties props) {
  return new S3CsvUserReader(s3, props, true);
}

  @Bean
  @StepScope
  public ItemProcessor<UserDeleteRecord, DeleteResult> deleteProcessor(
      Auth0Client auth0,
      // default to "false" if not provided
      @Value("#{jobParameters['dryRun'] ?: 'false'}") String dryRun) {
    return new Auth0DeleteProcessor(auth0, dryRun);
  }

@Bean
@StepScope
public ItemStreamWriter<DeleteResult> s3Writer(S3Client s3, AppProperties props) {
  return new S3CsvResultWriter(s3, props);
}

  // Step & Job (Boot provides JobRepository and TX manager via H2 datasource)

  @Bean
  public Step deleteUsersStep(JobRepository repo,
                              PlatformTransactionManager transactionManager,
                              ItemStreamReader<UserDeleteRecord> reader,
                              ItemProcessor<UserDeleteRecord, DeleteResult> processor,
                              ItemStreamWriter<DeleteResult> writer) {
    return new StepBuilder("deleteUsersStep", repo)
        .<UserDeleteRecord, DeleteResult>chunk(new SimpleCompletionPolicy(50))
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .transactionManager(transactionManager)
        .build();
  }

  @Bean
  public Job deleteUsersJob(JobRepository repo, Step deleteUsersStep) {
    return new JobBuilder("deleteUsersJob", repo)
        .start(deleteUsersStep)
        .build();
  }
}
