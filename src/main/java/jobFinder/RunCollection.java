package jobFinder;

import pages.Glassdoor;
import pages.Google;
import pages.Indeed;
import pages.LinkedIn;
import pages.Monster;
import pages.Ziprecruiter;
import utils.RunLimitedListOfThreads;


public class RunCollection {
	

	public void alinkedIn(int xpos) {
		try (LinkedIn linkedin = new LinkedIn(xpos)){
			
			//we're setting coordinates of the finishing thread for a new thread
			RunLimitedListOfThreads.lastPos=xpos;
		} catch(Exception e) {
			//and the same on exception
			RunLimitedListOfThreads.lastPos=xpos;
		}
	}

	public void glassdoor(int xpos) {
		try (Glassdoor glassdoor = new Glassdoor(xpos)){
			RunLimitedListOfThreads.lastPos=xpos;
		} catch(Exception e) {
			RunLimitedListOfThreads.lastPos=xpos;
		}
	}

	public void indeed(int xpos) {
		try (Indeed indeed = new Indeed(xpos)){
			RunLimitedListOfThreads.lastPos=xpos;
		} catch(Exception e) {
			RunLimitedListOfThreads.lastPos=xpos;
		}
	}

	public void ziprecruiter(int xpos) {
		try(Ziprecruiter ziprecruiter = new Ziprecruiter(xpos)) {
			RunLimitedListOfThreads.lastPos=xpos;
		} catch(Exception e) {
			RunLimitedListOfThreads.lastPos=xpos;
		}
	}

	public void monster(int xpos) {
		try (Monster monster = new Monster(xpos)){
			RunLimitedListOfThreads.lastPos=xpos;
		} catch(Exception e) {
			RunLimitedListOfThreads.lastPos=xpos;
		}
	}
	
	public void google(int xpos) {
		try (Google google = new Google(xpos)){
			RunLimitedListOfThreads.lastPos=xpos;
		} catch(Exception e) {
			RunLimitedListOfThreads.lastPos=xpos;
		}
	}
}
