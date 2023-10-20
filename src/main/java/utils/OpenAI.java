package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.core.credential.KeyCredential;

import datatypes.JobList;
import jobFinder.Paths;

public class OpenAI {
	private static final Logger log = LogManager.getLogger("com.jobFinder");
	private static String myApiKey = null;
	private static String model = null;
	private static String resume = null;
	private static String[] systemRoles = new String[3];
	private static OpenAIClient client = null;

	private static String refinedResume = null;
	public static boolean initialized = false;
	private static boolean run = false;
	private static boolean role3 = false;
	// how many requests per minute we'll send to chatGPT
	private static boolean freeKey = true;
	private static int rpm = 3;
	private static Semaphore semaphore = null;
	private static int totalPermits = 1; // permits per timeFrame
	private static AtomicInteger counter = new AtomicInteger(0);
	private static int totalJobs=0;
	
	
	public static boolean initOpenAI() {
		if (initialized) {
			return true;
		}
		LinkedHashMap<String, String> dic = LoadSettings.getPairs(NamedRanges.gpt);
		myApiKey = dic.get("API_key");
		model = dic.get("GPT_model");
		run = dic.get("GPT_switch").equals("on") ? true : false;
		freeKey = dic.get("GPT_freeKey").equals("Yes") ? true : false;
		// for freeKey we limit rpm to 3 requests
		rpm = freeKey == true ? 3 : 300;
		semaphore = new Semaphore(0);
		systemRoles[0] = dic.get("GPT_role1");
		systemRoles[1] = dic.get("GPT_role2");
		systemRoles[2] = dic.get("GPT_role3");
		role3 = dic.get("GPT_role3_switch").equals("on") ? true : false;
		resume = dic.get("Resume");
		if (resume.isBlank() | myApiKey.isBlank()) {
			log.error("Empty API or Resume");
			return false;
		}
		if (!run) {
			log.info("OpenAI is disabled in settings");
			return false;
		}
		client = new OpenAIClientBuilder().credential(new KeyCredential(myApiKey)).buildClient();
		initialized = true;
		totalJobs = Storage.generalJobList.size();
		return true;
	}

	public static void assessJobList() {
		if (!initOpenAI()) {
			return;
		}

		// timer will call releaseSemaphore() each timeFrame
		long timeFrame = 61000 / rpm;
		ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);
		timer.scheduleAtFixedRate(() -> releaseSemaphore(), timeFrame, timeFrame, TimeUnit.MILLISECONDS);

		// creating a copy of generralJobList
		List<JobList> list = new ArrayList<>(Storage.generalJobList);
		Storage.generalJobList.clear();

		// Creating a list of chunks and its temporary verison
		List<List<JobList>> chunks = new ArrayList<>();

		int chunkSize = (list.size() + 10) / 10; // items per chunk
		if(freeKey)
			chunkSize=list.size();

		// splitting initial list into several sublists (chunks);
		for (int i = 0; i < list.size(); i += chunkSize) {
			int endIndex = Math.min(i + chunkSize, list.size());
			chunks.add(list.subList(i, endIndex));
		}

		// creating pool of virtual threads
		// one thread for each element in the chunks list
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < chunks.size(); i++) {
			final int j = i;
			Thread virtualThread = Thread.ofVirtual().start(() -> {
				List<JobList> tmp = chunks.get(j).stream().map(job -> assessJob(job)).collect(Collectors.toList());
				chunks.set(j, tmp); //writing back modified chunk
			});
			threads.add(virtualThread);
		}

		// join threads
		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				log.error(e.toString());
			}
		}
		
		// transforming list of processed chunks back into whole list
		list = chunks.stream().flatMap(List::stream).collect(Collectors.toList());

		// writing results back to generalJobList
		Storage.generalJobList.addAll(list);
		
		// removing bad results from the list
		Storage.generalJobList.removeAll(Storage.generalBadJobList);
		timer.shutdown();

		log.info("Assessment finished.");
	}

	private static JobList assessJob(JobList job) {
		List<ChatMessage> chatMessages = new ArrayList<>();

		// we creating clone just to not modify source of the stream
		JobList clone = new JobList(job); // clone of the item

		// Step 0. checking the card
		
		if (clone.jobDescription==null) {
			clone.declineReason = "Empty description";
			synchronized(Storage.generalBadJobList) {
			Storage.generalBadJobList.add(clone);
			return clone;
			}
		}
		else if (clone.jobDescription.isBlank()) {
			clone.declineReason = "Empty description";
			synchronized(Storage.generalBadJobList) {
			Storage.generalBadJobList.add(clone);
			return clone;
			}
		}
		// Step 1. Getting job requirements from job description
		chatMessages.clear();
		chatMessages.add(new ChatMessage(ChatRole.SYSTEM, systemRoles[0]));
		chatMessages.add(new ChatMessage(ChatRole.USER, clone.jobDescription));

		log.info("Processing " + counter.incrementAndGet() + " out of " + totalJobs);
		String jobRequirements = askGPT(chatMessages, 250);
		
		// Step 2. Compare job requirements with candidate's resume
		chatMessages.clear();
		chatMessages.add(new ChatMessage(ChatRole.SYSTEM, systemRoles[1]));
		chatMessages.add(new ChatMessage(ChatRole.USER, jobRequirements));
		chatMessages.add(new ChatMessage(ChatRole.USER, "Candidate's resume:\n" + resume));
		// log.info("Match the resume?");
		String response = askGPT(chatMessages, 15);


		// Step 3. Optimize resumes for the ATS scanning process.
		// Or remove job position if it didn't match with resume

		// Attention, we overwrite jobDescription by its requirements, be cautious
		String jobDescription = clone.jobDescription;
		clone.jobDescription = jobRequirements; // it will be used in the Report.xlsx

		if (response.toUpperCase().startsWith("NO")) {
			clone.declineReason = response;
			synchronized(Storage.generalBadJobList) {
			Storage.generalBadJobList.add(clone);
			}
		} else if (role3) {
			// creating a resume tailored to the job description only if role3 is true
			chatMessages.clear();
			chatMessages.add(new ChatMessage(ChatRole.SYSTEM, systemRoles[2]));
			chatMessages.add(new ChatMessage(ChatRole.USER, "Job description:\n" + jobDescription));
			// log.info("Creating ideal resume for the position");
			refinedResume = askGPT(chatMessages, 400);

			// save refined resume to a file
			final String illigalCharacters = "[^A-Za-z0-9]+";
			String filename = GetDateString.getDate() + "_" 
					+ clone.companyName.replaceAll(illigalCharacters, "_") + "_"
					+ clone.jobTitle.replaceAll(illigalCharacters, "_") + ".txt";
			//the relative path to a resume for xlsx report link
			clone.refinedResume = "./Resumes/"+ filename;
			//here we use full path to the resume for file writing
			SaveRefinedResume(refinedResume, Paths.resumePath + filename);

		}
		return clone;
	}

	public static String askGPT(List<ChatMessage> chatMessages, int maxTokens) {
		// limiting rate per minute
		try {
			semaphore.acquire();
			
		} catch (InterruptedException e) {
			log.error(e.toString());
		}

		ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(chatMessages);
		chatCompletionsOptions.setMaxTokens(maxTokens).setTemperature(0.6);
		
		try {
			ChatCompletions chatCompletions = client.getChatCompletions(model, chatCompletionsOptions);
			return chatCompletions.getChoices().get(0).getMessage().getContent();
		}catch(Exception e) {
			log.error(e.toString());
			log.error("If you're using free API key, you may have reached daily limit");
			log.error("Check balance of your OpenAI account as well.");
			chatMessages.forEach(s->log.error("Name: "+s.getName()+" Content: " +s.getContent()));
			return "there was an error during assessment, try to use paid API key";
		}

	}

	private static void SaveRefinedResume(String refinedResume, String filename) {
		try {
			MakeDir.makeDir(filename);
			FileWriter writer = new FileWriter(filename);
			writer.write(refinedResume);
			writer.close();
		} catch (IOException e) {
			log.error("An error occurred: " + e.getMessage());
		}
	}

	// limits API calls
	private static void releaseSemaphore() {
		// we avoid issuing more permits than available
		int freePermits = semaphore.availablePermits();
		int release = totalPermits - freePermits;
		semaphore.release(release);
	}
}