package org.jenkinsci.plugins.testresultsanalyzer;

import hudson.model.Action;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.Actionable;
import hudson.model.Run;
import hudson.tasks.test.TabulatedResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;
import hudson.util.RunList;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.testresultsanalyzer.result.info.ResultInfo;
import org.jenkinsci.plugins.testresultsanalyzer.result.data.ResultData;
import org.jenkinsci.plugins.testresultsanalyzer.result.info.ClassInfo;
import org.jenkinsci.plugins.testresultsanalyzer.result.info.PackageInfo;
import org.jenkinsci.plugins.testresultsanalyzer.result.info.ResultInfo;
import org.jenkinsci.plugins.testresultsanalyzer.result.info.TestCaseInfo;
import org.kohsuke.stapler.bind.JavaScriptMethod;

public class TestResultsAnalyzerAction extends Actionable implements Action {

	@SuppressWarnings("rawtypes")
	AbstractProject project;
	private List<Integer> builds = new ArrayList<Integer>();

	ResultInfo resultInfo;

	public TestResultsAnalyzerAction(@SuppressWarnings("rawtypes")
	AbstractProject project) {
		this.project = project;
	}

	/**
	 * The display name for the action.
	 * 
	 * @return the name as String
	 */
	public final String getDisplayName() {
		return this.hasPermission() ? Constants.NAME : null;
	}

	/**
	 * The icon for this action.
	 * 
	 * @return the icon file as String
	 */
	public final String getIconFileName() {
		return this.hasPermission() ? Constants.ICONFILENAME : null;
	}

	/**
	 * The url for this action.
	 * 
	 * @return the url as String
	 */
	public String getUrlName() {
		return this.hasPermission() ? Constants.URL : null;
	}

	/**
	 * Search url for this action.
	 * 
	 * @return the url as String
	 */
	public String getSearchUrl() {
		return this.hasPermission() ? Constants.URL : null;
	}

	/**
	 * Checks if the user has CONFIGURE permission.
	 * 
	 * @return true - user has permission, false - no permission.
	 */
	private boolean hasPermission() {
		return project.hasPermission(Item.READ);
	}

	@SuppressWarnings("rawtypes")
	public AbstractProject getProject() {
		return this.project;
	}

	@JavaScriptMethod
	public JSONArray getNoOfBuilds(String noOfbuildsNeeded) {
		JSONArray jsonArray;
		int noOfBuilds = getNoOfBuildRequired(noOfbuildsNeeded);

		jsonArray = getBuildsArray(getBuildList(noOfBuilds));

		return jsonArray;
	}

	private JSONArray getBuildsArray(List<Integer> buildList) {
		JSONArray jsonArray = new JSONArray();
		for (Integer build : buildList) {
			jsonArray.add(build);
		}
		return jsonArray;
	}

	private List<Integer> getBuildList(int noOfBuilds) {
		if ((noOfBuilds <= 0) || (noOfBuilds >= builds.size())) {
			return builds;
		}

		List<Integer> buildList = new ArrayList<Integer>();

		for(int i = 0; i < noOfBuilds; i++) {
			buildList.add(builds.get(i));
		}

		return buildList;
	}

	private int getNoOfBuildRequired(String noOfbuildsNeeded) {
		int noOfBuilds;
		try {
			noOfBuilds = Integer.parseInt(noOfbuildsNeeded);
		}
		catch (NumberFormatException e) {
			noOfBuilds = -1;
		}
		return noOfBuilds;
	}

	public boolean isUpdated() {
		int latestBuildNumber = project.getLastBuild().getNumber();
		return !(builds.contains(latestBuildNumber));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public void getJsonLoadData() {
		if (isUpdated()) {
			resultInfo = new ResultInfo();
			builds = new ArrayList<Integer>();
			RunList<Run> runs = project.getBuilds();
			Iterator<Run> runIterator = runs.iterator();
			while (runIterator.hasNext()) {
				Run run = runIterator.next();
				int buildNumber = run.getNumber();
				String buildUrl = project.getBuildByNumber(buildNumber).getUrl();
				builds.add(run.getNumber());
				List<AbstractTestResultAction> testActions = run.getActions(hudson.tasks.test.AbstractTestResultAction.class);
				for (hudson.tasks.test.AbstractTestResultAction testAction : testActions) {
					TabulatedResult testResult = (TabulatedResult) testAction.getResult();
					Collection<? extends TestResult> packageResults = testResult.getChildren();
					for (TestResult packageResult : packageResults) { // packageresult
						resultInfo.addPackage(buildNumber, (TabulatedResult) packageResult, Jenkins.getInstance().getRootUrl() + buildUrl);
					}
				}
			}
		}
	}

    @JavaScriptMethod
    public JSONObject getTreeResult(String noOfBuildsNeeded) {
        int noOfBuilds = getNoOfBuildRequired(noOfBuildsNeeded);
        List<Integer> buildList = getBuildList(noOfBuilds);

        JsTreeUtil jsTreeUtils = new JsTreeUtil();
        return jsTreeUtils.getJsTree(buildList, resultInfo);
    }
	
	@JavaScriptMethod
    public String getExportCSV(String timeBased) {
		boolean isTimeBased = Boolean.parseBoolean(timeBased);
        Map<String, PackageInfo> packageResults = resultInfo.getPackageResults();
        String buildsString = "";
        for (int i = 0; i < builds.size(); i++) {
            buildsString += ",\"" + Integer.toString(builds.get(i)) + "\"";
        }		
        String header = "\"Package\",\"Class\",\"Test\"";
        header += buildsString;

		StringBuilder exportBuilder = new StringBuilder();
        exportBuilder.append(header + System.lineSeparator());
		DecimalFormat decimalFormat = new DecimalFormat("#.###");
		decimalFormat.setRoundingMode(RoundingMode.CEILING);
        for (PackageInfo pInfo : packageResults.values()) {
            String packageName = pInfo.getName();
            //loop the classes
            for (ClassInfo cInfo : pInfo.getClasses().values()) {
                String className = cInfo.getName();
                //loop the tests
                for (TestCaseInfo tInfo : cInfo.getTests().values()) {
                    String testName = tInfo.getName();
                    exportBuilder.append("\""+ packageName + "\",\"" + className + "\",\"" + testName+"\"");
					Map<Integer, ResultData> buildPackageResults = tInfo.getBuildPackageResults();
					for (int i = 0; i < builds.size(); i++) {
						Integer buildNumber = builds.get(i);
						String data = "N/A";
						if(buildPackageResults.containsKey(buildNumber)) {
							ResultData buildResult = buildPackageResults.get(buildNumber);
							if(!isTimeBased) {
								data = buildResult.getStatus();
							} else {
								data = decimalFormat.format(buildResult.getTotalTimeTaken());
							}
						}
						exportBuilder.append(",\"" + data + "\"");
					}
                    exportBuilder.append(System.lineSeparator());
                }
            }
        }
        return exportBuilder.toString();
    }

	public String getNoOfBuilds() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getNoOfBuilds();
	}

	public boolean getShowAllBuilds() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowAllBuilds();
	}

	public boolean getShowLineGraph() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowLineGraph();
	}

	public boolean getShowBarGraph() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowBarGraph();
	}

	public boolean getShowPieGraph() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowPieGraph();
	}

	public boolean getShowBuildTime() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getShowBuildTime();
	}

	public String getChartDataType() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getChartDataType();
	}

	public String getRunTimeLowThreshold() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getRunTimeLowThreshold();
	}

	public String getRunTimeHighThreshold() {
		return TestResultsAnalyzerExtension.DESCRIPTOR.getRunTimeHighThreshold();
	}
}
