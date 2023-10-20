package utils;

import java.util.List;

import jobFinder.MyThread;

public class RunLimitedListOfThreads {
	//this var is being changed by RunCollection methods on method teardown or exception
	//and it is being changed by runNewThread method
	public static int lastPos=Storage.xpos;
	
	//main method, runs the num threads from the list 
	//if one thread is terminated, it calls the next one
	public static void runThreadList(List<MyThread> list, int num) {
		boolean flag = true;
		while (flag) {
			// getting num of the active threads
			int active = getActiveThreads(list);
			// if limit was reached, continue;
			if (active == num) {
				sleep(1000);
				continue;
			}
			// running new thread
			boolean newThread = runNewThread(list);

			// there is no active or new threads, exit loop
			if (active == 0 && !newThread) {
				break;
			}
		}
	}

	private static void sleep(int ms) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//picks one method from the list and runs it
	private static boolean runNewThread(List<MyThread> list) {
		for (MyThread thread : list) {
			if (thread.getState() == Thread.State.NEW) {
				if(Storage.highPriority) {
					thread.setPriority(Thread.MAX_PRIORITY);
				}
				//set coordinates of a new window
				thread.setPos(lastPos);

				if(Storage.twoMonitors && Storage.threads == 2) {
					//we change starting coordinates for the next thread
					//this block is used for initial start because further
					//lastPost will be changed by RunCollection's methods
					lastPos = (lastPos==0)?Storage.xpos:0;
				}
				
				thread.start();
				return true;
			}
		}
		// when there is no more new threads to run
		return false;
	}
	
	//returns the number of threads that were run
	private static int getActiveThreads(List<MyThread> list) {
		int count = 0;
		for (Thread thread : list) {
			if (thread.isAlive()) {
				count++;
			}
		}
		return count;
	}

}
