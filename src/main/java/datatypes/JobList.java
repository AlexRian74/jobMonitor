package datatypes;

import java.util.Objects;

public class JobList implements Comparable<JobList> {
	public String jobTitle;
	public String companyName;
	public String jobDescription;
	public String url;
	public String refinedResume;
	public String declineReason;
	public String searchKeyword;
	public int hash;

	public JobList(String jobTitle, String companyName, String jobDescription, String applyLink, String searchKeyword) {
		this.jobTitle = jobTitle;
		this.companyName = companyName;
		this.jobDescription = jobDescription;
		this.url = applyLink;
		this.searchKeyword = searchKeyword;
		hashCode();
	}

	public JobList(JobList o) {
		this.jobTitle = o.jobTitle;
		this.companyName = o.companyName;
		this.jobDescription = o.jobDescription;
		this.url = o.url;
		this.refinedResume = o.refinedResume;
		this.declineReason = o.declineReason;
		this.searchKeyword = o.searchKeyword;
		this.hash = o.hash;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		JobList otherObject = (JobList) o;
		// Compare attributes that determine equality
		return this.jobTitle.equals(otherObject.jobTitle) && this.companyName.equals(otherObject.companyName);
	}

	@Override
	public int hashCode() {
		hash = Objects.hash(jobTitle, companyName);
		return hash;
	}

	@Override
	public int compareTo(JobList o) {
		int comp = this.jobTitle.compareTo(o.jobTitle);
		if (comp != 0) {
			return comp;
		} else {
			return this.companyName.compareTo(o.companyName);
		}
	}

}
