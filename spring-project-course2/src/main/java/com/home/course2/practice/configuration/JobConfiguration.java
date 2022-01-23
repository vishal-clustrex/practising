package com.home.course2.practice.configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import com.home.course2.config.AirlineConfiguration;
import com.home.course2.practice.configuration.airline.Airline;
import com.home.course2.simulator.AirlineSearchResultWriter;
import com.home.course2.simulator.SimulatorResponseDto;
import com.home.course2.utils.CourseUtilJobSummaryListener;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class JobConfiguration {

    private static final Logger LOGGER = LogManager.getLogger(JobConfiguration.class);

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final ApplicationContext applicationContext;

    public JobConfiguration(JobBuilderFactory jobBuilderFactory,
                            StepBuilderFactory stepBuilderFactory,
                            ApplicationContext applicationContext) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.applicationContext = applicationContext;
    }

    @Bean
    public Job job() throws Exception {
        return jobBuilderFactory.get("airlineSearchJob")
                .start(searchFlow()).end()
                .listener(new CourseUtilJobSummaryListener())
                .build();
    }


    @Bean
    public Step requestOceanicStep() {
        return requestAirlineReaderStep(Airline.OCEANIC);
    }

    @Bean
    public Step requestBelarusStep() {
        return requestAirlineReaderStep(Airline.BELARUS);
    }

    @Bean(value = "requestTransamericanStep")
    public Step requestTransamericanStep() {
        return requestAirlineReaderStep(Airline.TRANS_AMERICAN);
    }

    @Bean
    public Step requestSouthPacificStep() {
        return requestAirlineReaderStep(Airline.SOUTH_PACIFIC);
    }

    @Bean
    public Step requestAdiosStep() {
        return requestAirlineReaderStep(Airline.ADIOS);
    }

    @Bean
    public Step requestFlyUsStep() {
        return requestAirlineReaderStep(Airline.FLY_US);
    }

    @SuppressWarnings("unchecked")
    private Step requestAirlineReaderStep(Airline airline) {
        String beanName = airline.getReaderBeanName();
        ItemReader<SimulatorResponseDto> reader = applicationContext.getBean(beanName, ItemReader.class);
        return stepBuilderFactory.get("request_" + airline)
                .<SimulatorResponseDto, SimulatorResponseDto>chunk(1)
                .reader(reader)
                .writer(airlineSearchResultWriter())
                .build();
    }
    
    @Bean
    public Flow searchFlow() throws Exception {
    	ProviderDecider decider = new ProviderDecider();
    	return new FlowBuilder<SimpleFlow>("searchFlow")
    			.start(decider)
    			.on("DUBAI_AMSTERDUM_OFFER_ONLY")
    			.to(dubaiAmsterdamOffer())
    			.from(decider)
    			.on("DEFAULT_SEARCH")
    			.to(searchAirlineFlow())
    			.from(decider)
    			.on("NEWYORK_AMSTERDUM_OFFER_ONLY")
    			.to(newYorkAmsterdamOffer())
    			.next(searchAirlineFlow())
    			.build();
    }

    public Flow searchAirlineFlow() throws Exception{
    	Flow flow1 = airlineFlow("adios", requestAdiosStep());
    	Flow flow2 = airlineFlow("flyUs", requestFlyUsStep());
    	Flow flow3 = airlineFlow("belarus", requestBelarusStep());
    	Flow flow4 = airlineFlow("oceanic", requestOceanicStep());
    	Flow flow5 = airlineFlow("southPacific", requestSouthPacificStep());
    	Flow flow6 = airlineFlow("transamerican", requestTransamericanStep());
    	
    	return new FlowBuilder<SimpleFlow>("searchAirlineFlow")
    			.split(taskExecutor())
    			.add(flow1, flow2, flow3, flow4, flow5, flow6)
    			.build();
    }
    
    @Bean
    public TaskExecutor taskExecutor() {
    	SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
    	taskExecutor.setConcurrencyLimit(10);
    	return taskExecutor;
    }
    
    private Flow airlineFlow(String flowName, Step step) throws Exception {
    	SimpleFlow flow = new FlowBuilder<SimpleFlow>(flowName)
    			.start(step)
    			.build();
    	flow.afterPropertiesSet();
    	return flow;
    }
    
    @Bean
    @StepScope
    public AirlineSearchResultWriter airlineSearchResultWriter() {
        return new AirlineSearchResultWriter();
    }

    @Bean
    public Step newYorkAmsterdamOffer() {
        return stepBuilderFactory.get("newYorkAmsterdamOffer")
                .tasklet(applicationContext.getBean(
                        AirlineConfiguration.NEW_YORK_AMSTERDAM_OFFER_TASKLET, Tasklet.class
                )).build();
    }

    @Bean
    public Step dubaiAmsterdamOffer() {
        return stepBuilderFactory.get("dubaiAmsterdamOffer")
                .tasklet(applicationContext.getBean(
                        AirlineConfiguration.DUBAI_AMSTERDAM_OFFER_TASKLET, Tasklet.class
                )).build();
    }
}